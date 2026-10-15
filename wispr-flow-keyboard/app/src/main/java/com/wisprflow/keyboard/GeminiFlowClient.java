package com.wisprflow.keyboard;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles communication with Google Gemini AI API with:
 * - Multi-model fallback cascade (gemini-2.0-flash -> gemini-1.5-flash -> gemini-1.5-pro)
 * - Strict 6-field card extraction (Name, Designation, Organization, Email, Phone Number, City only; No N/A)
 * - Pure AI test methods (strict no-fallback mode)
 * - Model usage indicator (Selected AI Model vs Fallback AI Model vs Offline)
 */
public class GeminiFlowClient {

    private static final String TAG = "GeminiFlowClient";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String[] DEFAULT_MODEL_CHAIN = {
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
    };

    public interface PolishCallback {
        void onSuccess(String polishedText, long latencyMs);
        void onError(String errorMessage, String fallbackText);
    }

    public interface PolishResultCallback {
        void onSuccess(String polishedText, long latencyMs, String modelUsed, boolean isFallback);
        void onError(String errorMessage, String fallbackText);
    }

    public interface ModelsCallback {
        void onSuccess(List<String> models);
        void onError(String errorMessage);
    }

    public interface CardScanCallback {
        void onSuccess(String formattedContactCard, long latencyMs);
        void onError(String errorMessage);
    }

    public interface CardScanResultCallback {
        void onSuccess(String formattedContactCard, long latencyMs, String modelUsed, boolean isFallback);
        void onError(String errorMessage);
    }

    public interface StrictAiCallback {
        void onSuccess(String result, long latencyMs, String modelUsed);
        void onError(String errorMessage);
    }

    public static class ConversationAnalysisResult {
        public final String executiveSummary;
        public final String keyPoints;
        public final String actionItems;
        public final String fullResponse;
        public final long latencyMs;
        public final String modelUsed;
        public final boolean isFallback;

        public ConversationAnalysisResult(String executiveSummary, String keyPoints, String actionItems,
                                          String fullResponse, long latencyMs, String modelUsed, boolean isFallback) {
            this.executiveSummary = executiveSummary != null ? executiveSummary : "";
            this.keyPoints = keyPoints != null ? keyPoints : "";
            this.actionItems = actionItems != null ? actionItems : "";
            this.fullResponse = fullResponse != null ? fullResponse : "";
            this.latencyMs = latencyMs;
            this.modelUsed = modelUsed;
            this.isFallback = isFallback;
        }
    }

    public interface ConversationAnalysisCallback {
        void onSuccess(ConversationAnalysisResult result);
        void onError(String errorMessage);
    }

    /**
     * Backward-compatible polishSpeech overload.
     */
    public static void polishSpeech(final String rawSpeech,
                                    final String apiKey,
                                    final String modelName,
                                    final FlowMode mode,
                                    final PolishCallback callback) {
        polishSpeechWithFallback(rawSpeech, apiKey, modelName, mode, new PolishResultCallback() {
            @Override
            public void onSuccess(String polishedText, long latencyMs, String modelUsed, boolean isFallback) {
                callback.onSuccess(polishedText, latencyMs);
            }

            @Override
            public void onError(String errorMessage, String fallbackText) {
                callback.onError(errorMessage, fallbackText);
            }
        });
    }

    /**
     * Refines spoken text using multi-model fallback cascade.
     */
    public static void polishSpeechWithFallback(final String rawSpeech,
                                                final String apiKey,
                                                final String preferredModel,
                                                final FlowMode mode,
                                                final PolishResultCallback callback) {
        if (rawSpeech == null || rawSpeech.trim().isEmpty()) {
            mainHandler.post(() -> callback.onSuccess("", 0, "None", false));
            return;
        }

        final String cleanInput = rawSpeech.trim();
        final long startTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            final String offline = polishOfflineRules(cleanInput, mode);
            mainHandler.post(() -> callback.onSuccess(offline, System.currentTimeMillis() - startTime, "Offline Rules", false));
            return;
        }

