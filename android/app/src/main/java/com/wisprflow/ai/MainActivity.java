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

/**
 * Wispr Flow Main Native Activity
 * Hosts the local WebView app with full hardware acceleration,
 * Web Audio / Microphone capture permissions, and Android OS integration.
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 101;
    private static final int OVERLAY_PERMISSION_REQ_CODE = 102;
    private WebView webView;
    private PermissionRequest pendingAudioPermission;

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
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Bridge for native Android OS integration
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
        });

        // Grant Web Audio / Microphone permission to WebView
        webView.setWebChromeClient(new WebChromeClient() {
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
                webView.reload();
            }
        }
    }

    /**
     * JavaScript Interface callable from web logic
     */
    public class AndroidBridge {

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
}
