package com.wisprflow.ai;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Wispr Flow Main Native Activity
 * Hosts the local WebView app with full hardware acceleration,
 * continuous SpeechRecognizer with automatic fallback to standard Android Voice Dialog,
 * and Android OS integration (Clipboard, Overlay, IME Keyboard).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WisprFlow";
    private static final int PERMISSION_REQ_CODE = 101;
    private static final int OVERLAY_PERMISSION_REQ_CODE = 102;
    private static final int SPEECH_DIALOG_REQ_CODE = 103;

    private WebView webView;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private String pendingLanguage = "en-US";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hardware accelerated full-screen WebView
        webView = new WebView(this);
        setContentView(webView);

        configureWebView();
        checkAndRequestPermissions();

        // Load bundled standalone Wispr Flow web application
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Bridge for native Android OS integration & native speech recognition
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("https://wa.me/")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView error: " + description + " (" + failingUrl + ")");
            }
        });

        // Grant Web Audio / Microphone permission to WebView
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WisprFlowJS", consoleMessage.message() + " [" + consoleMessage.lineNumber() + "]");
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                            return;
                        }
                    }
                    request.grant(request.getResources());
                });
            }
        });
    }

    private void checkAndRequestPermissions() {
        String[] permissions = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.VIBRATE
        };

        boolean needed = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed = true;
                break;
            }
        }

        if (needed) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQ_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "✓ Microphone permission granted!", Toast.LENGTH_SHORT).show();
                postToWeb("window.flowSpeech && window.flowSpeech.onNativeStateChange('ready')");
            } else {
                Toast.makeText(this, "Microphone permission is required for voice recognition", Toast.LENGTH_LONG).show();
                postToWeb("window.flowSpeech && window.flowSpeech.onNativeError('permission-denied')");
            }
        }
    }

    /**
     * Continuous Speech Recognizer with real-time audio volume
     */
    private void startContinuousSpeechRecognizer(String language) {
        this.pendingLanguage = (language != null && !language.isEmpty()) ? language : Locale.getDefault().toString();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Continuous SpeechRecognizer unavailable, falling back to standard dialog.");
            launchStandardSpeechDialog(this.pendingLanguage);
            return;
        }

        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    postToWeb("window.flowSpeech && window.flowSpeech.onNativeStateChange('listening')");
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {
                    float normalized = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
                    postToWeb("window.flowSpeech && window.flowSpeech.onNativeVolume(" + normalized + ")");
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    postToWeb("window.flowSpeech && window.flowSpeech.onNativeStateChange('processing')");
                }

                @Override
                public void onError(int error) {
                    Log.w(TAG, "SpeechRecognizer error: " + error);
                    isListening = false;

                    // If error indicates speech service failure, fall back to standard Android dialog
                    if (error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_SERVER) {
                        runOnUiThread(() -> launchStandardSpeechDialog(pendingLanguage));
                        return;
                    }

                    postToWeb("window.flowSpeech && window.flowSpeech.onNativeError(" + error + ")");
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        sendResultToWeb(matches.get(0), true);
                    } else {
                        sendResultToWeb("", true);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        sendResultToWeb(matches.get(0), false);
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, pendingLanguage);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting SpeechRecognizer: " + e.getMessage());
            launchStandardSpeechDialog(this.pendingLanguage);
        }
    }

    /**
     * Standard Universal Android Voice Recognition Dialog (100% compatible on all Android devices)
     */
    private void launchStandardSpeechDialog(String language) {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, (language != null && !language.isEmpty()) ? language : Locale.getDefault().toString());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Wispr Flow AI...");
            startActivityForResult(intent, SPEECH_DIALOG_REQ_CODE);
            postToWeb("window.flowSpeech && window.flowSpeech.onNativeStateChange('listening')");
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported: " + e.getMessage(), Toast.LENGTH_LONG).show();
            postToWeb("window.flowSpeech && window.flowSpeech.onNativeError('no-speech-engine')");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_DIALOG_REQ_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    String recognized = matches.get(0);
                    sendResultToWeb(recognized, true);
                    return;
                }
            }
            postToWeb("window.flowSpeech && window.flowSpeech.onNativeStateChange('stopped')");
        }
    }

    private void stopNativeSpeechRecognizer() {
        if (speechRecognizer != null && isListening) {
            isListening = false;
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
        }
    }

    private void sendResultToWeb(String recognized, boolean isFinal) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("text", recognized != null ? recognized : "");
            obj.put("isFinal", isFinal);
            postToWeb("window.flowSpeech && window.flowSpeech.onNativeResult(" + obj.toString() + ".text, " + isFinal + ")");
        } catch (Exception e) {
            String safe = (recognized != null ? recognized : "").replace("'", "\\'").replace("\n", " ");
            postToWeb("window.flowSpeech && window.flowSpeech.onNativeResult('" + safe + "', " + isFinal + ")");
        }
    }

    private void postToWeb(String jsCode) {
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(jsCode, null));
        }
    }

    /**
     * JavaScript Interface callable from web logic
     */
    public class AndroidBridge {

        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }

        @JavascriptInterface
        public void startNativeSpeech(String language) {
            runOnUiThread(() -> {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQ_CODE);
                } else {
                    startContinuousSpeechRecognizer(language);
                }
            });
        }

        @JavascriptInterface
        public void launchSpeechDialog(String language) {
            runOnUiThread(() -> {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQ_CODE);
                } else {
                    launchStandardSpeechDialog(language);
                }
            });
        }

        @JavascriptInterface
        public void stopNativeSpeech() {
            runOnUiThread(() -> stopNativeSpeechRecognizer());
        }

        @JavascriptInterface
        public void copyToClipboard(String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Wispr Flow", text);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(MainActivity.this, "✓ Copied to phone clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void openKeyboardSettings() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Toast.makeText(MainActivity.this, "Enable 'Wispr Flow AI Keyboard' in list", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void startFloatingOverlay() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                    Toast.makeText(MainActivity.this, "Allow 'Display over other apps' to use Floating Mic", Toast.LENGTH_LONG).show();
                } else {
                    Intent serviceIntent = new Intent(MainActivity.this, FlowOverlayService.class);
                    startService(serviceIntent);
                    Toast.makeText(MainActivity.this, "Wispr Flow Floating HUD Active!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void triggerHaptic(int ms) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(ms);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
