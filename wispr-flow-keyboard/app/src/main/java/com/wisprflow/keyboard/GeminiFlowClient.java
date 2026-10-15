package com.wisprflow.keyboard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles communication with Google Gemini AI API and provides local offline fallback.
 */
public class GeminiFlowClient {

    private static final String TAG = "GeminiFlowClient";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface PolishCallback {
        void onSuccess(String polishedText, long latencyMs);
        void onError(String errorMessage, String fallbackText);
    }

    public interface ModelsCallback {
        void onSuccess(List<String> models);
        void onError(String errorMessage);
    }

    /**
     * Refines spoken text using Google Gemini Flash or instant offline rule-based polisher.
     */
    public static void polishSpeech(final String rawSpeech,
                                    final String apiKey,
                                    final String modelName,
                                    final FlowMode mode,
                                    final PolishCallback callback) {
        if (rawSpeech == null || rawSpeech.trim().isEmpty()) {
            mainHandler.post(() -> callback.onSuccess("", 0));
            return;
        }

        final String cleanInput = rawSpeech.trim();
        final long startTime = System.currentTimeMillis();

        // If no API key is provided, use instant local rule-based polisher
        if (apiKey == null || apiKey.trim().isEmpty()) {
            final String localResult = ruleBasedPolish(cleanInput, mode);
            mainHandler.post(() -> callback.onSuccess(localResult, System.currentTimeMillis() - startTime));
            return;
        }

        executor.execute(() -> {
            try {
                String targetModel = (modelName != null && !modelName.isEmpty()) ? modelName : "gemini-2.0-flash";
                if (targetModel.startsWith("models/")) {
                    targetModel = targetModel.substring(7);
                }

                URL url = new URL(BASE_URL + "/" + targetModel + ":generateContent?key=" + apiKey.trim());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                // Build Request JSON
                JSONObject requestJson = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject contentObj = new JSONObject();
                contentObj.put("role", "user");

                JSONArray partsArray = new JSONArray();
                JSONObject partObj = new JSONObject();
                String promptText = mode.getSystemPrompt() + "\n\nRAW SPOKEN TRANSCRIPT:\n\"" + cleanInput + "\"\n\nPOLISHED OUTPUT:";
                partObj.put("text", promptText);
                partsArray.put(partObj);

                contentObj.put("parts", partsArray);
                contentsArray.put(contentObj);
                requestJson.put("contents", contentsArray);

                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", mode == FlowMode.COMMAND ? 0.3 : 0.1);
                genConfig.put("maxOutputTokens", 1024);
                requestJson.put("generationConfig", genConfig);

                byte[] postData = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder responseSb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseSb.append(line);
                }
                reader.close();

                if (responseCode >= 200 && responseCode < 300) {
                    JSONObject responseJson = new JSONObject(responseSb.toString());
                    JSONArray candidates = responseJson.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject first = candidates.getJSONObject(0);
                        JSONObject content = first.optJSONObject("content");
                        if (content != null) {
                            JSONArray parts = content.optJSONArray("parts");
                            if (parts != null && parts.length() > 0) {
                                String text = parts.getJSONObject(0).optString("text", "").trim();
                                final long latency = System.currentTimeMillis() - startTime;
                                mainHandler.post(() -> callback.onSuccess(text, latency));
                                return;
                            }
                        }
                    }
                }

                // If API returned error or empty candidate, use rule-based fallback
                Log.w(TAG, "Gemini API HTTP " + responseCode + ", using rule-based fallback: " + responseSb);
                final String fallback = ruleBasedPolish(cleanInput, mode);
                final long latency = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> callback.onSuccess(fallback, latency));

            } catch (Exception e) {
                Log.e(TAG, "Network exception during Gemini polish: " + e.getMessage());
                final String fallback = ruleBasedPolish(cleanInput, mode);
                final long latency = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> callback.onSuccess(fallback, latency));
            }
        });
    }

    /**
     * Discovers all supported Gemini models from the user's API key.
     */
    public static void fetchAvailableModels(final String apiKey, final ModelsCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("Please enter a valid Gemini API key first.");
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "?key=" + apiKey.trim());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder responseSb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseSb.append(line);
                }
                reader.close();

                if (responseCode >= 200 && responseCode < 300) {
                    JSONObject json = new JSONObject(responseSb.toString());
                    JSONArray modelsArray = json.optJSONArray("models");
                    List<String> modelList = new ArrayList<>();

                    if (modelsArray != null) {
                        for (int i = 0; i < modelsArray.length(); i++) {
                            JSONObject m = modelsArray.getJSONObject(i);
                            String name = m.optString("name", "").replace("models/", "");
                            JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                            boolean canGenerate = false;
                            if (methods != null) {
                                for (int j = 0; j < methods.length(); j++) {
                                    if ("generateContent".equals(methods.optString(j))) {
                                        canGenerate = true;
                                        break;
                                    }
                                }
                            }
                            if (canGenerate && (name.contains("flash") || name.contains("pro") || name.contains("gemini"))) {
                                modelList.add(name);
                            }
                        }
                    }

                    if (!modelList.contains("gemini-2.0-flash")) {
                        modelList.add(0, "gemini-2.0-flash");
                    }

                    mainHandler.post(() -> callback.onSuccess(modelList));
                } else {
                    mainHandler.post(() -> callback.onError("Invalid API key or network error (HTTP " + responseCode + ")"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Connection failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Instant local rule-based polisher (<5ms execution).
     */
    public static String ruleBasedPolish(String rawText, FlowMode mode) {
        if (rawText == null || rawText.trim().isEmpty()) return "";

        String text = rawText.trim();

        // 1. Strip verbal filler ticks
        text = text.replaceAll("(?i)\\b(um|uh|erm|er|ah)\\b", "");
        text = text.replaceAll("(?i)\\b(like)\\s(?=(you know|basically|i mean))", "");
        text = text.replaceAll("(?i)\\b(you know|basically|so yeah|i mean)\\b", "");
        text = text.replaceAll("(?i)\\b(kind of|sort of)\\b(?=\\s(like|um))", "");

        // 2. Remove duplicate words (stutters: "I I", "the the")
        text = text.replaceAll("(?i)\\b(\\w+)\\s+\\1\\b", "$1");

        // 3. Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();

        // 4. Capitalization
        if (!text.isEmpty()) {
            text = Character.toUpperCase(text.charAt(0)) + (text.length() > 1 ? text.substring(1) : "");
            text = text.replaceAll("([.?!])\\s*([a-z])", "$1 $2".toUpperCase());
            text = text.replaceAll("\\bi\\b", "I");
        }

        // 5. Inferred punctuation
        if (!text.isEmpty() && !text.matches(".*[.?!]$")) {
            boolean isQuestion = text.matches("(?i)^(who|what|where|when|why|how|can|could|would|will|is|are|do|does|did)\\b.*");
            text += isQuestion ? "?" : ".";
        }

        // Mode specific touches
        if (mode == FlowMode.EMAIL) {
            text = "Hi,\n\n" + text + "\n\nBest regards,";
        } else if (mode == FlowMode.NOTES) {
            text = "# Notes\n• " + text;
        }

        return text;
    }
}