        executor.execute(() -> {
            List<String> chain = buildModelChain(preferredModel);
            String lastError = "";

            for (int i = 0; i < chain.size(); i++) {
                String model = chain.get(i);
                boolean isFallback = (i > 0);
                try {
                    String prompt = mode.buildPrompt(cleanInput);
                    String polished = callGeminiGenerateContent(prompt, apiKey.trim(), model);
                    if (polished != null && !polished.trim().isEmpty()) {
                        final long latency = System.currentTimeMillis() - startTime;
                        final String res = polished.trim();
                        mainHandler.post(() -> callback.onSuccess(res, latency, model, isFallback));
                        return;
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.w(TAG, "Model " + model + " failed: " + lastError + ". Trying next fallback model...");
                }
            }

            // All models failed -> fallback to offline rules
            final String offline = polishOfflineRules(cleanInput, mode);
            final String finalErr = lastError;
            mainHandler.post(() -> callback.onError("AI unavailable (" + finalErr + ")", offline));
        });
    }

    /**
     * Strict Pure AI Voice Test (no fallback).
     */
    public static void testPureAiVoice(final String sampleText,
                                       final String apiKey,
                                       final String modelName,
                                       final StrictAiCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("API Key is missing. Paste your Gemini API Key in Settings."));
            return;
        }

        final long start = System.currentTimeMillis();
        final String targetModel = (modelName != null && !modelName.isEmpty()) ? modelName : "gemini-2.0-flash";

        executor.execute(() -> {
            try {
                String prompt = "You are an executive dictation assistant. Clean up filler words and format cleanly:\n\n" + sampleText;
                String result = callGeminiGenerateContent(prompt, apiKey.trim(), targetModel);
                long latency = System.currentTimeMillis() - start;
                mainHandler.post(() -> callback.onSuccess(result, latency, targetModel));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Pure AI Error (" + targetModel + "): " + e.getMessage()));
            }
        });
    }

    /**
     * Extracts card details with multi-model fallback chain.
     * Enforces STRICT 6-field output: Name, Designation, Organization, Email, Phone Number, City only. No N/A.
     */
    public static void scanContactCard(final byte[] jpegBytes,
                                       final String apiKey,
                                       final String modelName,
                                       final CardScanResultCallback callback) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            mainHandler.post(() -> callback.onError("No image data provided for contact card scanning"));
            return;
        }

        final long startTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("No API key configured for Gemini Vision"));
            return;
        }

        executor.execute(() -> {
            List<String> chain = buildModelChain(modelName);
            String lastError = "";

            for (int i = 0; i < chain.size(); i++) {
                String model = chain.get(i);
                boolean isFallback = (i > 0);
                try {
                    String rawResult = callGeminiVision(jpegBytes, apiKey.trim(), model);
                    String cleaned = cleanCardOutput(rawResult);
                    if (cleaned != null && !cleaned.isEmpty()) {
                        final long latency = System.currentTimeMillis() - startTime;
                        mainHandler.post(() -> callback.onSuccess(cleaned, latency, model, isFallback));
                        return;
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.w(TAG, "Vision model " + model + " failed: " + lastError);
                }
            }

            final String err = lastError;
            mainHandler.post(() -> callback.onError("Gemini Vision AI failed (" + err + ")"));
        });
    }

    public static void scanContactCard(final byte[] jpegBytes,
                                       final String apiKey,
                                       final String modelName,
                                       final CardScanCallback callback) {
        scanContactCard(jpegBytes, apiKey, modelName, new CardScanResultCallback() {
            @Override
            public void onSuccess(String formattedContactCard, long latencyMs, String modelUsed, boolean isFallback) {
                callback.onSuccess(formattedContactCard, latencyMs);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Strict Pure AI Vision Card Test (no fallback).
     */
    public static void testPureAiVision(final byte[] jpegBytes,
                                        final String apiKey,
                                        final String modelName,
                                        final StrictAiCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("API Key is missing."));
            return;
        }

        final long start = System.currentTimeMillis();
        final String targetModel = (modelName != null && !modelName.isEmpty()) ? modelName : "gemini-2.0-flash";

        executor.execute(() -> {
            try {
                String raw = callGeminiVision(jpegBytes, apiKey.trim(), targetModel);
                String cleaned = cleanCardOutput(raw);
                long latency = System.currentTimeMillis() - start;
                mainHandler.post(() -> callback.onSuccess(cleaned, latency, targetModel));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Pure AI Vision Error (" + targetModel + "): " + e.getMessage()));
            }
        });
    }

    /**
     * Formats raw on-device OCR text using Gemini with strict 6-field enforcement.
     */
    public static void formatExtractedCardText(final String rawOcrText,
                                               final String apiKey,
                                               final String modelName,
                                               final CardScanResultCallback callback) {
        if (rawOcrText == null || rawOcrText.trim().isEmpty()) {
            mainHandler.post(() -> callback.onSuccess("", 0, "None", false));
            return;
        }

        final long startTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            final String fallback = cleanCardOutput(parseRawOcrLocal(rawOcrText));
            mainHandler.post(() -> callback.onSuccess(fallback, System.currentTimeMillis() - startTime, "Offline OCR Regex", false));
            return;
        }

        executor.execute(() -> {
            List<String> chain = buildModelChain(modelName);
            for (int i = 0; i < chain.size(); i++) {
                String model = chain.get(i);
                boolean isFallback = (i > 0);
                try {
                    String prompt = "You are an expert contact card parser.\n" +
                            "Here is raw OCR text extracted from a business card:\n" +
                            "\"\"\"\n" + rawOcrText + "\n\"\"\"\n\n" +
                            "Extract ONLY the following fields if present in the text:\n" +
                            "Name: [Full Name]\n" +
                            "Designation: [Job Title or Role]\n" +
                            "Organization: [Company or Business Name]\n" +
                            "Email: [Email Address]\n" +
                            "Phone Number: [Phone Number]\n" +
                            "City: [City Name]\n\n" +
                            "CRITICAL RULES:\n" +
                            "- Output ONLY these 6 fields. Do NOT output any other field.\n" +
                            "- If any field is missing or not mentioned, DO NOT output that line at all.\n" +
                            "- Absolutely NO N/A, None, or empty lines.\n" +
                            "- Plain text output only.";

                    String formatted = callGeminiGenerateContent(prompt, apiKey.trim(), model);
                    String cleaned = cleanCardOutput(formatted);
                    if (cleaned != null && !cleaned.isEmpty()) {
                        final long latency = System.currentTimeMillis() - startTime;
                        mainHandler.post(() -> callback.onSuccess(cleaned, latency, model, isFallback));
                        return;
                    }
                } catch (Exception ignored) {}
            }

            final String fallback = cleanCardOutput(parseRawOcrLocal(rawOcrText));
            final long latency = System.currentTimeMillis() - startTime;
            mainHandler.post(() -> callback.onSuccess(fallback, latency, "Offline Local OCR", false));
        });
    }

    public static void formatExtractedCardText(final String rawOcrText,
                                               final String apiKey,
                                               final String modelName,
                                               final CardScanCallback callback) {
        formatExtractedCardText(rawOcrText, apiKey, modelName, new CardScanResultCallback() {
            @Override
            public void onSuccess(String formattedContactCard, long latencyMs, String modelUsed, boolean isFallback) {
                callback.onSuccess(formattedContactCard, latencyMs);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Strict 6-Field Sanitizer:
     * Retains ONLY Name, Designation, Organization, Email, Phone Number, City.
     * Completely eliminates any field that is N/A, None, not available, or empty.
     */
    public static String cleanCardOutput(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n");

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.contains("CONTACT CARD") || line.contains("---") || line.startsWith("#")) continue;

            String lower = line.toLowerCase(Locale.US);
            String field = null;
            String val = null;

            if (lower.startsWith("name:") || lower.startsWith("👤 name:")) {
                field = "Name";
                val = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("designation:") || lower.startsWith("title:") || lower.startsWith("job title:") ||
                    lower.startsWith("💼 title:") || lower.startsWith("💼 designation:")) {
                field = "Designation";
                val = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("organization:") || lower.startsWith("company:") || lower.startsWith("company name:") ||
                    lower.startsWith("🏢 company:") || lower.startsWith("🏢 organization:")) {
                field = "Organization";
                val = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("email:") || lower.startsWith("email address:") || lower.startsWith("✉️ email:")) {
                field = "Email";
                val = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("phone number:") || lower.startsWith("phone:") || lower.startsWith("mobile:") ||
                    lower.startsWith("tel:") || lower.startsWith("📞 phone:")) {
                field = "Phone Number";
                val = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("city:") || lower.startsWith("location:") || lower.startsWith("📍 city:")) {
                field = "City";
                val = line.substring(line.indexOf(':') + 1).trim();
            }

            if (field != null && val != null) {
                val = val.trim();
                String valLower = val.toLowerCase(Locale.US);
                if (!val.isEmpty() && !valLower.equals("n/a") && !valLower.equals("na") &&
                        !valLower.equals("none") && !valLower.equals("not available") &&
                        !valLower.equals("not specified") && !valLower.equals("[scanned contact]") &&
                        !valLower.equals("null") && !valLower.equals("-") && !valLower.equals("unknown")) {
                    sb.append(field).append(": ").append(val).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * Local regex fallback honoring the strict 6-field rule.
     */
    public static String parseRawOcrLocal(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return "";

        String name = "";
        String designation = "";
        String organization = "";
        String phone = "";
        String email = "";
        String city = "";

        String[] lines = rawText.split("\n");
        java.util.regex.Pattern emailPattern = java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        java.util.regex.Pattern phonePattern = java.util.regex.Pattern.compile("(\\+?[0-9]{1,4}[-\\s]?)?(\\(?\\d{2,4}\\)?[-\\s]?)?[\\d\\s-]{7,15}");

        List<String> remaining = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            java.util.regex.Matcher em = emailPattern.matcher(line);
            if (em.find() && email.isEmpty()) {
                email = em.group();
                continue;
            }

            java.util.regex.Matcher pm = phonePattern.matcher(line);
            if (pm.find() && phone.isEmpty() && line.replaceAll("[^0-9]", "").length() >= 7) {
                phone = pm.group().trim();
                continue;
            }

            if (line.toLowerCase(Locale.US).contains("city") || line.matches("(?i).*(san francisco|new york|chicago|london|tokyo|paris|berlin|bangalore|mumbai|delhi|dubai|toronto|sydney).*")) {
                if (city.isEmpty()) {
                    city = line.replaceAll("(?i)^(city|location)[:\\s]*", "").trim();
                    continue;
                }
            }

            remaining.add(line);
        }

        if (remaining.size() > 0) name = remaining.get(0);
        if (remaining.size() > 1) designation = remaining.get(1);
        if (remaining.size() > 2) organization = remaining.get(2);

        StringBuilder sb = new StringBuilder();
        if (!name.isEmpty()) sb.append("Name: ").append(name).append("\n");
        if (!designation.isEmpty()) sb.append("Designation: ").append(designation).append("\n");
        if (!organization.isEmpty()) sb.append("Organization: ").append(organization).append("\n");
        if (!email.isEmpty()) sb.append("Email: ").append(email).append("\n");
        if (!phone.isEmpty()) sb.append("Phone Number: ").append(phone).append("\n");
        if (!city.isEmpty()) sb.append("City: ").append(city).append("\n");

        return sb.toString().trim();
    }

    private static String callGeminiGenerateContent(String prompt, String apiKey, String modelName) throws Exception {
        String cleanModel = modelName.startsWith("models/") ? modelName.substring(7) : modelName;
        URL url = new URL(BASE_URL + "/" + cleanModel + ":generateContent?key=" + apiKey);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);

        JSONObject requestJson = new JSONObject();
        JSONArray contentsArray = new JSONArray();
        JSONObject contentObj = new JSONObject();
        contentObj.put("role", "user");

        JSONArray partsArray = new JSONArray();
        JSONObject partObj = new JSONObject();
        partObj.put("text", prompt);
        partsArray.put(partObj);

        contentObj.put("parts", partsArray);
        contentsArray.put(contentObj);
        requestJson.put("contents", contentsArray);

        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.2);
        genConfig.put("maxOutputTokens", 512);
        requestJson.put("generationConfig", genConfig);

        OutputStream os = conn.getOutputStream();
        os.write(requestJson.toString().getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String err = readStream(conn.getErrorStream());
            throw new Exception("HTTP " + responseCode + ": " + err);
        }

        String responseBody = readStream(conn.getInputStream());
        JSONObject respJson = new JSONObject(responseBody);
        JSONArray candidates = respJson.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            if (content != null) {
                JSONArray respParts = content.optJSONArray("parts");
                if (respParts != null && respParts.length() > 0) {
                    return respParts.getJSONObject(0).optString("text", "").trim();
                }
            }
        }
        throw new Exception("Empty response from " + cleanModel);
    }

    private static String callGeminiVision(byte[] jpegBytes, String apiKey, String modelName) throws Exception {
        String cleanModel = modelName.startsWith("models/") ? modelName.substring(7) : modelName;
        URL url = new URL(BASE_URL + "/" + cleanModel + ":generateContent?key=" + apiKey);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(18000);
        conn.setDoOutput(true);

        String base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);

        JSONObject requestJson = new JSONObject();
        JSONArray contentsArray = new JSONArray();
        JSONObject contentObj = new JSONObject();
        contentObj.put("role", "user");

        JSONArray partsArray = new JSONArray();

        JSONObject imagePart = new JSONObject();
        JSONObject inlineData = new JSONObject();
        inlineData.put("mime_type", "image/jpeg");
        inlineData.put("data", base64Image);
        imagePart.put("inline_data", inlineData);
        partsArray.put(imagePart);

        JSONObject textPart = new JSONObject();
        String prompt = "You are an expert business card scanner.\n" +
                "Extract and output ONLY the following fields if present on this card:\n" +
                "Name: [Full Name]\n" +
                "Designation: [Job Title or Role]\n" +
                "Organization: [Company or Business Name]\n" +
                "Email: [Email Address]\n" +
                "Phone Number: [Phone Number]\n" +
                "City: [City Name]\n\n" +
                "RULES:\n" +
                "- ONLY these 6 fields. Do NOT output any other field.\n" +
                "- If a field is not available or missing, DO NOT include that line. Omit it completely.\n" +
                "- Do NOT write N/A, None, or empty lines.\n" +
                "- Plain text output only.";
        textPart.put("text", prompt);
        partsArray.put(textPart);

        contentObj.put("parts", partsArray);
        contentsArray.put(contentObj);
        requestJson.put("contents", contentsArray);

        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.1);
        genConfig.put("maxOutputTokens", 512);
        requestJson.put("generationConfig", genConfig);

        OutputStream os = conn.getOutputStream();
        os.write(requestJson.toString().getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String err = readStream(conn.getErrorStream());
            throw new Exception("HTTP " + responseCode + ": " + err);
        }

        String responseBody = readStream(conn.getInputStream());
        JSONObject respJson = new JSONObject(responseBody);
        JSONArray candidates = respJson.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            if (content != null) {
                JSONArray respParts = content.optJSONArray("parts");
                if (respParts != null && respParts.length() > 0) {
                    return respParts.getJSONObject(0).optString("text", "").trim();
                }
            }
        }
        throw new Exception("No text returned from Vision AI");
    }

    private static List<String> buildModelChain(String preferredModel) {
        List<String> chain = new ArrayList<>();
        if (preferredModel != null && !preferredModel.trim().isEmpty()) {
            chain.add(preferredModel.trim());
        }
        for (String m : DEFAULT_MODEL_CHAIN) {
            if (!chain.contains(m)) {
                chain.add(m);
            }
        }
        return chain;
    }

    public static void fetchAvailableModels(final String apiKey, final ModelsCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("API Key is empty"));
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "?key=" + apiKey.trim());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String err = readStream(conn.getErrorStream());
                    mainHandler.post(() -> callback.onError("HTTP " + responseCode + ": " + err));
                    return;
                }

                String responseBody = readStream(conn.getInputStream());
                JSONObject respJson = new JSONObject(responseBody);
                JSONArray modelsArray = respJson.optJSONArray("models");
                List<String> modelNames = new ArrayList<>();

                if (modelsArray != null) {
                    for (int i = 0; i < modelsArray.length(); i++) {
                        JSONObject m = modelsArray.getJSONObject(i);
                        String name = m.optString("name", "");
                        JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                        boolean canGenerate = false;
                        if (methods != null) {
                            for (int j = 0; j < methods.length(); j++) {
                                if ("generateContent".equals(methods.getString(j))) {
                                    canGenerate = true;
                                    break;
                                }
                            }
                        }
                        if (canGenerate && (name.contains("flash") || name.contains("pro") || name.contains("gemini-2.0"))) {
                            modelNames.add(name.replace("models/", ""));
                        }
                    }
                }

                if (!modelNames.contains("gemini-2.0-flash")) {
                    modelNames.add(0, "gemini-2.0-flash");
                }
                if (!modelNames.contains("gemini-1.5-flash")) {
                    modelNames.add("gemini-1.5-flash");
                }
                if (!modelNames.contains("gemini-1.5-pro")) {
                    modelNames.add("gemini-1.5-pro");
                }

                mainHandler.post(() -> callback.onSuccess(modelNames));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Error fetching models: " + e.getMessage()));
            }
        });
    }

    public static String polishOfflineRules(String text, FlowMode mode) {
        if (text == null) return "";
        text = text.replaceAll("(?i)\\b(um|uh|erm|like|you know|sort of|kind of)\\b", "");
        text = text.replaceAll("\\s+", " ").trim();
        text = collapseSpokenDigits(text);
        if (!text.isEmpty()) {
            text = Character.toUpperCase(text.charAt(0)) + (text.length() > 1 ? text.substring(1) : "");
            text = text.replaceAll("([.?!])\\s*([a-z])", "$1 $2".toUpperCase());
            text = text.replaceAll("\\bi\\b", "I");
        }
        if (!text.isEmpty() && !text.matches(".*[.?!]$")) {
            boolean isQuestion = text.matches("(?i)^(who|what|where|when|why|how|can|could|would|will|is|are|do|does|did)\\b.*");
            text += isQuestion ? "?" : ".";
        }
        return text;
    }

    public static String collapseSpokenDigits(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] digitWords = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        for (int i = 0; i < digitWords.length; i++) {
            text = text.replaceAll("(?i)\\b" + digitWords[i] + "\\b", String.valueOf(i));
        }
        for (int i = 0; i < 6; i++) {
            text = text.replaceAll("(\\b\\d)\\s+(?=\\d\\b)", "$1");
            text = text.replaceAll("(\\d)\\s+(\\d)", "$1$2");
        }
        return text;
    }

    public static void summarizeConversationWithFallback(final String transcript,
                                                         final String apiKey,
                                                         final String preferredModel,
                                                         final ConversationAnalysisCallback callback) {
        if (transcript == null || transcript.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("No conversation transcript recorded to summarize."));
            return;
        }

        final String cleanInput = transcript.trim();
        final long startTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            ConversationAnalysisResult offline = generateOfflineConversationSummary(cleanInput, System.currentTimeMillis() - startTime, "Offline Engine", false);
            mainHandler.post(() -> callback.onSuccess(offline));
            return;
        }

        executor.execute(() -> {
            List<String> chain = buildModelChain(preferredModel);
            String lastError = "";

            String prompt = "You are Smart AI Keyboard's Executive Meeting Assistant.\n" +
                    "Analyze the recorded conversation dialogue below and produce a clean, structured executive response.\n" +
                    "Return strictly in this format with these exact headings:\n\n" +
                    "📋 EXECUTIVE SUMMARY:\n" +
                    "- [2-3 concise bullet points summarizing context, decisions, and progress]\n\n" +
                    "💡 KEY DISCUSSION POINTS:\n" +
                    "- [Point 1]\n" +
                    "- [Point 2]\n\n" +
                    "⏳ PENDING ACTION ITEMS:\n" +
                    "- [ ] [Action item description] (Owner: [Person/Team if mentioned, else Team], Due: [Timeframe if mentioned, else ASAP])\n\n" +
                    "Conversation Transcript:\n\"\"\"\n" + cleanInput + "\n\"\"\"";

            for (int i = 0; i < chain.size(); i++) {
                String currentModel = chain.get(i);
                boolean isFallback = (i > 0);
                try {
                    String response = callGeminiGenerateContent(prompt, apiKey, currentModel);
                    if (response != null && !response.trim().isEmpty()) {
                        long latency = System.currentTimeMillis() - startTime;
                        ConversationAnalysisResult res = parseConversationResponse(response.trim(), latency, currentModel, isFallback);
                        mainHandler.post(() -> callback.onSuccess(res));
                        return;
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.w(TAG, "Conversation summary attempt with " + currentModel + " failed: " + lastError);
                }
            }

            // Fallback to offline summary if API models fail
            ConversationAnalysisResult offline = generateOfflineConversationSummary(cleanInput, System.currentTimeMillis() - startTime, "Offline Fallback", true);
            mainHandler.post(() -> callback.onSuccess(offline));
        });
    }

    private static ConversationAnalysisResult parseConversationResponse(String raw, long latency, String model, boolean isFallback) {
        String execSummary = "";
        String keyPoints = "";
        String actionItems = "";

        try {
            int summaryIdx = raw.indexOf("EXECUTIVE SUMMARY");
            int pointsIdx = raw.indexOf("KEY DISCUSSION POINTS");
            int actionIdx = raw.indexOf("PENDING ACTION ITEMS");

            if (summaryIdx != -1 && pointsIdx != -1) {
                execSummary = raw.substring(summaryIdx + "EXECUTIVE SUMMARY".length(), pointsIdx).replaceAll("^[\\s:]+", "").trim();
            }
            if (pointsIdx != -1 && actionIdx != -1) {
                keyPoints = raw.substring(pointsIdx + "KEY DISCUSSION POINTS".length(), actionIdx).replaceAll("^[\\s:]+", "").trim();
            }
            if (actionIdx != -1) {
                actionItems = raw.substring(actionIdx + "PENDING ACTION ITEMS".length()).replaceAll("^[\\s:]+", "").trim();
            }
        } catch (Exception ignored) {}

        if (execSummary.isEmpty() && keyPoints.isEmpty() && actionItems.isEmpty()) {
            execSummary = raw;
        }

        return new ConversationAnalysisResult(execSummary, keyPoints, actionItems, raw, latency, model, isFallback);
    }

    private static ConversationAnalysisResult generateOfflineConversationSummary(String transcript, long latency, String model, boolean isFallback) {
        String[] lines = transcript.split("[\\n.?!]+");
        StringBuilder summarySb = new StringBuilder();
        StringBuilder pointsSb = new StringBuilder();
        StringBuilder actionsSb = new StringBuilder();

        int pointCount = 0;
        int actionCount = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.length() < 6) continue;

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("need to") || lower.contains("will ") || lower.contains("must ") ||
                lower.contains("should ") || lower.contains("follow up") || lower.contains("action") ||
                lower.contains("send ") || lower.contains("call ") || lower.contains("prepare") ||
                lower.contains("review") || lower.contains("deadline") || lower.contains("submit")) {
                actionsSb.append("- [ ] ").append(Character.toUpperCase(line.charAt(0))).append(line.substring(1)).append(" (Owner: Team, Due: ASAP)\n");
                actionCount++;
            } else if (pointCount < 4) {
                pointsSb.append("- ").append(Character.toUpperCase(line.charAt(0))).append(line.substring(1)).append("\n");
                pointCount++;
            }
        }

        summarySb.append("• Recorded meeting covering ").append(Math.max(pointCount + actionCount, 1)).append(" conversation discussion items.\n");
        summarySb.append("• Core objectives outlined and identified for execution.");

        if (actionsSb.length() == 0) {
            actionsSb.append("- [ ] Follow up on conversation discussion points (Owner: Team, Due: ASAP)\n");
        }

        String full = "📋 EXECUTIVE SUMMARY:\n" + summarySb.toString() + "\n\n" +
                      "💡 KEY DISCUSSION POINTS:\n" + pointsSb.toString() + "\n" +
                      "⏳ PENDING ACTION ITEMS:\n" + actionsSb.toString();

        return new ConversationAnalysisResult(summarySb.toString().trim(), pointsSb.toString().trim(),
                actionsSb.toString().trim(), full.trim(), latency, model, isFallback);
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString().trim();
    }
}
