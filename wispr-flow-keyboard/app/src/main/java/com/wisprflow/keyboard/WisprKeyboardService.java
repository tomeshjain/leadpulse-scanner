package com.wisprflow.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Wispr Flow Custom Android Keyboard (InputMethodService)
 * Replaces mobile typing system-wide across all apps (WhatsApp, Chrome, Slack, Notes, etc.)
 * with an integrated voice dictation spacebar and Gemini 2.0 Flash AI text transformation.
 */
public class WisprKeyboardService extends InputMethodService {

    private static final String TAG = "WisprKeyboardService";

    private KeyboardPreferences prefs;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isShifted = false;
    private boolean isCapsLocked = false;
    private boolean isSymbolMode = false;
    private long lastShiftClickTime = 0;

    private LinearLayout keyboardRoot;
    private TextView statusTextView;
    private Button modeChipButton;
    private Button spaceMicButton;
    private LinearLayout keysContainer;
    private Vibrator vibrator;

    private final Handler backspaceHandler = new Handler(Looper.getMainLooper());
    private Runnable backspaceRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new KeyboardPreferences(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public View onCreateInputView() {
        keyboardRoot = new LinearLayout(this);
        keyboardRoot.setOrientation(LinearLayout.VERTICAL);
        keyboardRoot.setBackgroundColor(Color.parseColor("#07090e"));
        keyboardRoot.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(8));

        // 1. Top Action & Status Bar
        buildTopBar();

        // 2. Main QWERTY / Symbol Keys Container
        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        keysContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        keyboardRoot.addView(keysContainer);

        renderKeyRows();

        // 3. Initialize Speech Recognizer
        initSpeechRecognizer();

        return keyboardRoot;
    }

