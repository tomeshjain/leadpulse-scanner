package com.wisprflow.keyboard;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * High-Performance Gemini Multimodal Live WebSocket Client.
 * Connects directly to Google's real-time bidirectional streaming API:
 * wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent
 */
public class GeminiLiveWebSocketClient {

    private static final String TAG = "GeminiLiveWS";
    private static final String LIVE_WS_ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent";

    private final String apiKey;
    private final String modelName;
    private final LiveCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean isConnected = false;

    public interface LiveCallback {
        void onConnected();
        void onTextReceived(String textChunk, boolean isDone);
        void onError(String errorMessage);
        void onClosed(String reason);
    }

    public GeminiLiveWebSocketClient(String apiKey, String modelName, LiveCallback callback) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.modelName = (modelName != null && !modelName.isEmpty()) ? modelName : "gemini-2.0-flash-exp";
        this.callback = callback;
    }

    /**
     * Opens live bidirectional WebSocket connection.
     */
    public synchronized void connect() {
        if (apiKey.isEmpty()) {
            notifyError("No Gemini API key configured for Live WebSocket");
            return;
        }

        httpClient = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .pingInterval(10, TimeUnit.SECONDS)
                .build();

        String wsUrl = LIVE_WS_ENDPOINT + "?key=" + apiKey;
        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnected = true;
                Log.d(TAG, "Gemini Live WebSocket Handshake Established: " + response.code());

                // Send initial Session Setup configuration
                try {
                    JSONObject setupMsg = new JSONObject();
                    JSONObject setupBody = new JSONObject();

                    String fullModel = modelName.startsWith("models/") ? modelName : ("models/" + modelName);
                    setupBody.put("model", fullModel);

                    JSONObject genConfig = new JSONObject();
                    JSONArray modalities = new JSONArray();
                    modalities.put("TEXT");
                    genConfig.put("responseModalities", modalities);
                    setupBody.put("generationConfig", genConfig);

                    setupMsg.put("setup", setupBody);
                    ws.send(setupMsg.toString());

                    mainHandler.post(() -> {
                        if (callback != null) callback.onConnected();
                    });
                } catch (Exception e) {
                    notifyError("Failed sending Live Setup: " + e.getMessage());
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject root = new JSONObject(text);

                    // Parse serverContent parts
                    JSONObject serverContent = root.optJSONObject("serverContent");
                    if (serverContent != null) {
                        JSONObject modelTurn = serverContent.optJSONObject("modelTurn");
                        if (modelTurn != null) {
                            JSONArray parts = modelTurn.optJSONArray("parts");
                            if (parts != null) {
                                for (int i = 0; i < parts.length(); i++) {
                                    JSONObject part = parts.getJSONObject(i);
                                    String textChunk = part.optString("text", "");
                                    if (!textChunk.isEmpty()) {
                                        mainHandler.post(() -> {
                                            if (callback != null) callback.onTextReceived(textChunk, false);
                                        });
                                    }
                                }
                            }
                        }

                        boolean turnComplete = serverContent.optBoolean("turnComplete", false);
                        if (turnComplete) {
                            mainHandler.post(() -> {
                                if (callback != null) callback.onTextReceived("", true);
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Live message", e);
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnected = false;
                String errorMsg = t.getMessage();
                if (response != null) {
                    errorMsg = "HTTP " + response.code() + ": " + response.message();
                }
                notifyError("Live WebSocket Error: " + errorMsg);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                mainHandler.post(() -> {
                    if (callback != null) callback.onClosed(reason != null ? reason : "Normal closure");
                });
            }
        });
    }

    /**
     * Streams real-time audio data to Gemini Live.
     */
    public synchronized void sendAudioChunk(byte[] pcmData) {
        if (!isConnected || webSocket == null || pcmData == null) return;
        try {
            JSONObject msg = new JSONObject();
            JSONObject realtimeInput = new JSONObject();
            JSONArray mediaChunks = new JSONArray();

            JSONObject chunk = new JSONObject();
            chunk.put("mimeType", "audio/pcm;rate=16000");
            chunk.put("data", Base64.encodeToString(pcmData, Base64.NO_WRAP));
            mediaChunks.put(chunk);

            realtimeInput.put("mediaChunks", mediaChunks);
            msg.put("realtimeInput", realtimeInput);

            webSocket.send(msg.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error sending live audio chunk", e);
        }
    }

    /**
     * Sends live text prompt over WebSocket.
     */
    public synchronized void sendClientContent(String textPrompt, boolean endOfTurn) {
        if (!isConnected || webSocket == null || textPrompt == null) return;
        try {
            JSONObject msg = new JSONObject();
            JSONObject clientContent = new JSONObject();
            JSONArray turns = new JSONArray();

            JSONObject turn = new JSONObject();
            turn.put("role", "user");
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", textPrompt);
            parts.put(part);
            turn.put("parts", parts);
            turns.put(turn);

            clientContent.put("turns", turns);
            clientContent.put("turnComplete", endOfTurn);
            msg.put("clientContent", clientContent);

            webSocket.send(msg.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error sending clientContent", e);
        }
    }

    public synchronized void close() {
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Client closed");
            } catch (Exception ignored) {}
        }
        isConnected = false;
    }

    private void notifyError(String err) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(err);
        });
    }

    /**
     * Static utility to test Gemini Live WebSocket connectivity.
     */
    public static void testConnection(final String apiKey, final TestCallback testCallback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (testCallback != null) {
                testCallback.onResult(false, "API Key is required to test Gemini Live WebSocket");
            }
            return;
        }

        final long start = System.currentTimeMillis();
        GeminiLiveWebSocketClient client = new GeminiLiveWebSocketClient(apiKey, "gemini-2.0-flash-exp", new LiveCallback() {
            @Override
            public void onConnected() {
                long latency = System.currentTimeMillis() - start;
                if (testCallback != null) {
                    testCallback.onResult(true, "✓ WebSocket Handshake Success (101 Switching Protocols • " + latency + "ms)");
                }
            }

            @Override
            public void onTextReceived(String textChunk, boolean isDone) {}

            @Override
            public void onError(String errorMessage) {
                if (testCallback != null) {
                    testCallback.onResult(false, "WebSocket Connection Failed: " + errorMessage);
                }
            }

            @Override
            public void onClosed(String reason) {}
        });

        client.connect();
    }

    public interface TestCallback {
        void onResult(boolean success, String message);
    }
}
