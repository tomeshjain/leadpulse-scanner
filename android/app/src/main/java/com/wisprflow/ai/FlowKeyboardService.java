package com.wisprflow.ai;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Wispr Flow Custom Android Keyboard (InputMethodService)
 * Enables voice dictation as an alternative to typing across ALL apps on the mobile phone.
 * Features a glowing Flow AI Mic Spacebar that directly injects polished text into any focused input field.
 */
public class FlowKeyboardService extends InputMethodService {

    private SpeechRecognizer speechRecognizer;
    private TextView statusTextView;
    private Button spaceMicButton;
    private boolean isListening = false;

    @Override
    public View onCreateInputView() {
        LinearLayout keyboardRoot = new LinearLayout(this);
        keyboardRoot.setOrientation(LinearLayout.VERTICAL);
        keyboardRoot.setBackgroundColor(Color.parseColor("#07090e"));
        keyboardRoot.setPadding(8, 8, 8, 12);

        // Top Status / Voice Feedback Bar
        statusTextView = new TextView(this);
        statusTextView.setText("⚡ Wispr Flow AI Keyboard • Tap Spacebar to Dictate");
        statusTextView.setTextColor(Color.parseColor("#818cf8"));
        statusTextView.setTextSize(11);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(0, 4, 0, 8);
        keyboardRoot.addView(statusTextView);

        // Rows definition
        String[][] keyRows = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"}
        };

        for (String[] rowKeys : keyRows) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rowLayout.setPadding(0, 3, 0, 3);

            for (String key : rowKeys) {
                Button btn = createKeyButton(key);
                rowLayout.addView(btn);
            }
            keyboardRoot.addView(rowLayout);
        }

        // Bottom Spacebar Row with Glowing Flow AI Mic
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setPadding(0, 4, 0, 4);

        Button numBtn = createKeyButton("?123");
        LinearLayout.LayoutParams numParams = (LinearLayout.LayoutParams) numBtn.getLayoutParams();
        numParams.weight = 1.3f;
        bottomRow.addView(numBtn);

        Button commaBtn = createKeyButton(",");
        LinearLayout.LayoutParams commaParams = (LinearLayout.LayoutParams) commaBtn.getLayoutParams();
        commaParams.weight = 1.0f;
        bottomRow.addView(commaBtn);

        // Center Spacebar Mic Button
        spaceMicButton = new Button(this);
        spaceMicButton.setText("🎙️ Wispr Flow (Tap to Dictate)");
        spaceMicButton.setTextColor(Color.WHITE);
        spaceMicButton.setTextSize(12);
        spaceMicButton.setAllCaps(false);

        GradientDrawable spaceBg = new GradientDrawable();
        spaceBg.setShape(GradientDrawable.RECTANGLE);
        spaceBg.setCornerRadius(10);
        spaceBg.setColor(Color.parseColor("#1e1b4b"));
        spaceBg.setStroke(2, Color.parseColor("#6366f1"));
        spaceMicButton.setBackground(spaceBg);

        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dpToPx(44), 5.5f);
        spaceParams.setMargins(4, 0, 4, 0);
        spaceMicButton.setLayoutParams(spaceParams);

        spaceMicButton.setOnClickListener(v -> toggleVoiceDictation());
        bottomRow.addView(spaceMicButton);

        Button dotBtn = createKeyButton(".");
        LinearLayout.LayoutParams dotParams = (LinearLayout.LayoutParams) dotBtn.getLayoutParams();
        dotParams.weight = 1.0f;
        bottomRow.addView(dotBtn);

        Button enterBtn = createKeyButton("⏎");
        LinearLayout.LayoutParams enterParams = (LinearLayout.LayoutParams) enterBtn.getLayoutParams();
        enterParams.weight = 1.3f;
        enterBtn.setBackgroundColor(Color.parseColor("#6366f1"));
        bottomRow.addView(enterBtn);

        keyboardRoot.addView(bottomRow);

        initSpeechRecognizer();

        return keyboardRoot;
    }

    private Button createKeyButton(final String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8);
        bg.setColor(Color.parseColor("#151c2a"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(42), 1.0f);
        params.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> handleKeyPress(text));
        return btn;
    }

    private void handleKeyPress(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if ("⌫".equals(key)) {
            ic.deleteSurroundingText(1, 0);
        } else if ("⏎".equals(key)) {
            ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
        } else if ("⇧".equals(key) || "?123".equals(key)) {
            // Handled as shift/symbol toggle
        } else {
            ic.commitText(key, 1);
        }
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    statusTextView.setText("🎙️ Listening... Speak naturally");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    statusTextView.setText("✨ Polishing speech with AI...");
                }
                @Override public void onError(int error) {
                    isListening = false;
                    resetSpaceButton();
                    statusTextView.setText("Voice engine ready • Tap Spacebar to Dictate");
                }
                @Override public void onResults(Bundle results) {
                    isListening = false;
                    resetSpaceButton();
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognized = matches.get(0).trim();
                        // Format with punctuation
                        String formatted = cleanAndPunctuate(recognized);
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(formatted + " ", 1);
                        }
                        statusTextView.setText("✓ Injected: \"" + formatted + "\"");
                    } else {
                        statusTextView.setText("Voice engine ready • Tap Spacebar to Dictate");
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        statusTextView.setText(partial.get(0));
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void toggleVoiceDictation() {
        if (isListening) {
            if (speechRecognizer != null) speechRecognizer.stopListening();
            isListening = false;
            resetSpaceButton();
            statusTextView.setText("Stopping listening...");
        } else {
            if (speechRecognizer == null) initSpeechRecognizer();
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            isListening = true;
            spaceMicButton.setText("🔴 Listening... Tap to Finish");
            spaceMicButton.setBackgroundColor(Color.parseColor("#dc2626"));
            statusTextView.setText("🎙️ Listening... Speak your thoughts");

            if (speechRecognizer != null) {
                speechRecognizer.startListening(intent);
            } else {
                Toast.makeText(this, "Android Speech Recognizer unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void resetSpaceButton() {
        if (spaceMicButton == null) return;
        spaceMicButton.setText("🎙️ Wispr Flow (Tap to Dictate)");
        GradientDrawable spaceBg = new GradientDrawable();
        spaceBg.setShape(GradientDrawable.RECTANGLE);
        spaceBg.setCornerRadius(10);
        spaceBg.setColor(Color.parseColor("#1e1b4b"));
        spaceBg.setStroke(2, Color.parseColor("#6366f1"));
        spaceMicButton.setBackground(spaceBg);
    }

    private String cleanAndPunctuate(String text) {
        if (text == null || text.isEmpty()) return "";
        // Strip filler words
        String cleaned = text.replaceAll("(?i)\\b(um|uh|erm|like|you know|basically)\\b", "").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return text;
        // Capitalize first letter
        cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        // Add period if no ending punctuation
        if (!cleaned.endsWith(".") && !cleaned.endsWith("?") && !cleaned.endsWith("!")) {
            cleaned += ".";
        }
        return cleaned;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
