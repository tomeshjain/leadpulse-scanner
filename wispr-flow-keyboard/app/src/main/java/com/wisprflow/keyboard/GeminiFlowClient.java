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

    public interface CardScanCallback {
        void onSuccess(String formattedContactCard, long latencyMs);
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
                                String text = collapseSpokenDigits(parts.getJSONObject(0).optString("text", "").trim());
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

        // 2b. Collapse spaced digits (e.g. phone numbers, OTPs: "9 8 7 6" -> "9876")
        text = collapseSpokenDigits(text);

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

    /**
     * Collapses spoken consecutive digits (e.g. phone numbers, OTPs, PINs) into continuous numbers without spaces.
     */
    public static String collapseSpokenDigits(String text) {
        if (text == null || text.isEmpty()) return "";

        // Convert spoken single-digit words into numbers
        String[] digitWords = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        for (int i = 0; i < digitWords.length; i++) {
            text = text.replaceAll("(?i)\\b" + digitWords[i] + "\\b", String.valueOf(i));
        }

        // Collapse sequences of digits separated by whitespace: "9 8 7 6" -> "9876"
        for (int i = 0; i < 6; i++) {
            text = text.replaceAll("(\\b\\d)\\s+(?=\\d\\b)", "$1");
            text = text.replaceAll("(\\d)\\s+(\\d)", "$1$2");
        }
        return text;
    }

    /**
     * Extracts structured business / contact card information from a JPEG image using Gemini Vision API.
     */
    public static void scanContactCard(final byte[] jpegBytes,
                                       final String apiKey,
                                       final String modelName,
                                       final CardScanCallback callback) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            mainHandler.post(() -> callback.onError("No image data provided for contact card scanning"));
            return;
        }

        final long startTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            // Offline fallback template if no API key is configured
            final String fallbackCard = "📇 CONTACT CARD\n" +
                    "👤 Name: [Scanned Contact]\n" +
                    "💼 Title: Business Executive\n" +
                    "🏢 Company: Organization\n" +
                    "📞 Phone: +1 (555) 019-2834\n" +
                    "✉️ Email: contact@company.com\n" +
                    "🌐 Website: www.company.com\n" +
                    "📍 Address: 100 Innovation Way\n" +
                    "📝 Notes: Captured via Wispr Card Scanner (Set Gemini API Key for AI OCR)";
            mainHandler.post(() -> callback.onSuccess(fallbackCard, System.currentTimeMillis() - startTime));
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
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                // Encode JPEG bytes to base64
                String base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);

                // Build Multimodal Prompt
                JSONObject requestJson = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject contentObj = new JSONObject();
                contentObj.put("role", "user");

                JSONArray partsArray = new JSONArray();

                // 1. Image part
                JSONObject imagePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", "image/jpeg");
                inlineData.put("data", base64Image);
                imagePart.put("inline_data", inlineData);
                partsArray.put(imagePart);

                // 2. Text prompt part
                JSONObject textPart = new JSONObject();
                String promptText = "You are an expert OCR business card and contact card scanner.\n" +
                        "Carefully read all visible text in this business card image and extract all contact details.\n" +
                        "Format the output strictly as follows:\n\n" +
                        "📇 CONTACT CARD\n" +
                        "👤 Name: [Full Name]\n" +
                        "💼 Title: [Designation / Current Role]\n" +
                        "🏢 Company: [Company or Organization Name]\n" +
                        "📞 Phone: [Phone Number(s)]\n" +
                        "✉️ Email: [Email Address(es)]\n" +
                        "🔗 LinkedIn: [LinkedIn Profile URL or handle]\n" +
                        "🏛️ Past Work / Experience: [Past companies, previous roles, or background if mentioned]\n" +
                        "🌐 Website: [Website URL]\n" +
                        "📍 Address: [Physical Location or Address]\n" +
                        "📝 Notes: [Services, tagline, or other details]\n\n" +
                        "If a field is not on the card, write N/A for that field. Keep it clean and structured.";
                textPart.put("text", promptText);
                partsArray.put(textPart);

                contentObj.put("parts", partsArray);
                contentsArray.put(contentObj);
                requestJson.put("contents", contentsArray);

                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.1);
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
                        JSONObject firstCandidate = candidates.getJSONObject(0);
                        JSONObject content = firstCandidate.optJSONObject("content");
                        if (content != null) {
                            JSONArray respParts = content.optJSONArray("parts");
                            if (respParts != null && respParts.length() > 0) {
                                String extractedCard = respParts.getJSONObject(0).optString("text", "").trim();
                                final long latency = System.currentTimeMillis() - startTime;
                                mainHandler.post(() -> callback.onSuccess(extractedCard, latency));
                                return;
                            }
                        }
                    }
                    mainHandler.post(() -> callback.onError("No text returned by Gemini"));
                } else {
                    Log.e(TAG, "Gemini Vision error " + responseCode + ": " + responseSb);
                    mainHandler.post(() -> callback.onError("API Error " + responseCode + ": " + responseSb));
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception scanning card", e);
                mainHandler.post(() -> callback.onError("Scan error: " + e.getMessage()));
            }
        });
    }

    /**
     * Formats raw text extracted from on-device local OCR using Gemini text model.
     * Uses ultra low latency gemini-2.0-flash with unlimited free tier requests.
     */
    public static void formatExtractedCardText(final String rawOcrText,
                                               final String apiKey,
                                               final String modelName,
                                               final CardScanCallback callback) {
        if (rawOcrText == null || rawOcrText.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("No OCR text to format"));
            return;
        }

        final long startTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            final String fallbackCard = parseRawOcrLocal(rawOcrText);
            mainHandler.post(() -> callback.onSuccess(fallbackCard, System.currentTimeMillis() - startTime));
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
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);

                JSONObject requestJson = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject contentObj = new JSONObject();
                contentObj.put("role", "user");
                JSONArray partsArray = new JSONArray();

                JSONObject textPart = new JSONObject();
                String promptText = "You are an expert contact card parser.\n" +
                        "Here is raw OCR text scanned from a business card:\n\"\"\"\n" + rawOcrText + "\n\"\"\"\n" +
                        "Extract all contact details, including LinkedIn profile and past work / prior experience if present.\n" +
                        "Format strictly as follows:\n\n" +
                        "📇 CONTACT CARD\n" +
                        "👤 Name: [Full Name]\n" +
                        "💼 Title: [Designation / Current Role]\n" +
                        "🏢 Company: [Company or Organization Name]\n" +
                        "📞 Phone: [Phone Number(s)]\n" +
                        "✉️ Email: [Email Address(es)]\n" +
                        "🔗 LinkedIn: [LinkedIn Profile URL or handle]\n" +
                        "🏛️ Past Work / Experience: [Past companies, previous roles, or background if mentioned]\n" +
                        "🌐 Website: [Website URL]\n" +
                        "📍 Address: [Physical Location or Address]\n" +
                        "📝 Notes: [Services, tagline, or other details]\n\n" +
                        "If a field is not present in the text, write N/A for that field. Keep it clean and structured.";
                textPart.put("text", promptText);
                partsArray.put(textPart);

                contentObj.put("parts", partsArray);
                contentsArray.put(contentObj);
                requestJson.put("contents", contentsArray);

                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.1);
                genConfig.put("maxOutputTokens", 512);
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
                            JSONArray respParts = content.optJSONArray("parts");
                            if (respParts != null && respParts.length() > 0) {
                                String text = respParts.getJSONObject(0).optString("text", "").trim();
                                final long latency = System.currentTimeMillis() - startTime;
                                mainHandler.post(() -> callback.onSuccess(text, latency));
                                return;
                            }
                        }
                    }
                }

                // Fallback to local regex formatting
                final String fallback = parseRawOcrLocal(rawOcrText);
                final long latency = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> callback.onSuccess(fallback, latency));
            } catch (Exception e) {
                final String fallback = parseRawOcrLocal(rawOcrText);
                final long latency = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> callback.onSuccess(fallback, latency));
            }
        });
    }

    /**
     * Intelligent local regex fallback to structure raw OCR text without network/API key.
     */
    public static String parseRawOcrLocal(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "📇 CONTACT CARD\n" +
                    "👤 Name: [Scanned Contact]\n" +
                    "💼 Title: Business Executive\n" +
                    "🏢 Company: Organization\n" +
                    "📞 Phone: N/A\n" +
                    "✉️ Email: N/A\n" +
                    "🔗 LinkedIn: N/A\n" +
                    "🏛️ Past Work / Experience: N/A\n" +
                    "🌐 Website: N/A\n" +
                    "📍 Address: N/A\n" +
                    "📝 Notes: Captured via Local OCR";
        }

        String name = "";
        String title = "";
        String company = "";
        String phone = "";
        String email = "";
        String linkedin = "";
        String pastWork = "";
        String website = "";
        String address = "";

        String[] lines = rawText.split("\n");
        java.util.regex.Pattern emailPattern = java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        java.util.regex.Pattern phonePattern = java.util.regex.Pattern.compile("(\\+?[0-9]{1,4}[-\\s]?)?(\\(?\\d{2,4}\\)?[-\\s]?)?[\\d\\s-]{7,15}");
        java.util.regex.Pattern linkedinPattern = java.util.regex.Pattern.compile("(?:https?:\\/\\/)?(?:www\\.)?linkedin\\.com\\/(?:in\\/)?([a-zA-Z0-9_-]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern webPattern = java.util.regex.Pattern.compile("(https?:\\/\\/|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

        List<String> remainingLines = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Check Email
            java.util.regex.Matcher em = emailPattern.matcher(line);
            if (em.find() && email.isEmpty()) {
                email = em.group();
                continue;
            }

            // Check LinkedIn
            java.util.regex.Matcher lm = linkedinPattern.matcher(line);
            if (lm.find() && linkedin.isEmpty()) {
                linkedin = lm.group();
                continue;
            }

            // Check Phone
            java.util.regex.Matcher pm = phonePattern.matcher(line);
            if (pm.find() && phone.isEmpty() && line.replaceAll("[^0-9]", "").length() >= 7) {
                phone = pm.group().trim();
                continue;
            }

            // Check Website
            java.util.regex.Matcher wm = webPattern.matcher(line);
            if (wm.find() && website.isEmpty() && !line.contains("linkedin.com")) {
                website = wm.group();
                continue;
            }

            // Check Past Work / Experience
            if (line.toLowerCase().contains("ex-") || line.toLowerCase().contains("previously") ||
                line.toLowerCase().contains("former") || line.toLowerCase().contains("past work")) {
                pastWork += (pastWork.isEmpty() ? "" : "; ") + line;
                continue;
            }

            remainingLines.add(line);
        }

        // Infer Name, Title, Company from remaining lines
        if (remainingLines.size() > 0) name = remainingLines.get(0);
        if (remainingLines.size() > 1) title = remainingLines.get(1);
        if (remainingLines.size() > 2) company = remainingLines.get(2);
        if (remainingLines.size() > 3) address = remainingLines.get(3);

        return "📇 CONTACT CARD\n" +
                "👤 Name: " + (name.isEmpty() ? "N/A" : name) + "\n" +
                "💼 Title: " + (title.isEmpty() ? "N/A" : title) + "\n" +
                "🏢 Company: " + (company.isEmpty() ? "N/A" : company) + "\n" +
                "📞 Phone: " + (phone.isEmpty() ? "N/A" : phone) + "\n" +
                "✉️ Email: " + (email.isEmpty() ? "N/A" : email) + "\n" +
                "🔗 LinkedIn: " + (linkedin.isEmpty() ? "N/A" : linkedin) + "\n" +
                "🏛️ Past Work / Experience: " + (pastWork.isEmpty() ? "N/A" : pastWork) + "\n" +
                "🌐 Website: " + (website.isEmpty() ? "N/A" : website) + "\n" +
                "📍 Address: " + (address.isEmpty() ? "N/A" : address) + "\n" +
                "📝 Notes: Extracted via On-Device Local OCR";
    }
}
