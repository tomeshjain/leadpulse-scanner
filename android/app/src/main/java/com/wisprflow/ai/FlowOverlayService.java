package com.wisprflow.ai;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Wispr Flow Floating Overlay HUD Service
 * Displays a draggable microphone bubble floating on top of any active Android app
 * (WhatsApp, Chrome, Slack, Gmail, etc.) allowing 1-tap voice dictation anywhere.
 */
public class FlowOverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private Button micBtn;
    private TextView labelView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = createOverlayView();

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 200;

        // Make overlay draggable across the screen
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, params);
        initSpeech();
    }

    private View createOverlayView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(8, 6, 12, 6);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(100);
        bg.setColor(Color.parseColor("#0e131d"));
        bg.setStroke(2, Color.parseColor("#6366f1"));
        layout.setBackground(bg);

        micBtn = new Button(this);
        micBtn.setText("🎙️");
        micBtn.setTextSize(18);
        GradientDrawable micBg = new GradientDrawable();
        micBg.setShape(GradientDrawable.OVAL);
        micBg.setColor(Color.parseColor("#6366f1"));
        micBtn.setBackground(micBg);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        micBtn.setLayoutParams(btnParams);
        micBtn.setOnClickListener(v -> toggleDictation());
        layout.addView(micBtn);

        labelView = new TextView(this);
        labelView.setText(" Flow");
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(12);
        labelView.setPadding(8, 0, 8, 0);
        layout.addView(labelView);

        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.parseColor("#94a3b8"));
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setTextSize(12);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        closeBtn.setLayoutParams(closeParams);
        closeBtn.setOnClickListener(v -> stopSelf());
        layout.addView(closeBtn);

        return layout;
    }

    private void initSpeech() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    labelView.setText(" Listening...");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    labelView.setText(" Polishing...");
                }
                @Override public void onError(int error) {
                    isListening = false;
                    labelView.setText(" Flow");
                    micBtn.setBackgroundColor(Color.parseColor("#6366f1"));
                }
                @Override public void onResults(Bundle results) {
                    isListening = false;
                    labelView.setText(" ✓ Copied");
                    micBtn.setBackgroundColor(Color.parseColor("#6366f1"));

                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognized = matches.get(0).trim();
                        String formatted = polishSimple(recognized);

                        // Copy to phone clipboard
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Wispr Flow", formatted);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                        }

                        // Haptic feedback
                        vibrate(25);
                        Toast.makeText(FlowOverlayService.this, "✓ Copied to clipboard! Ready to paste in app.", Toast.LENGTH_LONG).show();
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void toggleDictation() {
        if (isListening) {
            if (speechRecognizer != null) speechRecognizer.stopListening();
            isListening = false;
            labelView.setText(" Flow");
            micBtn.setBackgroundColor(Color.parseColor("#6366f1"));
        } else {
            if (speechRecognizer == null) initSpeech();
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

            isListening = true;
            labelView.setText(" Listening...");
            micBtn.setBackgroundColor(Color.parseColor("#dc2626"));
            vibrate(15);

            if (speechRecognizer != null) {
                speechRecognizer.startListening(intent);
            }
        }
    }

    private String polishSimple(String text) {
        if (text == null || text.isEmpty()) return "";
        String cleaned = text.replaceAll("(?i)\\b(um|uh|erm|like|you know|basically)\\b", "").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return text;
        cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        if (!cleaned.endsWith(".") && !cleaned.endsWith("?") && !cleaned.endsWith("!")) {
            cleaned += ".";
        }
        return cleaned;
    }

    private void vibrate(int ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(ms);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