    private void buildTopBar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#0b0f17"));
        topBar.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(38));
        topParams.bottomMargin = dpToPx(4);
        topBar.setLayoutParams(topParams);

        // Active Flow Mode Switcher Chip (Tap to cycle: Polish -> Casual -> Email -> Notes -> Raw -> Code -> Action)
        modeChipButton = new Button(this);
        updateModeChipText();
        modeChipButton.setTextSize(11);
        modeChipButton.setTextColor(Color.parseColor("#c7d2fe"));
        modeChipButton.setAllCaps(false);

        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(Color.parseColor("#1e1b4b"));
        chipBg.setStroke(dpToPx(1), Color.parseColor("#6366f1"));
        chipBg.setCornerRadius(dpToPx(14));
        modeChipButton.setBackground(chipBg);
        modeChipButton.setPadding(dpToPx(10), 0, dpToPx(10), 0);

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        modeChipButton.setLayoutParams(chipParams);
        modeChipButton.setOnClickListener(v -> cycleNextMode());
        topBar.addView(modeChipButton);

        // Center Voice / Status Text
        statusTextView = new TextView(this);
        statusTextView.setText("Tap Spacebar to Dictate with AI");
        statusTextView.setTextColor(Color.parseColor("#818cf8"));
        statusTextView.setTextSize(11);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setSingleLine(true);
        statusTextView.setPadding(dpToPx(8), 0, dpToPx(8), 0);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        statusTextView.setLayoutParams(statusParams);
        topBar.addView(statusTextView);

        // 📋 Paste Button
        Button pasteBtn = new Button(this);
        pasteBtn.setText("📋");
        pasteBtn.setTextSize(13);
        pasteBtn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(30));
        pasteBtn.setLayoutParams(pasteParams);
        pasteBtn.setOnClickListener(v -> pasteFromClipboard());
        topBar.addView(pasteBtn);

        // ⚙️ Settings Key (Opens SettingsActivity to configure Gemini key and select model)
        Button settingsBtn = new Button(this);
        settingsBtn.setText("⚙️");
        settingsBtn.setTextSize(13);
        settingsBtn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(30));
        settingsBtn.setLayoutParams(setParams);
        settingsBtn.setOnClickListener(v -> openSettingsActivity());
        topBar.addView(settingsBtn);

        keyboardRoot.addView(topBar);
    }

    private void renderKeyRows() {
        if (keysContainer == null) return;
        keysContainer.removeAllViews();

        if (isSymbolMode) {
            renderSymbolRows();
        } else {
            renderAlphabetRows();
        }

        // Bottom Spacebar Row (with Glowing Voice Mic)
        renderSpacebarRow();
    }

    private void renderAlphabetRows() {
        String[][] alphaRows = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"shift", "z", "x", "c", "v", "b", "n", "m", "backspace"}
        };

        for (int r = 0; r < alphaRows.length; r++) {
            String[] rowKeys = alphaRows[r];
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rowLayout.setPadding(0, dpToPx(2), 0, dpToPx(2));

            // Small padding for middle row to mimic standard keyboard stagger
            if (r == 1) {
                rowLayout.setPadding(dpToPx(14), dpToPx(2), dpToPx(14), dpToPx(2));
            }

            for (String key : rowKeys) {
                View keyView = createKeyView(key);
                rowLayout.addView(keyView);
            }
            keysContainer.addView(rowLayout);
        }
    }

    private void renderSymbolRows() {
        String[][] symRows = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
            {"abc", "*", "\"", "'", ":", ";", "!", "?", "backspace"}
        };

        for (String[] rowKeys : symRows) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rowLayout.setPadding(0, dpToPx(2), 0, dpToPx(2));

            for (String key : rowKeys) {
                View keyView = createKeyView(key);
                rowLayout.addView(keyView);
            }
            keysContainer.addView(rowLayout);
        }
    }

    private void renderSpacebarRow() {
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setPadding(0, dpToPx(3), 0, dpToPx(3));

        // ?123 / ABC Toggle Key
        Button numBtn = createButton(isSymbolMode ? "ABC" : "?123", true);
        LinearLayout.LayoutParams numParams = (LinearLayout.LayoutParams) numBtn.getLayoutParams();
        numParams.weight = 1.3f;
        numBtn.setOnClickListener(v -> {
            isSymbolMode = !isSymbolMode;
            renderKeyRows();
            triggerHaptic(15);
        });
        bottomRow.addView(numBtn);

        // Comma
        Button commaBtn = createButton(",", false);
        LinearLayout.LayoutParams commaParams = (LinearLayout.LayoutParams) commaBtn.getLayoutParams();
        commaParams.weight = 1.0f;
        commaBtn.setOnClickListener(v -> insertText(","));
        bottomRow.addView(commaBtn);

        // Center Spacebar Mic Button (Glowing Flow Voice Trigger)
        spaceMicButton = new Button(this);
        resetSpaceButton();
        spaceMicButton.setTextColor(Color.WHITE);
        spaceMicButton.setTextSize(12);
        spaceMicButton.setAllCaps(false);

        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dpToPx(44), 5.4f);
        spaceParams.setMargins(dpToPx(3), 0, dpToPx(3), 0);
        spaceMicButton.setLayoutParams(spaceParams);

        // Click to toggle voice dictation, hold for regular space
        spaceMicButton.setOnClickListener(v -> toggleVoiceDictation());
        spaceMicButton.setOnLongClickListener(v -> {
            insertText(" ");
            triggerHaptic(20);
            return true;
        });
        bottomRow.addView(spaceMicButton);

        // Dot / Period
        Button dotBtn = createButton(".", false);
        LinearLayout.LayoutParams dotParams = (LinearLayout.LayoutParams) dotBtn.getLayoutParams();
        dotParams.weight = 1.0f;
        dotBtn.setOnClickListener(v -> insertText("."));
        bottomRow.addView(dotBtn);

        // Enter Key
        Button enterBtn = createButton("⏎", true);
        enterBtn.setTextColor(Color.WHITE);
        enterBtn.setBackgroundColor(Color.parseColor("#4f46e5"));
        LinearLayout.LayoutParams enterParams = (LinearLayout.LayoutParams) enterBtn.getLayoutParams();
        enterParams.weight = 1.3f;
        enterBtn.setOnClickListener(v -> handleEnterKey());
        bottomRow.addView(enterBtn);

        keysContainer.addView(bottomRow);
    }

    private View createKeyView(final String key) {
        if ("shift".equals(key)) {
            final Button shiftBtn = createButton(isCapsLocked ? "⇪" : (isShifted ? "⇧" : "⇧"), true);
            if (isShifted || isCapsLocked) {
                shiftBtn.setBackgroundColor(Color.parseColor("#4f46e5"));
                shiftBtn.setTextColor(Color.WHITE);
            }
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) shiftBtn.getLayoutParams();
            p.weight = 1.4f;
            shiftBtn.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - lastShiftClickTime < 350) {
                    isCapsLocked = !isCapsLocked;
                    isShifted = isCapsLocked;
                } else {
                    if (isCapsLocked) {
                        isCapsLocked = false;
                        isShifted = false;
                    } else {
                        isShifted = !isShifted;
                    }
                }
                lastShiftClickTime = now;
                renderKeyRows();
                triggerHaptic(15);
            });
            return shiftBtn;
        }

        if ("backspace".equals(key)) {
            Button bsBtn = createButton("⌫", true);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) bsBtn.getLayoutParams();
            p.weight = 1.4f;

            bsBtn.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        triggerHaptic(15);
                        handleBackspace();
                        backspaceRunnable = new Runnable() {
                            @Override
                            public void run() {
                                handleBackspace();
                                backspaceHandler.postDelayed(this, 60);
                            }
                        };
                        backspaceHandler.postDelayed(backspaceRunnable, 400);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        backspaceHandler.removeCallbacks(backspaceRunnable);
                        return true;
                }
                return false;
            });
            return bsBtn;
        }

        if ("abc".equals(key)) {
            Button abcBtn = createButton("ABC", true);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) abcBtn.getLayoutParams();
            p.weight = 1.4f;
            abcBtn.setOnClickListener(v -> {
                isSymbolMode = false;
                renderKeyRows();
                triggerHaptic(15);
            });
            return abcBtn;
        }

        // Standard character key
        final String displayChar = (isShifted || isCapsLocked) ? key.toUpperCase(Locale.US) : key.toLowerCase(Locale.US);
        Button charBtn = createButton(displayChar, false);
        charBtn.setOnClickListener(v -> {
            insertText(displayChar);
            if (isShifted && !isCapsLocked) {
                isShifted = false;
                renderKeyRows();
            }
        });
        return charBtn;
    }

    private Button createButton(String text, boolean isFunctionKey) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isFunctionKey ? 14 : 17);
        btn.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(7));
        bg.setColor(Color.parseColor(isFunctionKey ? "#1a2332" : "#141b27"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(42), 1.0f);
        params.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void insertText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            triggerHaptic(12);
        }
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void handleEnterKey() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            EditorInfo ei = getCurrentInputEditorInfo();
            int action = (ei != null) ? (ei.imeOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) : 0;

            if (action == EditorInfo.IME_ACTION_GO ||
                action == EditorInfo.IME_ACTION_SEARCH ||
                action == EditorInfo.IME_ACTION_SEND ||
                action == EditorInfo.IME_ACTION_NEXT ||
                action == EditorInfo.IME_ACTION_DONE) {
                ic.performEditorAction(action);
            } else {
                ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
            }
            triggerHaptic(20);
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
            if (text != null && text.length() > 0) {
                insertText(text.toString());
                statusTextView.setText("✓ Pasted clipboard text");
            }
        }
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void cycleNextMode() {
        FlowMode[] modes = FlowMode.values();
        FlowMode current = FlowMode.fromId(prefs.getFlowMode());
        int nextIndex = (current.ordinal() + 1) % modes.length;
        FlowMode next = modes[nextIndex];
        prefs.setFlowMode(next.getId());
        updateModeChipText();
        statusTextView.setText("Mode: " + next.getDisplayName());
        triggerHaptic(25);
    }

    private void updateModeChipText() {
        if (modeChipButton != null) {
            FlowMode mode = FlowMode.fromId(prefs.getFlowMode());
            modeChipButton.setText(mode.getDisplayName());
        }
    }

    // --- VOICE RECOGNITION & GEMINI AI POLISHING PIPELINE ---

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    statusTextView.setText("🎙️ Listening... Speak naturally");
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Visual pulse based on volume
                    if (isListening && spaceMicButton != null) {
                        int dots = Math.max(1, Math.min(3, (int) ((rmsdB + 2) / 3)));
                        StringBuilder sb = new StringBuilder("🔴 Listening");
                        for (int i = 0; i < dots; i++) sb.append(".");
                        spaceMicButton.setText(sb.toString());
                    }
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    statusTextView.setText("✨ AI Polishing speech...");
                }

                @Override
                public void onError(int error) {
                    Log.w(TAG, "Speech recognition error: " + error);
                    isListening = false;
                    resetSpaceButton();
                    statusTextView.setText("Voice ready • Tap Spacebar to Dictate");
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    resetSpaceButton();
                    ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        processSpokenText(matches.get(0));
                    } else {
                        statusTextView.setText("No speech heard • Tap Spacebar to retry");
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        statusTextView.setText(matches.get(0));
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void toggleVoiceDictation() {
        if (isListening) {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.stopListening();
                } catch (Exception ignored) {}
            }
            isListening = false;
            resetSpaceButton();
            statusTextView.setText("Stopping listening...");
        } else {
            if (speechRecognizer == null) initSpeechRecognizer();

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Toast.makeText(this, "Android Speech Recognizer not available on this device", Toast.LENGTH_SHORT).show();
                statusTextView.setText("Voice recognition unavailable");
                return;
            }

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            isListening = true;
            triggerHaptic(30);

            // Active visual feedback
            spaceMicButton.setText("🔴 Listening... Tap to Finish");
            spaceMicButton.setBackgroundColor(Color.parseColor("#dc2626"));
            statusTextView.setText("🎙️ Listening... Speak your thoughts");

            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                isListening = false;
                resetSpaceButton();
                statusTextView.setText("Error starting voice engine");
            }
        }
    }

    private void processSpokenText(final String rawText) {
        final FlowMode mode = FlowMode.fromId(prefs.getFlowMode());
        final String model = prefs.getSelectedModel();
        final String apiKey = prefs.getApiKey();

        statusTextView.setText("✨ AI Polishing (" + model + ")...");

        GeminiFlowClient.polishSpeech(rawText, apiKey, model, mode, new GeminiFlowClient.PolishCallback() {
            @Override
            public void onSuccess(String polishedText, long latencyMs) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !polishedText.isEmpty()) {
                    ic.commitText(polishedText + " ", 1);
                    triggerHaptic(new long[]{20, 40});
                }
                statusTextView.setText("✓ Injected (" + latencyMs + "ms)");
            }

            @Override
            public void onError(String errorMessage, String fallbackText) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !fallbackText.isEmpty()) {
                    ic.commitText(fallbackText + " ", 1);
                }
                statusTextView.setText("✓ Injected (offline)");
            }
        });
    }

    private void resetSpaceButton() {
        if (spaceMicButton == null) return;
        spaceMicButton.setText("🎙️ Wispr Flow (Tap to Dictate)");

        GradientDrawable spaceBg = new GradientDrawable();
        spaceBg.setShape(GradientDrawable.RECTANGLE);
        spaceBg.setCornerRadius(dpToPx(10));
        spaceBg.setColor(Color.parseColor("#1e1b4b"));
        spaceBg.setStroke(dpToPx(2), Color.parseColor("#6366f1"));
        spaceMicButton.setBackground(spaceBg);
    }

    private void triggerHaptic(long ms) {
        if (prefs != null && prefs.isHapticEnabled() && vibrator != null && vibrator.hasVibrator()) {
            try {
                vibrator.vibrate(ms);
            } catch (Exception ignored) {}
        }
    }

    private void triggerHaptic(long[] pattern) {
        if (prefs != null && prefs.isHapticEnabled() && vibrator != null && vibrator.hasVibrator()) {
            try {
                vibrator.vibrate(pattern, -1);
            } catch (Exception ignored) {}
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
