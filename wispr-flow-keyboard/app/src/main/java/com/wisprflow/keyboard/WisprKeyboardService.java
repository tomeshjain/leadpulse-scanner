package com.wisprflow.keyboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Smart AI Keyboard Professional Extended Android Keyboard (InputMethodService)
 * 
 * Features:
 * 1. Dedicated Top Suggestion Row with Next-Word Prediction, Collocations & Real-Time Spelling Suggestions.
 * 2. Dedicated Action Toolbar Row (🎙️ Voice, 📇 Scan Card, ✨ Mode Selector, 🎙️💬 Meeting, 📋 Clipboard, 🎞️ GIF, 😊 Emoji, ⚡ Shortcuts, 🎨 Themes, ⚙️ Settings).
 * 3. Meeting & Conversation Recording Mode: Manual Start & Stop with continuous dialogue transcription, generating Executive Summary & Pending Action Items.
 * 4. Animated GIF Search & Visual Previews: Loaded with Glide, with on-keyboard typing search, instant voice search, and 1-tap direct sending.
 * 5. Clipboard & Pinned Snippets: Dedicated tabs ([⭐ All], [📌 Pinned], [🕒 Recent]), individual delete, and clear unpinned (pinned preserved).
 * 6. Clean Separation of Text and Numeric/Symbol Keyboards (with dedicated 10-key PIN Numpad).
 * 7. Contact Card Scanner: Extracts business card details using Gemini AI and sends text + image.
 * 8. Dynamic Themes (Clean Light, Midnight Slate, OLED Black, Cyberpunk Neon, Forest Emerald, Sunset Amber).
 */
public class WisprKeyboardService extends InputMethodService {

    private static final String TAG = "WisprKeyboardService";

    // Panel View Modes
    private enum PanelMode {
        KEYBOARD,
        EMOJI,
        GIFS,
        CLIPBOARD,
        SHORTCUTS,
        THEMES,
        CONVERSATION,
        TEXT_EDITING
    }

    private enum ClipboardTab {
        ALL,
        PINNED,
        RECENT
    }
    private ClipboardTab currentClipboardTab = ClipboardTab.ALL;

    // Meeting & Conversation Recording State
    private boolean isConversationRecordingActive = false;
    private final StringBuilder conversationTranscript = new StringBuilder();
    private SpeechRecognizer conversationSpeechRecognizer;
    private long conversationStartTime = 0;
    private TextView tvConversationLiveTranscript;
    private TextView tvConversationStatus;
    private Button btnConversationRecordToggle;
    private final Handler conversationTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable conversationTimerRunnable;

    // GIF Typing & Search State
    private boolean isGifTypingActive = false;

    // Samsung Text Editing Selection State
    private boolean isTextEditingSelecting = false;

    private KeyboardPreferences prefs;
    private ShortcutsManager shortcutsManager;
    private SuggestionEngine suggestionEngine;
    private ClipboardHistoryManager clipboardManager;
    private Vibrator vibrator;
    private android.media.AudioManager audioManager;
    private SpeechRecognizer speechRecognizer;
    private ClipboardManager systemClipboard;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;

    private PanelMode currentPanel = PanelMode.KEYBOARD;
    private boolean isListening = false;
    private boolean isShifted = false;
    private boolean isCapsLocked = false;
    private boolean isSymbolMode = false;
    private boolean isNumpadMode = false;
    private long lastShiftClickTime = 0;
    private long lastSpacePressTime = 0;

    // Typing tracking for Suggestions & Collocations
    private final StringBuilder currentWordBuffer = new StringBuilder();
    private String lastCommittedWord = "";
    private String lastCopiedSnippet = null;

    // UI Components
    private LinearLayout keyboardRoot;
    private LinearLayout toolbarContainer;
    private LinearLayout suggestionsBar;
    private LinearLayout actionToolbarRow;
    private LinearLayout voiceStatusBanner;
    private TextView tvVoiceStatus;

    // One-Handed Mode Container & Docks
    private LinearLayout keyboardBodyContainer;
    private LinearLayout leftDockPanel;
    private LinearLayout rightDockPanel;
    private LinearLayout mainInputContainer;

    private LinearLayout keysContainer;
    private LinearLayout panelContainer;

    private Button btnShiftKey;
    private Button btnEnterKey;
    private Button modeChipButton;

    private final Handler backspaceHandler = new Handler(Looper.getMainLooper());
    private boolean isBackspaceActive = false;
    private final Runnable backspaceRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isBackspaceActive) return;
            handleBackspace();
            if (isBackspaceActive) {
                backspaceHandler.postDelayed(this, 55);
            }
        }
    };

    private void stopBackspaceRepeat() {
        isBackspaceActive = false;
        backspaceHandler.removeCallbacksAndMessages(null);
    }

    private final List<TextView> alphaKeyPrimaryLabels = new ArrayList<>();
    private final List<String> alphaKeyOriginalLetters = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new KeyboardPreferences(this);
        shortcutsManager = new ShortcutsManager(this);
        suggestionEngine = new SuggestionEngine(this, shortcutsManager);
        clipboardManager = new ClipboardHistoryManager(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // System clipboard monitoring
        systemClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (systemClipboard != null) {
            clipListener = () -> {
                captureSystemClipboard();
                updateSuggestions();
            };
            try {
                systemClipboard.addPrimaryClipChangedListener(clipListener);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public View onCreateInputView() {
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);
        keyboardRoot = new LinearLayout(this);
        keyboardRoot.setOrientation(LinearLayout.VERTICAL);
        keyboardRoot.setBackgroundColor(theme.backgroundColor);
        keyboardRoot.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(4));

        // 1. Two-Tier Header: Tier 1 (Suggestions) + Tier 2 (Action Toolbar)
        buildTwoTierToolbar();

        // 2. Horizontal Body Container for One-Handed Mode Support
        keyboardBodyContainer = new LinearLayout(this);
        keyboardBodyContainer.setOrientation(LinearLayout.HORIZONTAL);
        keyboardBodyContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        keyboardRoot.addView(keyboardBodyContainer);

        // Left Dock Panel (shown in Right-Handed Mode)
        leftDockPanel = createDockPanel(true);
        keyboardBodyContainer.addView(leftDockPanel);

        // Main Input Container (Keys & Panels)
        mainInputContainer = new LinearLayout(this);
        mainInputContainer.setOrientation(LinearLayout.VERTICAL);
        mainInputContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        keyboardBodyContainer.addView(mainInputContainer);

        // Right Dock Panel (shown in Left-Handed Mode)
        rightDockPanel = createDockPanel(false);
        keyboardBodyContainer.addView(rightDockPanel);

        // Keys Container
        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        keysContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        mainInputContainer.addView(keysContainer);

        // Panel Container for Emojis, GIFs, Clipboard, Shortcuts, Themes, Text Editing
        panelContainer = new LinearLayout(this);
        panelContainer.setOrientation(LinearLayout.VERTICAL);
        panelContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(260)));
        panelContainer.setVisibility(View.GONE);
        mainInputContainer.addView(panelContainer);

        applyOneHandedMode();

        // Render Keyboard
        showPanel(PanelMode.KEYBOARD);

        // Initialize Speech Recognizer
        initSpeechRecognizer();

        return keyboardRoot;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        currentWordBuffer.setLength(0);
        lastCommittedWord = "";
        isShifted = false;
        isCapsLocked = false;
        isSymbolMode = false;
        isNumpadMode = false;

        // Check if a contact card was scanned by CardScannerActivity and ready for insertion
        checkPendingContactCard();

        applyCurrentTheme();
        applyOneHandedMode();
        showPanel(PanelMode.KEYBOARD);
        captureSystemClipboard();
        updateEnterKeyAction(info);
        evaluateAutoCaps();
        updateSuggestions();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        checkPendingContactCard();
    }

    private void checkPendingContactCard() {
        if (ContactCardTransfer.hasPendingCard()) {
            ContactCardTransfer.CardPayload payload = ContactCardTransfer.consumePendingCard();
            if (payload != null) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(payload.text + "\n", 1);
                    if (payload.imageUri != null) {
                        sendImageMedia(payload.imageUri, payload.mimeType);
                    } else {
                        Toast.makeText(this, "📇 Contact Card text inserted!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        stopBackspaceRepeat();
        if (isListening) {
            stopVoiceDictation();
        }
        if (suggestionEngine != null) {
            suggestionEngine.saveLearnedPatterns();
        }
    }

    // --- TWO-TIER TOOLBAR DESIGN (ROW 1: SUGGESTIONS | ROW 2: ACTIONS) ---

    private void buildTwoTierToolbar() {
        toolbarContainer = new LinearLayout(this);
        toolbarContainer.setOrientation(LinearLayout.VERTICAL);
        toolbarContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        toolbarContainer.setBackgroundColor(Color.parseColor("#0c121e"));
        toolbarContainer.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2));

        // TIER 1: Dedicated Full-Width Suggestion Strip
        suggestionsBar = new LinearLayout(this);
        suggestionsBar.setOrientation(LinearLayout.HORIZONTAL);
        suggestionsBar.setGravity(Gravity.CENTER_VERTICAL);
        suggestionsBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(38)));
        toolbarContainer.addView(suggestionsBar);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)));
        divider.setBackgroundColor(Color.parseColor("#1a2638"));
        toolbarContainer.addView(divider);

        // TIER 2: Dedicated Quick Action Toolbar Row
        actionToolbarRow = new LinearLayout(this);
        actionToolbarRow.setOrientation(LinearLayout.HORIZONTAL);
        actionToolbarRow.setGravity(Gravity.CENTER_VERTICAL);
        actionToolbarRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(38)));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        hsv.addView(iconRow);

        // 🎙️ Voice Dictation Button (Compact Icon Pill)
        Button btnVoice = new Button(this);
        btnVoice.setText("🎙️ Voice");
        btnVoice.setTextSize(11);
        btnVoice.setTextColor(Color.WHITE);
        btnVoice.setAllCaps(false);
        btnVoice.setBackground(createChipDrawable("#4338ca", "#6366f1", 12));
        btnVoice.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        voiceParams.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btnVoice.setLayoutParams(voiceParams);
        btnVoice.setOnClickListener(v -> toggleVoiceDictation());
        iconRow.addView(btnVoice);

        // 📇 Contact Card Scanner Button (Compact Icon Pill)
        Button btnScanCard = new Button(this);
        btnScanCard.setText("📇 Scan");
        btnScanCard.setTextSize(11);
        btnScanCard.setTextColor(Color.WHITE);
        btnScanCard.setAllCaps(false);
        btnScanCard.setBackground(createChipDrawable("#065f46", "#10b981", 12));
        btnScanCard.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        scanParams.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btnScanCard.setLayoutParams(scanParams);
        btnScanCard.setOnClickListener(v -> openCardScanner());
        iconRow.addView(btnScanCard);

        // ✨ AI Flow Mode Chip (Placed IMMEDIATELY AFTER Voice & Scan Card!)
        modeChipButton = new Button(this);
        updateModeChipText();
        modeChipButton.setTextSize(10);
        modeChipButton.setTextColor(Color.parseColor("#c7d2fe"));
        modeChipButton.setAllCaps(false);
        modeChipButton.setBackground(createChipDrawable("#1e1b4b", "#6366f1", 12));
        modeChipButton.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        chipParams.setMargins(dpToPx(2), 0, dpToPx(4), 0);
        modeChipButton.setLayoutParams(chipParams);
        modeChipButton.setOnClickListener(v -> cycleNextMode());
        iconRow.addView(modeChipButton);

        // 🎙️💬 Dedicated Meeting & Conversation Recording Mode Button
        Button btnMeeting = new Button(this);
        btnMeeting.setText("🎙️💬 Meeting");
        btnMeeting.setTextSize(10);
        btnMeeting.setTextColor(Color.parseColor("#fef08a"));
        btnMeeting.setAllCaps(false);
        btnMeeting.setBackground(createChipDrawable("#451a03", "#d97706", 12));
        btnMeeting.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        LinearLayout.LayoutParams meetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        meetParams.setMargins(dpToPx(2), 0, dpToPx(4), 0);
        btnMeeting.setLayoutParams(meetParams);
        btnMeeting.setOnClickListener(v -> togglePanel(PanelMode.CONVERSATION));
        iconRow.addView(btnMeeting);

        // ✂️ Samsung Text Editing & Selection Mode Toggle
        Button btnTextEdit = createToolbarIconButton("✂️", Color.TRANSPARENT);
        btnTextEdit.setOnClickListener(v -> togglePanel(PanelMode.TEXT_EDITING));
        iconRow.addView(btnTextEdit);

        // 📋 Clipboard & Pinned Favorites Toggle
        Button btnClip = createToolbarIconButton("📋", Color.TRANSPARENT);
        btnClip.setOnClickListener(v -> togglePanel(PanelMode.CLIPBOARD));
        iconRow.addView(btnClip);

        // 🔢 Number Row Quick Toggle
        Button btnNumRow = createToolbarIconButton("🔢", Color.TRANSPARENT);
        btnNumRow.setOnClickListener(v -> {
            boolean current = prefs != null && prefs.isShowNumberRow();
            if (prefs != null) prefs.setShowNumberRow(!current);
            renderKeyRows();
            triggerHaptic(15);
            Toast.makeText(this, !current ? "Number Row Shown" : "Number Row Hidden", Toast.LENGTH_SHORT).show();
        });
        iconRow.addView(btnNumRow);

        // 📱 One-Handed Mode Quick Toggle
        Button btnOneHand = createToolbarIconButton("📱", Color.TRANSPARENT);
        btnOneHand.setOnClickListener(v -> cycleOneHandedMode());
        iconRow.addView(btnOneHand);

        // 🎞️ GIFs & Reactions Toggle
        Button btnGifs = createToolbarIconButton("🎞️", Color.TRANSPARENT);
        btnGifs.setOnClickListener(v -> togglePanel(PanelMode.GIFS));
        iconRow.addView(btnGifs);

        // 😊 Emojis Toggle
        Button btnEmoji = createToolbarIconButton("😊", Color.TRANSPARENT);
        btnEmoji.setOnClickListener(v -> togglePanel(PanelMode.EMOJI));
        iconRow.addView(btnEmoji);

        // ⚡ Shortcuts Toggle (GM, GE, etc.)
        Button btnShortcuts = createToolbarIconButton("⚡", Color.TRANSPARENT);
        btnShortcuts.setOnClickListener(v -> togglePanel(PanelMode.SHORTCUTS));
        iconRow.addView(btnShortcuts);

        // 🎨 Themes Picker Button
        Button btnThemes = createToolbarIconButton("🎨", Color.TRANSPARENT);
        btnThemes.setOnClickListener(v -> togglePanel(PanelMode.THEMES));
        iconRow.addView(btnThemes);

        // ⚙️ Settings Button
        Button btnSettings = createToolbarIconButton("⚙️", Color.TRANSPARENT);
        btnSettings.setOnClickListener(v -> openSettingsActivity());
        iconRow.addView(btnSettings);

        actionToolbarRow.addView(hsv);
        toolbarContainer.addView(actionToolbarRow);

        // Voice Listening Banner (shown while dictating)
        voiceStatusBanner = new LinearLayout(this);
        voiceStatusBanner.setOrientation(LinearLayout.HORIZONTAL);
        voiceStatusBanner.setGravity(Gravity.CENTER_VERTICAL);
        voiceStatusBanner.setBackgroundColor(Color.parseColor("#1f1435"));
        voiceStatusBanner.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        voiceStatusBanner.setVisibility(View.GONE);

        tvVoiceStatus = new TextView(this);
        tvVoiceStatus.setText("🎙️ Listening... Speak naturally");
        tvVoiceStatus.setTextColor(Color.parseColor("#e0e7ff"));
        tvVoiceStatus.setTextSize(12);
        LinearLayout.LayoutParams tvVoiceParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvVoiceStatus.setLayoutParams(tvVoiceParams);
        voiceStatusBanner.addView(tvVoiceStatus);

        Button btnVoiceCancel = new Button(this);
        btnVoiceCancel.setText("Done / Stop");
        btnVoiceCancel.setTextSize(11);
        btnVoiceCancel.setTextColor(Color.WHITE);
        btnVoiceCancel.setBackground(createChipDrawable("#dc2626", "#ef4444", 8));
        btnVoiceCancel.setOnClickListener(v -> stopVoiceDictation());
        voiceStatusBanner.addView(btnVoiceCancel);

        toolbarContainer.addView(voiceStatusBanner);
        keyboardRoot.addView(toolbarContainer);
    }

    private Button createToolbarIconButton(String icon, int bgColor) {
        Button btn = new Button(this);
        btn.setText(icon);
        btn.setTextSize(14);
        btn.setTextColor(Color.WHITE);
        btn.setPadding(0, 0, 0, 0);
        btn.setGravity(Gravity.CENTER);

        if (bgColor == Color.TRANSPARENT) {
            btn.setBackgroundColor(Color.TRANSPARENT);
        } else {
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(bgColor);
            btn.setBackground(gd);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(34));
        params.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    // --- SMART SUGGESTIONS & COLLOCATIONS ---

    private static class CursorWordContext {
        final String currentPrefix;
        final String previousWord;

        CursorWordContext(String currentPrefix, String previousWord) {
            this.currentPrefix = currentPrefix != null ? currentPrefix : "";
            this.previousWord = previousWord != null ? previousWord : "";
        }
    }

    private CursorWordContext getCursorWordContext() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return new CursorWordContext(currentWordBuffer.toString().trim(), lastCommittedWord);
        }

        CharSequence before = ic.getTextBeforeCursor(60, 0);
        if (before == null || before.length() == 0) {
            currentWordBuffer.setLength(0);
            return new CursorWordContext("", "");
        }

        int len = before.length();
        char lastChar = before.charAt(len - 1);

        // If cursor is immediately after whitespace or delimiter, user is between words
        if (Character.isWhitespace(lastChar) || lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == ',') {
            currentWordBuffer.setLength(0);
            int end = len - 1;
            while (end >= 0 && (Character.isWhitespace(before.charAt(end)) || before.charAt(end) == '.' || before.charAt(end) == '?' || before.charAt(end) == '!' || before.charAt(end) == ',')) {
                end--;
            }
            int start = end;
            while (start >= 0 && !Character.isWhitespace(before.charAt(start)) && before.charAt(start) != '.' && before.charAt(start) != '?' && before.charAt(start) != '!') {
                start--;
            }
            String prev = (start < end) ? before.subSequence(start + 1, end + 1).toString().trim() : "";
            lastCommittedWord = prev;
            return new CursorWordContext("", prev);
        }

        // User is currently typing or backspacing within an active word token
        int start = len - 1;
        while (start >= 0 && !Character.isWhitespace(before.charAt(start)) && before.charAt(start) != '\n') {
            start--;
        }
        String activeToken = before.subSequence(start + 1, len).toString().trim();

        int prevEnd = start;
        while (prevEnd >= 0 && Character.isWhitespace(before.charAt(prevEnd))) {
            prevEnd--;
        }
        int prevStart = prevEnd;
        while (prevStart >= 0 && !Character.isWhitespace(before.charAt(prevStart))) {
            prevStart--;
        }
        String prev = (prevStart < prevEnd) ? before.subSequence(prevStart + 1, prevEnd + 1).toString().trim() : "";

        currentWordBuffer.setLength(0);
        currentWordBuffer.append(activeToken);
        lastCommittedWord = prev;

        return new CursorWordContext(activeToken, prev);
    }

    private void updateSuggestions() {
        if (suggestionsBar == null) return;
        suggestionsBar.removeAllViews();
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        CursorWordContext ctx = getCursorWordContext();
        String currentWord = ctx.currentPrefix;
        String prevWord = ctx.previousWord;

        // 1. Quick Clipboard Paste Chip (if user has copied text)
        if (lastCopiedSnippet != null && !lastCopiedSnippet.trim().isEmpty()) {
            final String clipToPaste = lastCopiedSnippet.trim();
            String displayClip = clipToPaste.length() > 14 ? clipToPaste.substring(0, 14) + "…" : clipToPaste;

            Button clipBtn = new Button(this);
            clipBtn.setText("📋 " + displayClip);
            clipBtn.setTextColor(Color.parseColor("#fbbf24"));
            clipBtn.setTextSize(11);
            clipBtn.setAllCaps(false);
            clipBtn.setSingleLine(true);
            clipBtn.setBackground(createChipDrawable("#241c08", "#d97706", 8));
            clipBtn.setPadding(dpToPx(8), 0, dpToPx(8), 0);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32));
            cp.setMargins(dpToPx(2), 0, dpToPx(4), 0);
            clipBtn.setLayoutParams(cp);

            clipBtn.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(clipToPaste + " ", 1);
                    triggerHaptic(15);
                    playKeyClick();
                    lastCopiedSnippet = null;
                    evaluateAutoCaps();
                    updateSuggestions();
                }
            });
            suggestionsBar.addView(clipBtn);
        }

        // 2. Contextual Inline Emoji Suggestion Chip
        final String contextualEmoji = SuggestionEngine.getEmojiForWord(currentWord.isEmpty() ? prevWord : currentWord);
        if (contextualEmoji != null) {
            Button emojiChip = new Button(this);
            emojiChip.setText(contextualEmoji);
            emojiChip.setTextSize(16);
            emojiChip.setPadding(0, 0, 0, 0);
            emojiChip.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(32));
            ep.setMargins(dpToPx(2), 0, dpToPx(4), 0);
            emojiChip.setLayoutParams(ep);
            emojiChip.setOnClickListener(v -> {
                insertText(contextualEmoji + " ");
                triggerHaptic(12);
            });
            suggestionsBar.addView(emojiChip);
        }

        // 3. Contextual GIF Suggestion Chip (based on current/last typed word)
        final GifReactionsData.GifItem contextualGif = GifReactionsData.findContextualGif(prevWord, currentWord);
        if (contextualGif != null) {
            Button gifChip = new Button(this);
            gifChip.setText(contextualGif.emojiPreview + " GIF: " + contextualGif.title);
            gifChip.setTextColor(Color.parseColor("#f472b6"));
            gifChip.setTextSize(11);
            gifChip.setAllCaps(false);
            gifChip.setSingleLine(true);
            gifChip.setBackground(createChipDrawable("#2e1065", "#7c3aed", 8));
            gifChip.setPadding(dpToPx(8), 0, dpToPx(8), 0);

            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32));
            gp.setMargins(dpToPx(2), 0, dpToPx(4), 0);
            gifChip.setLayoutParams(gp);

            gifChip.setOnClickListener(v -> sendGifMedia(contextualGif));
            suggestionsBar.addView(gifChip);
        }

        // 4. Next-Word Prediction / Typing Pattern Suggestions (NEVER wiped out on backspace!)
        List<String> suggestions = suggestionEngine.getSuggestions(prevWord, currentWord, 4);

        for (int i = 0; i < suggestions.size(); i++) {
            final String suggestion = suggestions.get(i);
            Button sugBtn = new Button(this);
            sugBtn.setText(suggestion);
            sugBtn.setAllCaps(false);
            sugBtn.setSingleLine(true);
            sugBtn.setGravity(Gravity.CENTER);

            // Highlight primary / autocorrect suggestion (Center / Index 0 when typing)
            if (i == 0 && !currentWord.isEmpty()) {
                sugBtn.setTextColor(Color.WHITE);
                sugBtn.setTextSize(13);
                sugBtn.setTypeface(Typeface.DEFAULT_BOLD);
                sugBtn.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
            } else {
                sugBtn.setTextColor(theme.textColor);
                sugBtn.setTextSize(12);
                sugBtn.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
            }

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dpToPx(32), 1.0f);
            p.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            sugBtn.setLayoutParams(p);

            sugBtn.setOnClickListener(v -> applySuggestion(suggestion));
            suggestionsBar.addView(sugBtn);
        }
    }

    private void applySuggestion(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CursorWordContext ctx = getCursorWordContext();
        int len = ctx.currentPrefix.length();
        if (len > 0) {
            ic.deleteSurroundingText(len, 0);
        }

        ic.commitText(word + " ", 1);

        suggestionEngine.recordWord(word);
        if (!ctx.previousWord.isEmpty()) {
            suggestionEngine.recordWordPair(ctx.previousWord, word);
        }
        lastCommittedWord = word;
        currentWordBuffer.setLength(0);

        triggerHaptic(12);
        playKeyClick();
        evaluateAutoCaps();
    }

    // --- PANEL NAVIGATION ---

    private void togglePanel(PanelMode mode) {
        if (currentPanel == mode) {
            showPanel(PanelMode.KEYBOARD);
        } else {
            showPanel(mode);
        }
    }

    private void showPanel(PanelMode mode) {
        currentPanel = mode;
        if (mode == PanelMode.KEYBOARD) {
            panelContainer.setVisibility(View.GONE);
            keysContainer.setVisibility(View.VISIBLE);
            renderKeyRows();
        } else {
            keysContainer.setVisibility(View.GONE);
            panelContainer.setVisibility(View.VISIBLE);
            panelContainer.removeAllViews();

            switch (mode) {
                case EMOJI:
                    buildEmojiPanel();
                    break;
                case GIFS:
                    String prefill = "";
                    if (currentWordBuffer.length() > 0) {
                        prefill = currentWordBuffer.toString().trim();
                    } else if (lastCommittedWord != null && !lastCommittedWord.isEmpty()) {
                        prefill = lastCommittedWord.trim();
                    }
                    buildGifsPanel(prefill);
                    break;
                case CLIPBOARD:
                    buildClipboardPanel();
                    break;
                case SHORTCUTS:
                    buildShortcutsPanel();
                    break;
                case THEMES:
                    buildThemesPanel();
                    break;
                case CONVERSATION:
                    buildConversationPanel();
                    break;
                case TEXT_EDITING:
                    buildTextEditingPanel();
                    break;
            }
        }
        triggerHaptic(18);
    }

    // --- EMOJI PANEL ---

    private void buildEmojiPanel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(tabs);
        layout.addView(tabScroll);

        ScrollView emojiScroll = new ScrollView(this);
        emojiScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final LinearLayout emojiContent = new LinearLayout(this);
        emojiContent.setOrientation(LinearLayout.VERTICAL);
        emojiContent.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        emojiScroll.addView(emojiContent);
        layout.addView(emojiScroll);

        final String[] categories = EmojiData.getCategoryNames();
        for (int i = 0; i < categories.length; i++) {
            final String cat = categories[i];
            Button catBtn = new Button(this);
            catBtn.setText(cat);
            catBtn.setTextSize(11);
            catBtn.setTextColor(i == 0 ? Color.parseColor("#a5b4fc") : Color.parseColor("#94a3b8"));
            catBtn.setBackgroundColor(Color.TRANSPARENT);
            catBtn.setAllCaps(false);
            catBtn.setOnClickListener(v -> {
                renderEmojis(emojiContent, cat);
                for (int j = 0; j < tabs.getChildCount(); j++) {
                    View child = tabs.getChildAt(j);
                    if (child instanceof Button) {
                        ((Button) child).setTextColor(child == v ? Color.parseColor("#a5b4fc") : Color.parseColor("#94a3b8"));
                    }
                }
            });
            tabs.addView(catBtn);
        }

        renderEmojis(emojiContent, categories[0]);
        layout.addView(createPanelBottomBar());
        panelContainer.addView(layout);
    }

    private void renderEmojis(LinearLayout container, String category) {
        container.removeAllViews();
        String[] emojis = EmojiData.getEmojisForCategory(category);

        int cols = 7;
        LinearLayout row = null;
        for (int i = 0; i < emojis.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40)));
                container.addView(row);
            }

            final String em = emojis[i];
            Button btn = new Button(this);
            btn.setText(em);
            btn.setTextSize(20);
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
            btn.setLayoutParams(p);
            btn.setOnClickListener(v -> {
                insertText(em);
                triggerHaptic(12);
            });
            if (row != null) row.addView(btn);
        }
    }

    // --- GIF & REACTION PANEL ---

    private String currentGifSearchQuery = "";
    private boolean isKeyboardShortcutsExpanded = false;

    private void buildGifsPanel() {
        buildGifsPanel(currentGifSearchQuery);
    }

    private void buildGifsPanel(String initialQuery) {
        panelContainer.removeAllViews();
        currentGifSearchQuery = (initialQuery != null) ? initialQuery.trim() : "";
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(theme.backgroundColor);

        // 1. Sleek Unified Search Bar Header
        LinearLayout searchHeader = new LinearLayout(this);
        searchHeader.setOrientation(LinearLayout.HORIZONTAL);
        searchHeader.setGravity(Gravity.CENTER_VERTICAL);
        searchHeader.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));

        // Unified Search Pill Container
        LinearLayout searchPill = new LinearLayout(this);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 18));
        searchPill.setPadding(dpToPx(12), 0, dpToPx(6), 0);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f);
        searchPill.setLayoutParams(pillParams);

        TextView tvSearchIcon = new TextView(this);
        tvSearchIcon.setText("🔍 ");
        tvSearchIcon.setTextSize(12);
        tvSearchIcon.setTextColor(theme.subTextColor);
        searchPill.addView(tvSearchIcon);

        final TextView tvSearchBox = new TextView(this);
        if (currentGifSearchQuery.isEmpty()) {
            tvSearchBox.setText("Search GIFs & reactions...");
            tvSearchBox.setTextColor(theme.subTextColor);
        } else {
            tvSearchBox.setText(currentGifSearchQuery);
            tvSearchBox.setTextColor(theme.textColor);
            tvSearchBox.setTypeface(null, Typeface.BOLD);
        }
        tvSearchBox.setTextSize(12);
        tvSearchBox.setSingleLine(true);
        tvSearchBox.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvSearchBox.setLayoutParams(searchParams);
        searchPill.addView(tvSearchBox);

        // Clear '✕' inside search pill
        if (!currentGifSearchQuery.isEmpty()) {
            Button btnClear = new Button(this);
            btnClear.setText("✕");
            btnClear.setTextSize(11);
            btnClear.setTextColor(theme.subTextColor);
            btnClear.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dpToPx(30), dpToPx(32));
            btnClear.setLayoutParams(clearParams);
            btnClear.setOnClickListener(v -> {
                currentGifSearchQuery = "";
                buildGifsPanel("");
            });
            searchPill.addView(btnClear);
        }

        // Voice Search Mic button inside search pill
        Button btnVoiceSearch = new Button(this);
        btnVoiceSearch.setText("🎙️");
        btnVoiceSearch.setTextSize(13);
        btnVoiceSearch.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams vsParams = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        btnVoiceSearch.setLayoutParams(vsParams);
        btnVoiceSearch.setOnClickListener(v -> startGifVoiceSearch());
        searchPill.addView(btnVoiceSearch);

        searchPill.setOnClickListener(v -> {
            isGifTypingActive = !isGifTypingActive;
            buildGifsPanel(currentGifSearchQuery);
        });
        searchHeader.addView(searchPill);

        // Toggle Type / Grid button on the right
        Button btnToggleType = new Button(this);
        btnToggleType.setText(isGifTypingActive ? "🖼️ Grid" : "⌨️ Type");
        btnToggleType.setTextSize(11);
        btnToggleType.setTextColor(isGifTypingActive ? theme.accentColor : Color.WHITE);
        btnToggleType.setAllCaps(false);
        btnToggleType.setBackground(createChipDrawable(isGifTypingActive ? theme.actionKeyColor : theme.accentColor, theme.dividerColor, 18));
        LinearLayout.LayoutParams ttParams = new LinearLayout.LayoutParams(dpToPx(66), dpToPx(36));
        ttParams.leftMargin = dpToPx(6);
        btnToggleType.setLayoutParams(ttParams);
        btnToggleType.setOnClickListener(v -> {
            isGifTypingActive = !isGifTypingActive;
            buildGifsPanel(currentGifSearchQuery);
        });
        searchHeader.addView(btnToggleType);

        layout.addView(searchHeader);

        // 2. Horizontal Category Filter Pills
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(4));
        tabScroll.addView(tabs);
        layout.addView(tabScroll);

        final String[] categories = GifReactionsData.getCategoryNames();
        for (int i = 0; i < categories.length; i++) {
            final String cat = categories[i];
            Button catBtn = new Button(this);
            catBtn.setText(cat);
            catBtn.setTextSize(11);
            boolean isSelected = cat.equalsIgnoreCase(currentGifSearchQuery) || (i == 0 && currentGifSearchQuery.isEmpty());
            catBtn.setTextColor(isSelected ? Color.WHITE : theme.subTextColor);
            catBtn.setBackground(createChipDrawable(isSelected ? theme.accentColor : theme.keyColor, theme.dividerColor, 14));
            catBtn.setPadding(dpToPx(12), 0, dpToPx(12), 0);
            catBtn.setAllCaps(false);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28));
            cp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            catBtn.setLayoutParams(cp);

            catBtn.setOnClickListener(v -> {
                isGifTypingActive = false;
                currentGifSearchQuery = cat;
                buildGifsPanel(cat);
            });
            tabs.addView(catBtn);
        }

        // 3. Scrollable Results Container
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final LinearLayout gifContent = new LinearLayout(this);
        gifContent.setOrientation(LinearLayout.VERTICAL);
        gifContent.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(4));
        scroll.addView(gifContent);
        layout.addView(scroll);

        // Populate GIFs based on query or default category
        List<GifReactionsData.GifItem> items;
        if (!currentGifSearchQuery.isEmpty()) {
            items = GifReactionsData.searchLocalGifs(currentGifSearchQuery);
        } else {
            items = GifReactionsData.getItemsForCategory(categories[0]);
        }
        displayGifGrid(gifContent, items, theme);

        // If online search has results, query in background
        if (!currentGifSearchQuery.isEmpty()) {
            final String q = currentGifSearchQuery;
            GifReactionsData.searchOnlineGifs(q, onlineResults -> {
                if (onlineResults != null && !onlineResults.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (currentGifSearchQuery.equalsIgnoreCase(q)) {
                            displayGifGrid(gifContent, onlineResults, theme);
                        }
                    });
                }
            });
        }

        // 4. In-Keyboard Typing Keys (when Type mode is active)
        if (isGifTypingActive) {
            layout.addView(createGifTypingKeyboardRow(theme));
        }

        layout.addView(createPanelBottomBar());
        panelContainer.addView(layout);
    }

    private void displayGifGrid(LinearLayout container, List<GifReactionsData.GifItem> items, KeyboardTheme theme) {
        container.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No GIFs found for \"" + currentGifSearchQuery + "\".\nTry another search keyword or tap a category pill!");
            empty.setTextColor(theme.subTextColor);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dpToPx(30), 0, 0);
            container.addView(empty);
            return;
        }

        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            for (int col = 0; col < 2; col++) {
                int idx = i + col;
                if (idx < items.size()) {
                    final GifReactionsData.GifItem item = items.get(idx);
                    LinearLayout card = new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 10));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    cp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                    card.setLayoutParams(cp);
                    card.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

                    ImageView ivGif = new ImageView(this);
                    ivGif.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(95));
                    ivGif.setLayoutParams(ivParams);

                    try {
                        Glide.with(WisprKeyboardService.this)
                                .asGif()
                                .load(item.gifUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .placeholder(android.R.drawable.progress_indeterminate_horizontal)
                                .error(android.R.drawable.ic_menu_gallery)
                                .into(ivGif);
                    } catch (Throwable ignored) {}

                    card.addView(ivGif);

                    TextView tvTitle = new TextView(this);
                    tvTitle.setText(item.emojiPreview + " " + item.title);
                    tvTitle.setTextColor(theme.textColor);
                    tvTitle.setTextSize(11);
                    tvTitle.setMaxLines(1);
                    tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    tvTitle.setGravity(Gravity.CENTER);
                    tvTitle.setPadding(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(2));
                    card.addView(tvTitle);

                    card.setOnClickListener(v -> sendGifMedia(item));
                    row.addView(card);
                } else {
                    View spacer = new View(this);
                    spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
                    row.addView(spacer);
                }
            }
            container.addView(row);
        }
    }

    private View createGifTypingKeyboardRow(final KeyboardTheme theme) {
        LinearLayout kb = new LinearLayout(this);
        kb.setOrientation(LinearLayout.VERTICAL);
        kb.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
        kb.setBackgroundColor(theme.backgroundColor);

        String[] row1 = {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"};
        String[] row2 = {"A", "S", "D", "F", "G", "H", "J", "K", "L"};
        String[] row3 = {"Z", "X", "C", "V", "B", "N", "M"};

        kb.addView(createMiniKeyRow(row1, theme));
        kb.addView(createMiniKeyRow(row2, theme));

        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(32)));

        for (final String k : row3) {
            Button b = createMiniKeyButton(k, theme);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
            p.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
            b.setLayoutParams(p);
            b.setOnClickListener(v -> appendGifSearchChar(k.toLowerCase(Locale.ROOT)));
            r3.addView(b);
        }

        // Backspace button
        Button btnBack = new Button(this);
        btnBack.setText("⌫");
        btnBack.setTextSize(13);
        btnBack.setTextColor(theme.textColor);
        btnBack.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 6));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f);
        bp.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        btnBack.setLayoutParams(bp);
        btnBack.setOnClickListener(v -> deleteGifSearchChar());
        r3.addView(btnBack);

        // Space button
        Button btnSpace = new Button(this);
        btnSpace.setText("Space");
        btnSpace.setTextSize(11);
        btnSpace.setAllCaps(false);
        btnSpace.setTextColor(theme.subTextColor);
        btnSpace.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 6));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.8f);
        sp.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        btnSpace.setLayoutParams(sp);
        btnSpace.setOnClickListener(v -> appendGifSearchChar(" "));
        r3.addView(btnSpace);

        // Done button
        Button btnDone = new Button(this);
        btnDone.setText("✓ Done");
        btnDone.setTextSize(11);
        btnDone.setAllCaps(false);
        btnDone.setTextColor(Color.WHITE);
        btnDone.setBackground(createChipDrawable(theme.accentColor, theme.accentColor, 6));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f);
        dp.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
        btnDone.setLayoutParams(dp);
        btnDone.setOnClickListener(v -> {
            isGifTypingActive = false;
            buildGifsPanel(currentGifSearchQuery);
        });
        r3.addView(btnDone);

        kb.addView(r3);
        return kb;
    }

    private LinearLayout createMiniKeyRow(String[] keys, KeyboardTheme theme) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(32)));

        for (final String k : keys) {
            Button b = createMiniKeyButton(k, theme);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
            p.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
            b.setLayoutParams(p);
            b.setOnClickListener(v -> appendGifSearchChar(k.toLowerCase(Locale.ROOT)));
            row.addView(b);
        }
        return row;
    }

    private Button createMiniKeyButton(String text, KeyboardTheme theme) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(theme.textColor);
        b.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 6));
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void appendGifSearchChar(String ch) {
        currentGifSearchQuery += ch;
        triggerHaptic(10);
        buildGifsPanel(currentGifSearchQuery);
    }

    private void deleteGifSearchChar() {
        if (!currentGifSearchQuery.isEmpty()) {
            currentGifSearchQuery = currentGifSearchQuery.substring(0, currentGifSearchQuery.length() - 1);
            triggerHaptic(12);
            buildGifsPanel(currentGifSearchQuery);
        }
    }

    private void startGifVoiceSearch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission needed to voice-search GIFs", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "🎙️ Listening... Speak GIF search query", Toast.LENGTH_SHORT).show();

        try {
            SpeechRecognizer sr = SpeechRecognizer.createSpeechRecognizer(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            sr.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        String query = matches.get(0);
                        currentGifSearchQuery = query;
                        isGifTypingActive = false;
                        buildGifsPanel(query);
                    }
                    try { sr.destroy(); } catch (Exception ignored) {}
                }
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    try { sr.destroy(); } catch (Exception ignored) {}
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            sr.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start voice search: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // --- CLIPBOARD & PINNED FAVORITES PANEL ---

    private void buildClipboardPanel() {
        panelContainer.removeAllViews();
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(theme.backgroundColor);

        // 1. Header Row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📋 Clipboard History & Pinned Snippets");
        tvTitle.setTextColor(theme.accentColor);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(tp);
        header.addView(tvTitle);

        Button btnClear = new Button(this);
        btnClear.setText("Clear History");
        btnClear.setTextSize(11);
        btnClear.setTextColor(Color.parseColor("#ef4444"));
        btnClear.setBackgroundColor(Color.TRANSPARENT);
        btnClear.setAllCaps(false);
        btnClear.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        btnClear.setOnClickListener(v -> {
            clipboardManager.clearUnpinned();
            triggerHaptic(18);
            buildClipboardPanel();
            Toast.makeText(this, "Cleared recent history. Pinned messages preserved! 📌", Toast.LENGTH_SHORT).show();
        });
        header.addView(btnClear);
        layout.addView(header);

        // 2. Segmented Filter Tabs: [⭐ All (N)] | [📌 Pinned (N)] | [🕒 Recent (N)]
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(6));

        final int pinnedCount = clipboardManager.getPinnedCount();
        final int recentCount = clipboardManager.getRecentCount();
        final int allCount = pinnedCount + recentCount;

        tabRow.addView(createClipboardTabButton("⭐ All (" + allCount + ")", ClipboardTab.ALL, theme));
        tabRow.addView(createClipboardTabButton("📌 Pinned (" + pinnedCount + ")", ClipboardTab.PINNED, theme));
        tabRow.addView(createClipboardTabButton("🕒 Recent (" + recentCount + ")", ClipboardTab.RECENT, theme));
        layout.addView(tabRow);

        // 3. Scrollable Clips List
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        scroll.addView(content);
        layout.addView(scroll);

        List<ClipboardHistoryManager.ClipItem> clips;
        if (currentClipboardTab == ClipboardTab.PINNED) {
            clips = clipboardManager.getPinnedClips();
        } else if (currentClipboardTab == ClipboardTab.RECENT) {
            clips = clipboardManager.getRecentClips();
        } else {
            clips = clipboardManager.getAllClips();
        }

        if (clips.isEmpty()) {
            TextView empty = new TextView(this);
            if (currentClipboardTab == ClipboardTab.PINNED) {
                empty.setText("📌 No pinned messages yet.\nTap '☆ Pin' on any message to pin it forever!");
            } else if (currentClipboardTab == ClipboardTab.RECENT) {
                empty.setText("🕒 Recent clipboard history is clear.\nCopy any text to store it here!");
            } else {
                empty.setText("Clipboard is empty.\nCopy any text to store it here!");
            }
            empty.setTextColor(theme.subTextColor);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dpToPx(35), 0, 0);
            content.addView(empty);
        } else {
            for (final ClipboardHistoryManager.ClipItem clip : clips) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                int cardBg = clip.isPinned ? (theme.isLight ? Color.parseColor("#e0e7ff") : Color.parseColor("#1e1b4b")) : theme.keyColor;
                int cardStroke = clip.isPinned ? theme.accentColor : theme.dividerColor;
                card.setBackground(createChipDrawable(cardBg, cardStroke, 10));
                card.setPadding(dpToPx(12), dpToPx(8), dpToPx(8), dpToPx(8));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.bottomMargin = dpToPx(6);
                card.setLayoutParams(cp);

                if (clip.isPinned) {
                    TextView pinBadge = new TextView(this);
                    pinBadge.setText("📌 ");
                    pinBadge.setTextSize(12);
                    card.addView(pinBadge);
                }

                TextView tv = new TextView(this);
                tv.setText(clip.text);
                tv.setTextColor(theme.textColor);
                tv.setTextSize(13);
                tv.setMaxLines(3);
                LinearLayout.LayoutParams txtParam = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                tv.setLayoutParams(txtParam);
                card.addView(tv);

                // Pin / Unpin Button
                Button btnPin = new Button(this);
                btnPin.setText(clip.isPinned ? "📌 Pinned" : "☆ Pin");
                btnPin.setTextSize(11);
                btnPin.setAllCaps(false);
                btnPin.setTextColor(clip.isPinned ? theme.accentColor : theme.subTextColor);
                btnPin.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
                btnPin.setPadding(dpToPx(8), 0, dpToPx(8), 0);
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28));
                pp.leftMargin = dpToPx(4);
                btnPin.setLayoutParams(pp);
                btnPin.setOnClickListener(v -> {
                    clipboardManager.togglePin(clip);
                    triggerHaptic(18);
                    buildClipboardPanel();
                });
                card.addView(btnPin);

                // Delete Button
                Button btnDel = new Button(this);
                btnDel.setText("🗑️");
                btnDel.setTextSize(13);
                btnDel.setBackgroundColor(Color.TRANSPARENT);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dpToPx(34), dpToPx(34));
                dp.leftMargin = dpToPx(4);
                btnDel.setLayoutParams(dp);
                btnDel.setOnClickListener(v -> {
                    clipboardManager.deleteClip(clip);
                    triggerHaptic(18);
                    buildClipboardPanel();
                });
                card.addView(btnDel);

                card.setOnClickListener(v -> {
                    insertText(clip.text + " ");
                    triggerHaptic(18);
                    showPanel(PanelMode.KEYBOARD);
                });

                content.addView(card);
            }
        }

        layout.addView(createPanelBottomBar());
        panelContainer.addView(layout);
    }

    private View createClipboardTabButton(String label, final ClipboardTab tab, KeyboardTheme theme) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(11);
        btn.setAllCaps(false);
        boolean isSelected = (currentClipboardTab == tab);
        btn.setTextColor(isSelected ? theme.accentColor : theme.subTextColor);
        int btnBg = isSelected ? (theme.isLight ? Color.parseColor("#e0e7ff") : Color.parseColor("#1e1b4b")) : theme.actionKeyColor;
        int btnStroke = isSelected ? theme.accentColor : theme.dividerColor;
        btn.setBackground(createChipDrawable(btnBg, btnStroke, 8));
        btn.setPadding(dpToPx(10), 0, dpToPx(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(32), 1.0f);
        lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            currentClipboardTab = tab;
            triggerHaptic(12);
            buildClipboardPanel();
        });
        return btn;
    }

    // --- MEETING & CONVERSATION RECORDING MODE ---

    private void buildConversationPanel() {
        panelContainer.removeAllViews();
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(theme.backgroundColor);

        // 1. Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🎙️💬 Meeting & Conversation Mode");
        tvTitle.setTextColor(Color.parseColor("#f59e0b")); // amber
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(tp);
        header.addView(tvTitle);

        tvConversationStatus = new TextView(this);
        if (isConversationRecordingActive) {
            long elapsedSec = (System.currentTimeMillis() - conversationStartTime) / 1000;
            long mins = elapsedSec / 60;
            long secs = elapsedSec % 60;
            tvConversationStatus.setText(String.format(Locale.US, "🔴 (%02d:%02d)", mins, secs));
            tvConversationStatus.setTextColor(Color.parseColor("#ef4444"));
        } else {
            tvConversationStatus.setText("⚪ Ready");
            tvConversationStatus.setTextColor(theme.subTextColor);
        }
        tvConversationStatus.setTextSize(11);
        tvConversationStatus.setTypeface(null, Typeface.BOLD);
        header.addView(tvConversationStatus);

        Button btnClose = new Button(this);
        btnClose.setText("✕");
        btnClose.setTextSize(14);
        btnClose.setTextColor(theme.subTextColor);
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setOnClickListener(v -> {
            if (isConversationRecordingActive) {
                stopConversationRecording();
            }
            showPanel(PanelMode.KEYBOARD);
        });
        header.addView(btnClose);
        layout.addView(header);

        // 2. Info / Instructions Pill
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Press 'Start Conversation' to record dialogue. Press 'Stop & Summarize' to get an AI Executive Summary & Action Items.");
        tvInfo.setTextColor(theme.subTextColor);
        tvInfo.setTextSize(10);
        tvInfo.setPadding(dpToPx(10), 0, dpToPx(10), dpToPx(4));
        layout.addView(tvInfo);

        // 3. Live Transcript Area
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

        tvConversationLiveTranscript = new TextView(this);
        String transcript = conversationTranscript.toString().trim();
        if (transcript.isEmpty()) {
            tvConversationLiveTranscript.setText("Press '▶️ Start Conversation' when ready.\n\nAs participants speak, the continuous transcript will appear here.\n\nWhen finished, press '⏹️ Stop & Summarize'.");
            tvConversationLiveTranscript.setTextColor(theme.subTextColor);
        } else {
            tvConversationLiveTranscript.setText(transcript);
            tvConversationLiveTranscript.setTextColor(theme.textColor);
        }
        tvConversationLiveTranscript.setTextSize(12);
        tvConversationLiveTranscript.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 10));
        tvConversationLiveTranscript.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        tvConversationLiveTranscript.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(tvConversationLiveTranscript);
        scroll.addView(content);
        layout.addView(scroll);

        // 4. Control Bar (Start / Stop)
        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(6));

        btnConversationRecordToggle = new Button(this);
        if (isConversationRecordingActive) {
            btnConversationRecordToggle.setText("⏹️ Stop & Summarize");
            btnConversationRecordToggle.setBackground(createChipDrawable("#7f1d1d", "#ef4444", 12));
            btnConversationRecordToggle.setTextColor(Color.WHITE);
        } else {
            btnConversationRecordToggle.setText("▶️ Start Conversation");
            btnConversationRecordToggle.setBackground(createChipDrawable("#064e3b", "#10b981", 12));
            btnConversationRecordToggle.setTextColor(Color.WHITE);
        }
        btnConversationRecordToggle.setTextSize(12);
        btnConversationRecordToggle.setTypeface(null, Typeface.BOLD);
        btnConversationRecordToggle.setAllCaps(false);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f);
        btnConversationRecordToggle.setLayoutParams(btnParams);
        btnConversationRecordToggle.setOnClickListener(v -> {
            if (isConversationRecordingActive) {
                stopConversationRecordingAndSummarize();
            } else {
                startConversationRecording();
            }
        });
        controlBar.addView(btnConversationRecordToggle);

        if (!isConversationRecordingActive && conversationTranscript.length() > 0) {
            Button btnClear = new Button(this);
            btnClear.setText("🗑️ Clear");
            btnClear.setTextSize(11);
            btnClear.setTextColor(theme.subTextColor);
            btnClear.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 10));
            btnClear.setAllCaps(false);
            LinearLayout.LayoutParams clrParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(38));
            clrParams.leftMargin = dpToPx(6);
            btnClear.setLayoutParams(clrParams);
            btnClear.setOnClickListener(v -> {
                conversationTranscript.setLength(0);
                buildConversationPanel();
            });
            controlBar.addView(btnClear);
        }

        layout.addView(controlBar);
        layout.addView(createPanelBottomBar());
        panelContainer.addView(layout);
    }

    private void startConversationRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required for conversation recording", Toast.LENGTH_SHORT).show();
            return;
        }

        isConversationRecordingActive = true;
        conversationStartTime = System.currentTimeMillis();
        triggerHaptic(20);
        buildConversationPanel();

        conversationTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConversationRecordingActive) {
                    if (tvConversationStatus != null) {
                        long elapsedSec = (System.currentTimeMillis() - conversationStartTime) / 1000;
                        long mins = elapsedSec / 60;
                        long secs = elapsedSec % 60;
                        tvConversationStatus.setText(String.format(Locale.US, "🔴 (%02d:%02d)", mins, secs));
                    }
                    conversationTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        conversationTimerHandler.postDelayed(conversationTimerRunnable, 1000);

        initContinuousConversationSpeechRecognizer();
    }

    private void stopConversationRecording() {
        isConversationRecordingActive = false;
        conversationTimerHandler.removeCallbacksAndMessages(null);
        if (conversationSpeechRecognizer != null) {
            try {
                conversationSpeechRecognizer.stopListening();
                conversationSpeechRecognizer.destroy();
                conversationSpeechRecognizer = null;
            } catch (Exception ignored) {}
        }
    }

    private void initContinuousConversationSpeechRecognizer() {
        if (conversationSpeechRecognizer != null) {
            try {
                conversationSpeechRecognizer.destroy();
            } catch (Exception ignored) {}
        }

        try {
            conversationSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

            conversationSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    if (isConversationRecordingActive) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (isConversationRecordingActive) {
                                initContinuousConversationSpeechRecognizer();
                            }
                        }, 250);
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String phrase = matches.get(0).trim();
                            if (!phrase.isEmpty()) {
                                if (conversationTranscript.length() > 0) {
                                    conversationTranscript.append(" ");
                                }
                                conversationTranscript.append(phrase).append(".");
                                if (tvConversationLiveTranscript != null) {
                                    tvConversationLiveTranscript.setText(conversationTranscript.toString());
                                    tvConversationLiveTranscript.setTextColor(Color.WHITE);
                                }
                            }
                        }
                    }
                    if (isConversationRecordingActive) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (isConversationRecordingActive) {
                                initContinuousConversationSpeechRecognizer();
                            }
                        }, 200);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    if (partialResults != null && tvConversationLiveTranscript != null) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String live = conversationTranscript.toString() + " " + matches.get(0) + "...";
                            tvConversationLiveTranscript.setText(live);
                        }
                    }
                }

                @Override public void onEvent(int eventType, Bundle params) {}
            });

            conversationSpeechRecognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start audio listener: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isConversationRecordingActive = false;
            buildConversationPanel();
        }
    }

    private void stopConversationRecordingAndSummarize() {
        stopConversationRecording();
        triggerHaptic(20);

        final String recorded = conversationTranscript.toString().trim();
        if (recorded.isEmpty()) {
            Toast.makeText(this, "No conversation dialogue captured to summarize.", Toast.LENGTH_SHORT).show();
            buildConversationPanel();
            return;
        }

        showConversationGeneratingState();

        GeminiFlowClient.summarizeConversationWithFallback(
                recorded,
                prefs != null ? prefs.getApiKey() : "",
                prefs != null ? prefs.getSelectedModel() : KeyboardPreferences.DEFAULT_MODEL,
                new GeminiFlowClient.ConversationAnalysisCallback() {
                    @Override
                    public void onSuccess(GeminiFlowClient.ConversationAnalysisResult result) {
                        displayConversationSummaryResult(result);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(WisprKeyboardService.this, "Summary error: " + errorMessage, Toast.LENGTH_LONG).show();
                        buildConversationPanel();
                    }
                }
        );
    }

    private void showConversationGeneratingState() {
        panelContainer.removeAllViews();
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(theme.backgroundColor);

        TextView tv = new TextView(this);
        tv.setText("✨ Processing Conversation with Smart AI...\nExtracting Executive Summary & Action Items");
        tv.setTextColor(theme.accentColor);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        layout.addView(tv);

        panelContainer.addView(layout);
    }

    private void displayConversationSummaryResult(final GeminiFlowClient.ConversationAnalysisResult result) {
        panelContainer.removeAllViews();
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        // Automatically store meeting summary and action items in ActionItemsManager
        final int addedTasks = ActionItemsManager.getInstance(this).addMeeting(
                result.executiveSummary,
                result.keyPoints,
                result.actionItems,
                conversationTranscript.toString()
        );
        if (addedTasks > 0) {
            Toast.makeText(this, "📋 " + addedTasks + " Action Items saved to Settings!", Toast.LENGTH_SHORT).show();
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(theme.backgroundColor);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("✨ Meeting Summary & Action Items");
        tvTitle.setTextColor(theme.accentColor);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(tp);
        header.addView(tvTitle);

        TextView tvBadge = new TextView(this);
        tvBadge.setText((result.isFallback ? "🔄 " : "⚡ ") + result.modelUsed + " (" + result.latencyMs + "ms)");
        tvBadge.setTextColor(theme.subTextColor);
        tvBadge.setTextSize(10);
        header.addView(tvBadge);
        layout.addView(header);

        // Scrollable Results
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

        // 1. Executive Summary Card
        if (!result.executiveSummary.isEmpty()) {
            TextView lbl1 = new TextView(this);
            lbl1.setText("📋 EXECUTIVE SUMMARY");
            lbl1.setTextColor(Color.parseColor("#38bdf8"));
            lbl1.setTextSize(11);
            lbl1.setTypeface(null, Typeface.BOLD);
            content.addView(lbl1);

            TextView tvSummary = new TextView(this);
            tvSummary.setText(result.executiveSummary);
            tvSummary.setTextColor(theme.textColor);
            tvSummary.setTextSize(12);
            tvSummary.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
            tvSummary.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            content.addView(tvSummary);
        }

        // 2. Key Discussion Points Card
        if (!result.keyPoints.isEmpty()) {
            TextView lbl2 = new TextView(this);
            lbl2.setText("\n💡 KEY DISCUSSION POINTS");
            lbl2.setTextColor(Color.parseColor("#a78bfa"));
            lbl2.setTextSize(11);
            lbl2.setTypeface(null, Typeface.BOLD);
            content.addView(lbl2);

            TextView tvPoints = new TextView(this);
            tvPoints.setText(result.keyPoints);
            tvPoints.setTextColor(theme.textColor);
            tvPoints.setTextSize(12);
            tvPoints.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
            tvPoints.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            content.addView(tvPoints);
        }

        // 3. Pending Action Items Card
        if (!result.actionItems.isEmpty()) {
            TextView lbl3 = new TextView(this);
            lbl3.setText("\n⏳ PENDING ACTION ITEMS");
            lbl3.setTextColor(Color.parseColor("#34d399"));
            lbl3.setTextSize(11);
            lbl3.setTypeface(null, Typeface.BOLD);
            content.addView(lbl3);

            TextView tvActions = new TextView(this);
            tvActions.setText(result.actionItems);
            tvActions.setTextColor(theme.textColor);
            tvActions.setTextSize(12);
            tvActions.setBackground(createChipDrawable(theme.keyColor, Color.parseColor("#059669"), 8));
            tvActions.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            content.addView(tvActions);
        }

        scroll.addView(content);
        layout.addView(scroll);

        // Action Buttons Bar
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(6));

        Button btnInsert = new Button(this);
        btnInsert.setText("📥 Insert All");
        btnInsert.setTextSize(11);
        btnInsert.setTextColor(Color.WHITE);
        btnInsert.setBackground(createChipDrawable("#065f46", "#10b981", 8));
        btnInsert.setAllCaps(false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, dpToPx(36), 1.2f);
        ip.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btnInsert.setLayoutParams(ip);
        btnInsert.setOnClickListener(v -> {
            insertText(result.fullResponse + "\n");
            triggerHaptic(20);
            showPanel(PanelMode.KEYBOARD);
        });
        btnBar.addView(btnInsert);

        Button btnCopy = new Button(this);
        btnCopy.setText("📋 Copy");
        btnCopy.setTextSize(11);
        btnCopy.setTextColor(Color.WHITE);
        btnCopy.setBackground(createChipDrawable("#1e293b", "#3b82f6", 8));
        btnCopy.setAllCaps(false);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dpToPx(36), 1.0f);
        cp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btnCopy.setLayoutParams(cp);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Meeting Summary", result.fullResponse));
                Toast.makeText(WisprKeyboardService.this, "Meeting summary copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });
        btnBar.addView(btnCopy);

        Button btnViewItems = new Button(this);
        btnViewItems.setText("📋 Tasks (" + addedTasks + ")");
        btnViewItems.setTextSize(11);
        btnViewItems.setTextColor(Color.parseColor("#38bdf8"));
        btnViewItems.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
        btnViewItems.setAllCaps(false);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, dpToPx(36), 1.1f);
        vp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btnViewItems.setLayoutParams(vp);
        btnViewItems.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        btnBar.addView(btnViewItems);

        Button btnNew = new Button(this);
        btnNew.setText("🔄 New");
        btnNew.setTextSize(11);
        btnNew.setTextColor(theme.subTextColor);
        btnNew.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
        btnNew.setAllCaps(false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dpToPx(36), 0.9f);
        np.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btnNew.setLayoutParams(np);
        btnNew.setOnClickListener(v -> {
            conversationTranscript.setLength(0);
            buildConversationPanel();
        });
        btnBar.addView(btnNew);

        layout.addView(btnBar);
        layout.addView(createPanelBottomBar());
        panelContainer.addView(layout);
    }

    // --- SHORTCUTS DRAWER PANEL ---

    private void buildShortcutsPanel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("⚡ Text Shortcuts (Tap to Insert)");
        tvTitle.setTextColor(Color.parseColor("#818cf8"));
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(tp);
        header.addView(tvTitle);

        Button btnManage = new Button(this);
        btnManage.setText("+ Add / Edit");
        btnManage.setTextSize(11);
        btnManage.setTextColor(Color.parseColor("#a5b4fc"));
        btnManage.setBackgroundColor(Color.TRANSPARENT);
        btnManage.setAllCaps(false);
        btnManage.setOnClickListener(v -> openSettingsActivity());
        header.addView(btnManage);
        layout.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
        scroll.addView(content);
        layout.addView(scroll);

        List<ShortcutsManager.ShortcutItem> list = shortcutsManager.getAllShortcuts();
        int maxItemsToShow = (list.size() > 3 && !isKeyboardShortcutsExpanded) ? 3 : list.size();
        for (int i = 0; i < maxItemsToShow; i++) {
            final ShortcutsManager.ShortcutItem item = list.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(createChipDrawable("#141d2c", "#243247", 10));
            card.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dpToPx(6);
            card.setLayoutParams(cp);

            TextView tvTrigger = new TextView(this);
            tvTrigger.setText(item.trigger);
            tvTrigger.setTextColor(Color.parseColor("#818cf8"));
            tvTrigger.setTextSize(13);
            tvTrigger.setTypeface(null, Typeface.BOLD);
            card.addView(tvTrigger);

            TextView tvArrow = new TextView(this);
            tvArrow.setText(" ➔ ");
            tvArrow.setTextColor(Color.parseColor("#64748b"));
            card.addView(tvArrow);

            TextView tvExp = new TextView(this);
            tvExp.setText(item.expansion);
            tvExp.setTextColor(Color.WHITE);
            tvExp.setTextSize(13);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvExp.setLayoutParams(ep);
            card.addView(tvExp);

            card.setOnClickListener(v -> {
                insertText(item.expansion + " ");
                triggerHaptic(18);
                showPanel(PanelMode.KEYBOARD);
            });

            content.addView(card);
        }

        // Expander Button for > 3 shortcuts on keyboard
        if (list.size() > 3) {
            Button btnExpander = new Button(this);
            btnExpander.setText(isKeyboardShortcutsExpanded
                    ? "▲ Show Less (Showing All " + list.size() + ")"
                    : "▼ Show All (" + list.size() + ") Shortcuts");
            btnExpander.setTextSize(12);
            btnExpander.setAllCaps(false);
            btnExpander.setTextColor(Color.parseColor("#818cf8"));
            btnExpander.setBackground(createChipDrawable("#1e293b", "#334155", 8));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(38));
            ep.topMargin = dpToPx(4);
            btnExpander.setLayoutParams(ep);
            btnExpander.setOnClickListener(v -> {
                isKeyboardShortcutsExpanded = !isKeyboardShortcutsExpanded;
                buildShortcutsPanel();
            });
            content.addView(btnExpander);
        }

        layout.addView(createPanelBottomBar());
        panelContainer.addView(layout);
    }

    private View createPanelBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(42)));
        bar.setBackgroundColor(Color.parseColor("#080b11"));
        bar.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));

        Button btnAbc = new Button(this);
        btnAbc.setText("ABC");
        btnAbc.setTextColor(Color.WHITE);
        btnAbc.setTextSize(13);
        btnAbc.setBackground(createChipDrawable("#1e293b", "#334155", 8));
        LinearLayout.LayoutParams abcP = new LinearLayout.LayoutParams(dpToPx(70), dpToPx(36));
        btnAbc.setLayoutParams(abcP);
        btnAbc.setOnClickListener(v -> showPanel(PanelMode.KEYBOARD));
        bar.addView(btnAbc);

        View spacer = new View(this);
        LinearLayout.LayoutParams spP = new LinearLayout.LayoutParams(0, 1, 1.0f);
        spacer.setLayoutParams(spP);
        bar.addView(spacer);

        Button btnBs = new Button(this);
        btnBs.setText("⌫");
        btnBs.setTextColor(Color.WHITE);
        btnBs.setTextSize(16);
        btnBs.setBackground(createChipDrawable("#1e293b", "#334155", 8));
        LinearLayout.LayoutParams bsP = new LinearLayout.LayoutParams(dpToPx(70), dpToPx(36));
        btnBs.setLayoutParams(bsP);
        btnBs.setOnClickListener(v -> handleBackspace());
        bar.addView(btnBs);

        return bar;
    }

    // --- MAIN KEYBOARD ROWS WITH CLEAN TEXT / NUMERIC SEPARATION ---

    private void renderKeyRows() {
        stopBackspaceRepeat();
        if (keysContainer == null) return;
        keysContainer.removeAllViews();

        if (isNumpadMode) {
            renderNumpadRows();
        } else if (isSymbolMode) {
            renderSymbolRows();
            renderSpacebarRow();
        } else {
            renderAlphabetRows();
            renderSpacebarRow();
        }
    }

    private void updateKeyCapsDisplay() {
        boolean upper = isShifted || isCapsLocked;
        for (int i = 0; i < alphaKeyPrimaryLabels.size() && i < alphaKeyOriginalLetters.size(); i++) {
            TextView tv = alphaKeyPrimaryLabels.get(i);
            String orig = alphaKeyOriginalLetters.get(i);
            if (tv != null && orig != null) {
                tv.setText(upper ? orig.toUpperCase(Locale.US) : orig.toLowerCase(Locale.US));
            }
        }
        if (btnShiftKey != null) {
            KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);
            if (isCapsLocked) {
                btnShiftKey.setText("⇪");
                btnShiftKey.setTextColor(Color.WHITE);
                btnShiftKey.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
            } else if (isShifted) {
                btnShiftKey.setText("⇧");
                btnShiftKey.setTextColor(Color.WHITE);
                btnShiftKey.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
            } else {
                btnShiftKey.setText("⇧");
                btnShiftKey.setTextColor(theme.textColor);
                btnShiftKey.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
            }
        }
    }

    private void renderAlphabetRows() {
        alphaKeyPrimaryLabels.clear();
        alphaKeyOriginalLetters.clear();
        // Optional Dedicated Top Number Row (only if user explicitly turned it ON in settings or toolbar)
        if (prefs != null && prefs.isShowNumberRow()) {
            String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
            LinearLayout numRow = new LinearLayout(this);
            numRow.setOrientation(LinearLayout.HORIZONTAL);
            numRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            numRow.setPadding(0, dpToPx(1), 0, dpToPx(1));

            for (final String num : numbers) {
                Button numBtn = createButton(num, true);
                numBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) numBtn.getLayoutParams();
                p.height = dpToPx(36);
                numBtn.setLayoutParams(p);

                numBtn.setOnClickListener(v -> {
                    insertText(num);
                    currentWordBuffer.append(num);
                    updateSuggestions();
                });
                numRow.addView(numBtn);
            }
            keysContainer.addView(numRow);
        }

        // Standard QWERTY rows with secondary symbols
        String[][] alphaRows = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"shift", "z", "x", "c", "v", "b", "n", "m", "backspace"}
        };

        String[][] secondarySymbols = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "_", "&", "-", "+", "(", ")"},
            {null, "*", "\"", "'", ":", ";", "!", "?", null}
        };

        for (int r = 0; r < alphaRows.length; r++) {
            String[] rowKeys = alphaRows[r];
            String[] secKeys = secondarySymbols[r];
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rowLayout.setPadding(0, dpToPx(2), 0, dpToPx(2));

            if (r == 1) {
                rowLayout.setPadding(dpToPx(16), dpToPx(2), dpToPx(16), dpToPx(2));
            }

            for (int c = 0; c < rowKeys.length; c++) {
                String key = rowKeys[c];
                String sec = (c < secKeys.length) ? secKeys[c] : null;
                if ("shift".equals(key) || "backspace".equals(key)) {
                    rowLayout.addView(createKeyView(key));
                } else {
                    rowLayout.addView(createAlphaKeyView(key, sec));
                }
            }
            keysContainer.addView(rowLayout);
        }
    }

    private void renderSymbolRows() {
        // Dedicated number and symbol rows
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
                rowLayout.addView(createKeyView(key));
            }
            keysContainer.addView(rowLayout);
        }
    }

    /**
     * Dedicated 10-Key Numpad / PIN Layout for fast numerical, OTP, and calculation input.
     */
    private void renderNumpadRows() {
        String[][] numpad = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {",", "0", "."}
        };

        for (String[] rowKeys : numpad) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rowLayout.setPadding(dpToPx(28), dpToPx(2), dpToPx(28), dpToPx(2));

            for (final String key : rowKeys) {
                Button btn = createButton(key, false);
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                btn.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) btn.getLayoutParams();
                p.height = dpToPx(46);
                btn.setLayoutParams(p);
                btn.setOnClickListener(v -> {
                    insertText(key);
                    triggerHaptic(10);
                });
                rowLayout.addView(btn);
            }
            keysContainer.addView(rowLayout);
        }

        // Bottom row of numpad: ABC | ?#+ | ⌫ | ⏎
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setPadding(dpToPx(14), dpToPx(3), dpToPx(14), dpToPx(3));

        Button abcBtn = createButton("ABC", true);
        LinearLayout.LayoutParams abcP = (LinearLayout.LayoutParams) abcBtn.getLayoutParams();
        abcP.weight = 1.15f;
        abcBtn.setOnClickListener(v -> {
            isNumpadMode = false;
            isSymbolMode = false;
            renderKeyRows();
            triggerHaptic(15);
        });
        bottomRow.addView(abcBtn);

        Button symBtn = createButton("?123", true);
        LinearLayout.LayoutParams symP = (LinearLayout.LayoutParams) symBtn.getLayoutParams();
        symP.weight = 1.05f;
        symBtn.setOnClickListener(v -> {
            isNumpadMode = false;
            isSymbolMode = true;
            renderKeyRows();
            triggerHaptic(15);
        });
        bottomRow.addView(symBtn);

        Button delBtn = createButton("⌫", true);
        LinearLayout.LayoutParams delP = (LinearLayout.LayoutParams) delBtn.getLayoutParams();
        delP.weight = 1.2f;
        delBtn.setOnClickListener(v -> handleBackspace());
        bottomRow.addView(delBtn);

        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);
        btnEnterKey = createButton("⏎", true);
        btnEnterKey.setTextColor(Color.WHITE);
        btnEnterKey.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
        LinearLayout.LayoutParams enterP = (LinearLayout.LayoutParams) btnEnterKey.getLayoutParams();
        enterP.weight = 1.4f;
        btnEnterKey.setOnClickListener(v -> handleEnterKey());
        updateEnterKeyAction(getCurrentInputEditorInfo());
        bottomRow.addView(btnEnterKey);

        keysContainer.addView(bottomRow);
    }

    private void renderSpacebarRow() {
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setPadding(0, dpToPx(3), 0, dpToPx(3));

        // Bottom-left corner: Only numbers, numbers or punctuations, and ABC options
        if (!isSymbolMode) {
            // ABC mode -> offers ?123 (numbers/punctuations) and 123 (pure numbers)
            Button numPuncBtn = createButton("?123", true);
            LinearLayout.LayoutParams p1 = (LinearLayout.LayoutParams) numPuncBtn.getLayoutParams();
            p1.weight = 1.15f;
            numPuncBtn.setOnClickListener(v -> {
                isSymbolMode = true;
                isNumpadMode = false;
                renderKeyRows();
                triggerHaptic(15);
            });
            bottomRow.addView(numPuncBtn);

            Button numpadBtn = createButton("123", true);
            LinearLayout.LayoutParams p2 = (LinearLayout.LayoutParams) numpadBtn.getLayoutParams();
            p2.weight = 1.05f;
            numpadBtn.setOnClickListener(v -> {
                isNumpadMode = true;
                isSymbolMode = false;
                renderKeyRows();
                triggerHaptic(15);
            });
            bottomRow.addView(numpadBtn);
        } else {
            // ?123 mode -> offers ABC and 123 (pure numbers)
            Button abcBtn = createButton("ABC", true);
            LinearLayout.LayoutParams p1 = (LinearLayout.LayoutParams) abcBtn.getLayoutParams();
            p1.weight = 1.15f;
            abcBtn.setOnClickListener(v -> {
                isSymbolMode = false;
                isNumpadMode = false;
                renderKeyRows();
                triggerHaptic(15);
            });
            bottomRow.addView(abcBtn);

            Button numpadBtn = createButton("123", true);
            LinearLayout.LayoutParams p2 = (LinearLayout.LayoutParams) numpadBtn.getLayoutParams();
            p2.weight = 1.05f;
            numpadBtn.setOnClickListener(v -> {
                isNumpadMode = true;
                isSymbolMode = false;
                renderKeyRows();
                triggerHaptic(15);
            });
            bottomRow.addView(numpadBtn);
        }

        // Comma
        Button commaBtn = createButton(",", false);
        LinearLayout.LayoutParams commaParams = (LinearLayout.LayoutParams) commaBtn.getLayoutParams();
        commaParams.weight = 0.9f;
        commaBtn.setOnClickListener(v -> handlePunctuation(","));
        bottomRow.addView(commaBtn);

        // NORMAL CLEAN SPACEBAR WITH CURSOR SCRUBBING
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);
        final Button spaceBtn = new Button(this);
        spaceBtn.setText("English • Smart AI");
        spaceBtn.setTextColor(theme.subTextColor);
        spaceBtn.setTextSize(13);
        spaceBtn.setAllCaps(false);
        spaceBtn.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dpToPx(42), 4.2f);
        spaceParams.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        spaceBtn.setLayoutParams(spaceParams);

        // Cursor scrubbing on spacebar swipe
        spaceBtn.setOnTouchListener(new View.OnTouchListener() {
            private float startX = 0;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        isDragging = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getX() - startX;
                        if (Math.abs(deltaX) > dpToPx(16)) {
                            isDragging = true;
                            InputConnection ic = getCurrentInputConnection();
                            if (ic != null) {
                                if (deltaX > 0) {
                                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));
                                } else {
                                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));
                                }
                                triggerHaptic(8);
                            }
                            startX = event.getX();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            return true;
                        }
                        break;
                }
                return false;
            }
        });

        spaceBtn.setOnClickListener(v -> handleSpaceKey());
        bottomRow.addView(spaceBtn);

        // Period
        Button dotBtn = createButton(".", false);
        LinearLayout.LayoutParams dotParams = (LinearLayout.LayoutParams) dotBtn.getLayoutParams();
        dotParams.weight = 0.9f;
        dotBtn.setOnClickListener(v -> handlePunctuation("."));
        bottomRow.addView(dotBtn);

        // Enter / Action Key (NO duplicate mic button!)
        btnEnterKey = createButton("⏎", true);
        btnEnterKey.setTextColor(Color.WHITE);
        btnEnterKey.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
        LinearLayout.LayoutParams enterParams = (LinearLayout.LayoutParams) btnEnterKey.getLayoutParams();
        enterParams.weight = 1.4f;
        btnEnterKey.setOnClickListener(v -> handleEnterKey());
        updateEnterKeyAction(getCurrentInputEditorInfo());
        bottomRow.addView(btnEnterKey);

        keysContainer.addView(bottomRow);
    }

    private View createKeyView(final String key) {
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        if ("shift".equals(key)) {
            String label = isCapsLocked ? "⇪" : "⇧";
            btnShiftKey = createButton(label, true);

            if (isCapsLocked) {
                btnShiftKey.setBackground(createChipDrawable("#db2777", "#f472b6", 8));
                btnShiftKey.setTextColor(Color.WHITE);
            } else if (isShifted) {
                btnShiftKey.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
                btnShiftKey.setTextColor(Color.WHITE);
            } else {
                btnShiftKey.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
                btnShiftKey.setTextColor(theme.textColor);
            }

            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) btnShiftKey.getLayoutParams();
            p.weight = 1.4f;

            btnShiftKey.setOnClickListener(v -> {
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
                updateKeyCapsDisplay();
                triggerHaptic(15);
                playKeyClick();
            });
            return btnShiftKey;
        }

        if ("backspace".equals(key)) {
            Button bsBtn = createButton("⌫", true);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) bsBtn.getLayoutParams();
            p.weight = 1.4f;

            bsBtn.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        isBackspaceActive = true;
                        triggerHaptic(15);
                        handleBackspace();
                        backspaceHandler.removeCallbacks(backspaceRepeatRunnable);
                        backspaceHandler.postDelayed(backspaceRepeatRunnable, 400);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopBackspaceRepeat();
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
                playKeyClick();
            });
            return abcBtn;
        }

        // Fallback single-character button
        final String displayChar = (isShifted || isCapsLocked) ? key.toUpperCase(Locale.US) : key.toLowerCase(Locale.US);
        Button charBtn = createButton(displayChar, false);
        charBtn.setOnClickListener(v -> {
            insertText(displayChar);
            currentWordBuffer.append(displayChar);

            if (isShifted && !isCapsLocked) {
                isShifted = false;
                updateKeyCapsDisplay();
            }

            updateSuggestions();
        });
        return charBtn;
    }

    /**
     * Samsung-Style Alphabet Key View with visible secondary symbol subscript and animated visual long-press indicator.
     */
    private View createAlphaKeyView(final String key, final String secondaryChar) {
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        final LinearLayout keyLayout = new LinearLayout(this);
        keyLayout.setOrientation(LinearLayout.VERTICAL);
        keyLayout.setGravity(Gravity.CENTER);
        keyLayout.setClickable(true);
        keyLayout.setFocusable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(46), 1.0f);
        params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
        keyLayout.setLayoutParams(params);
        final GradientDrawable normalBg = createChipDrawable(theme.keyColor, theme.dividerColor, 8);
        final GradientDrawable activeSecondaryBg = createChipDrawable(theme.accentColor, Color.WHITE, 8);
        keyLayout.setBackground(normalBg);

        // Secondary Symbol Label (Subtle top-right in idle, visibly highlighted on hold)
        boolean showSecondary = prefs == null || prefs.isShowSecondarySymbols();
        final TextView tvSecondary;
        if (showSecondary && secondaryChar != null && !secondaryChar.isEmpty()) {
            tvSecondary = new TextView(this);
            tvSecondary.setText(secondaryChar);
            tvSecondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            tvSecondary.setTextColor(theme.subTextColor);
            tvSecondary.setTypeface(Typeface.DEFAULT_BOLD);
            tvSecondary.setGravity(Gravity.END | Gravity.TOP);
            tvSecondary.setPadding(0, dpToPx(1), dpToPx(4), 0);
            LinearLayout.LayoutParams secParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(13));
            tvSecondary.setLayoutParams(secParams);
            keyLayout.addView(tvSecondary);
        } else {
            tvSecondary = null;
            View spacer = new View(this);
            LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(showSecondary ? 10 : 4));
            spacer.setLayoutParams(spParams);
            keyLayout.addView(spacer);
        }

        // Primary Letter Label
        final TextView tvPrimary = new TextView(this);
        String initialDisplay = (isShifted || isCapsLocked) ? key.toUpperCase(Locale.US) : key.toLowerCase(Locale.US);
        tvPrimary.setText(initialDisplay);
        tvPrimary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvPrimary.setTextColor(theme.textColor);
        tvPrimary.setTypeface(Typeface.DEFAULT_BOLD);
        tvPrimary.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams primParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        primParams.bottomMargin = dpToPx(2);
        tvPrimary.setLayoutParams(primParams);
        keyLayout.addView(tvPrimary);

        alphaKeyPrimaryLabels.add(tvPrimary);
        alphaKeyOriginalLetters.add(key);

        final Handler visualHandler = new Handler(Looper.getMainLooper());
        final boolean hasSecondary = secondaryChar != null && !secondaryChar.isEmpty();

        keyLayout.setOnClickListener(v -> {
            String charToInsert = (isShifted || isCapsLocked) ? key.toUpperCase(Locale.US) : key.toLowerCase(Locale.US);
            insertText(charToInsert);
            currentWordBuffer.append(charToInsert);

            // Auto-capitalization: single-shift is consumed after one letter
            if (isShifted && !isCapsLocked) {
                isShifted = false;
                updateKeyCapsDisplay();
            }

            updateSuggestions();
        });

        if (hasSecondary) {
            keyLayout.setOnLongClickListener(v -> {
                // 1. VISUAL INDICATION: Key illuminates in vibrant accent color with bold bright white secondary character!
                keyLayout.setBackground(activeSecondaryBg);
                if (tvSecondary != null) {
                    tvSecondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvSecondary.setTextColor(Color.WHITE);
                    tvSecondary.setTypeface(Typeface.DEFAULT_BOLD);
                }
                tvPrimary.setAlpha(0.2f);

                // 2. Insert secondary character
                insertText(secondaryChar);
                if (Character.isDigit(secondaryChar.charAt(0))) {
                    currentWordBuffer.append(secondaryChar);
                    updateSuggestions();
                } else {
                    handlePunctuation(secondaryChar);
                }
                triggerHaptic(30);

                // 3. Smoothly restore idle appearance
                visualHandler.postDelayed(() -> {
                    keyLayout.setBackground(normalBg);
                    if (tvSecondary != null) {
                        tvSecondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
                        tvSecondary.setTextColor(theme.subTextColor);
                    }
                    tvPrimary.setAlpha(1.0f);
                }, 220);

                return true;
            });
        }

        return keyLayout;
    }

    private Button createButton(String text, boolean isFunctionKey) {
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(theme.textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isFunctionKey ? 14 : 17);
        btn.setAllCaps(false);
        int bg = isFunctionKey ? theme.actionKeyColor : theme.keyColor;
        btn.setBackground(createChipDrawable(bg, theme.dividerColor, 8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(42), 1.0f);
        params.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private GradientDrawable createChipDrawable(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) {
            gd.setStroke(dpToPx(1), strokeColor);
        }
        gd.setCornerRadius(dpToPx(radiusDp));
        return gd;
    }

    private GradientDrawable createChipDrawable(String fillColor, String strokeColor, int radiusDp) {
        int fill = Color.parseColor(fillColor);
        int stroke = strokeColor != null ? Color.parseColor(strokeColor) : Color.TRANSPARENT;
        return createChipDrawable(fill, stroke, radiusDp);
    }

    private void playKeyClick() {
        if (prefs != null && prefs.isSoundEnabled() && audioManager != null) {
            audioManager.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD);
        }
    }

    // --- TEXT INSERTION & BACKSPACE (CRITICAL BUG FIX FOR SELECTED TEXT) ---

    private void insertText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            triggerHaptic(10);
            playKeyClick();
        }
    }

    /**
     * Handles backspace correctly for BOTH highlighted/selected text AND standard character deletion.
     */
    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        playKeyClick();

        // 1. Check if user has selected text to delete
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            // Replacing selection with empty string immediately deletes highlighted text!
            ic.commitText("", 1);
            currentWordBuffer.setLength(0);
        } else {
            // 2. Standard single character deletion
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));

            if (currentWordBuffer.length() > 0) {
                currentWordBuffer.deleteCharAt(currentWordBuffer.length() - 1);
            }
        }

        evaluateAutoCaps();
        updateSuggestions();
        triggerHaptic(10);
    }

    private void handleSpaceKey() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        playKeyClick();
        long now = System.currentTimeMillis();

        // 1. Two spaces replaced as a . and a space (Double-space period)
        if (prefs == null || prefs.isDoubleSpacePeriodEnabled()) {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            boolean isDoubleSpace = false;
            if (before != null && before.length() > 0 && before.charAt(before.length() - 1) == ' ') {
                if (before.length() >= 2) {
                    char prevChar = before.charAt(before.length() - 2);
                    if (Character.isLetterOrDigit(prevChar) || prevChar == ')' || prevChar == '"' || prevChar == '\'') {
                        isDoubleSpace = true;
                    }
                } else if (now - lastSpacePressTime < 600) {
                    isDoubleSpace = true;
                }
            }

            if (isDoubleSpace) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                currentWordBuffer.setLength(0);
                lastCommittedWord = ".";
                lastSpacePressTime = 0;

                if (prefs == null || prefs.isAutoCapsEnabled()) {
                    if (!isCapsLocked) {
                        isShifted = true;
                        updateKeyCapsDisplay();
                    }
                }

                triggerHaptic(12);
                updateSuggestions();
                return;
            }
        }

        lastSpacePressTime = now;
        String currentWord = currentWordBuffer.toString().trim();

        // 2. Check Shortcuts Manager first
        String expansion = shortcutsManager != null ? shortcutsManager.getExpansion(currentWord) : null;
        if (expansion != null) {
            ic.deleteSurroundingText(currentWordBuffer.length(), 0);
            ic.commitText(expansion + " ", 1);
            suggestionEngine.recordWordPair(lastCommittedWord, expansion);
            lastCommittedWord = expansion;
        }
        // 3. Error Corrections (Standalone "i" -> "I", "dont" -> "don't", "teh" -> "the", contractions, etc.)
        else if (!currentWord.isEmpty() && (prefs == null || prefs.isAutoCorrectEnabled())) {
            String autocorrect = suggestionEngine.getAutocorrect(currentWord);
            if (autocorrect != null) {
                ic.deleteSurroundingText(currentWordBuffer.length(), 0);
                ic.commitText(autocorrect + " ", 1);
                suggestionEngine.recordWordPair(lastCommittedWord, autocorrect);
                lastCommittedWord = autocorrect;
            } else {
                ic.commitText(" ", 1);
                suggestionEngine.recordWordPair(lastCommittedWord, currentWord);
                lastCommittedWord = currentWord;
            }
        } else {
            ic.commitText(" ", 1);
            if (!currentWord.isEmpty()) {
                suggestionEngine.recordWordPair(lastCommittedWord, currentWord);
                lastCommittedWord = currentWord;
            }
        }

        currentWordBuffer.setLength(0);
        triggerHaptic(10);
        updateSuggestions();
        evaluateAutoCaps();
    }

    private void handlePunctuation(String punc) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        playKeyClick();
        String currentWord = currentWordBuffer.toString().trim();
        String expansion = shortcutsManager != null ? shortcutsManager.getExpansion(currentWord) : null;

        if (expansion != null) {
            ic.deleteSurroundingText(currentWordBuffer.length(), 0);
            ic.commitText(expansion + punc + " ", 1);
            suggestionEngine.recordWordPair(lastCommittedWord, expansion);
            lastCommittedWord = expansion;
        } else if (!currentWord.isEmpty() && (prefs == null || prefs.isAutoCorrectEnabled())) {
            String autocorrect = suggestionEngine.getAutocorrect(currentWord);
            if (autocorrect != null) {
                ic.deleteSurroundingText(currentWordBuffer.length(), 0);
                ic.commitText(autocorrect + punc + " ", 1);
                suggestionEngine.recordWordPair(lastCommittedWord, autocorrect);
                lastCommittedWord = autocorrect;
            } else {
                ic.commitText(punc + " ", 1);
                suggestionEngine.recordWordPair(lastCommittedWord, currentWord);
                lastCommittedWord = currentWord;
            }
        } else {
            ic.commitText(punc + " ", 1);
            if (!currentWord.isEmpty()) {
                suggestionEngine.recordWordPair(lastCommittedWord, currentWord);
                lastCommittedWord = currentWord;
            }
        }

        currentWordBuffer.setLength(0);
        triggerHaptic(10);
        updateSuggestions();

        if (punc.equals(".") || punc.equals("?") || punc.equals("!")) {
            if ((prefs == null || prefs.isAutoCapsEnabled()) && !isCapsLocked) {
                isShifted = true;
                updateKeyCapsDisplay();
            }
        }
    }

    private void handleEnterKey() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        playKeyClick();
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = (ei != null) ? (ei.imeOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) : 0;

        if (action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_SEARCH ||
            action == EditorInfo.IME_ACTION_SEND ||
            action == EditorInfo.IME_ACTION_NEXT ||
            action == EditorInfo.IME_ACTION_DONE) {
            ic.performEditorAction(action);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            if ((prefs == null || prefs.isAutoCapsEnabled()) && !isCapsLocked) {
                isShifted = true;
                updateKeyCapsDisplay();
            }
        }

        currentWordBuffer.setLength(0);
        triggerHaptic(20);
        updateSuggestions();
    }

    /**
     * Automatic Capitalization Evaluator:
     * Capitalizes first letter of sentence, after sentence-ending punctuation (. ? !) or newline,
     * while respecting passwords, emails, URLs, and numbers.
     */
    private void evaluateAutoCaps() {
        if (prefs == null || !prefs.isAutoCapsEnabled() || isCapsLocked) return;

        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int inputType = ei.inputType;
            int cls = inputType & EditorInfo.TYPE_MASK_CLASS;
            int variation = inputType & EditorInfo.TYPE_MASK_VARIATION;

            // Skip auto-capitalization for password, email, URL, and number fields
            if (cls == EditorInfo.TYPE_CLASS_NUMBER ||
                cls == EditorInfo.TYPE_CLASS_PHONE ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_URI ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                if (isShifted) {
                    isShifted = false;
                    updateKeyCapsDisplay();
                }
                return;
            }

            if ((inputType & EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                isCapsLocked = true;
                isShifted = true;
                updateKeyCapsDisplay();
                return;
            }

            if ((inputType & EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
                isShifted = true;
                updateKeyCapsDisplay();
                return;
            }
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            if (!isShifted) {
                isShifted = true;
                updateKeyCapsDisplay();
            }
            return;
        }

        CharSequence before = ic.getTextBeforeCursor(40, 0);
        boolean shouldCap = false;

        if (before == null || before.length() == 0) {
            shouldCap = true;
        } else {
            String str = before.toString();
            int i = str.length() - 1;
            while (i >= 0 && (str.charAt(i) == ' ' || str.charAt(i) == '\t')) {
                i--;
            }
            if (i < 0) {
                shouldCap = true;
            } else {
                char lastChar = str.charAt(i);
                if (lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '\n') {
                    shouldCap = true;
                }
            }
        }

        if (isShifted != shouldCap) {
            isShifted = shouldCap;
            updateKeyCapsDisplay();
        }
    }

    // --- SAMSUNG ONE-HANDED KEYBOARD DOCKS & MODES ---

    private LinearLayout createDockPanel(final boolean isLeftPanel) {
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setGravity(Gravity.CENTER);
        dock.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48), LinearLayout.LayoutParams.MATCH_PARENT));
        dock.setBackgroundColor(Color.TRANSPARENT);
        dock.setVisibility(View.GONE);

        // Switch direction button
        Button btnSwitch = new Button(this);
        btnSwitch.setText(isLeftPanel ? "◀" : "▶");
        btnSwitch.setTextSize(14);
        btnSwitch.setTextColor(theme.textColor);
        btnSwitch.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(42));
        p1.setMargins(0, dpToPx(4), 0, dpToPx(4));
        btnSwitch.setLayoutParams(p1);
        btnSwitch.setOnClickListener(v -> {
            String newMode = isLeftPanel ? "left" : "right";
            if (prefs != null) prefs.setOneHandedMode(newMode);
            applyOneHandedMode();
            triggerHaptic(15);
        });
        dock.addView(btnSwitch);

        // Expand to Full Width button
        Button btnExpand = new Button(this);
        btnExpand.setText("⤢");
        btnExpand.setTextSize(16);
        btnExpand.setTextColor(theme.textColor);
        btnExpand.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(42));
        p2.setMargins(0, dpToPx(4), 0, dpToPx(4));
        btnExpand.setLayoutParams(p2);
        btnExpand.setOnClickListener(v -> {
            if (prefs != null) prefs.setOneHandedMode("full");
            applyOneHandedMode();
            triggerHaptic(15);
        });
        dock.addView(btnExpand);

        return dock;
    }

    private void applyOneHandedMode() {
        if (keyboardBodyContainer == null || leftDockPanel == null || rightDockPanel == null || mainInputContainer == null) return;
        String mode = prefs != null ? prefs.getOneHandedMode() : "full";

        if ("right".equalsIgnoreCase(mode)) {
            leftDockPanel.setVisibility(View.VISIBLE);
            rightDockPanel.setVisibility(View.GONE);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4.2f);
            mainInputContainer.setLayoutParams(p);
        } else if ("left".equalsIgnoreCase(mode)) {
            leftDockPanel.setVisibility(View.GONE);
            rightDockPanel.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4.2f);
            mainInputContainer.setLayoutParams(p);
        } else {
            leftDockPanel.setVisibility(View.GONE);
            rightDockPanel.setVisibility(View.GONE);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mainInputContainer.setLayoutParams(p);
        }
    }

    private void cycleOneHandedMode() {
        String current = prefs != null ? prefs.getOneHandedMode() : "full";
        String next;
        if ("full".equalsIgnoreCase(current)) {
            next = "right";
        } else if ("right".equalsIgnoreCase(current)) {
            next = "left";
        } else {
            next = "full";
        }
        if (prefs != null) prefs.setOneHandedMode(next);
        applyOneHandedMode();
        triggerHaptic(15);
        Toast.makeText(this, "One-Handed Mode: " + next.toUpperCase(Locale.US), Toast.LENGTH_SHORT).show();
    }

    // --- SAMSUNG TEXT EDITING TOOL (D-PAD & SELECTION MODE) ---

    private void buildTextEditingPanel() {
        panelContainer.removeAllViews();
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(theme.backgroundColor);
        layout.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));

        // 1. Top Operations Bar: [Select] [Select All] [Cut] [Copy] [Paste] [⌫]
        LinearLayout opRow = new LinearLayout(this);
        opRow.setOrientation(LinearLayout.HORIZONTAL);
        opRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(38)));

        final Button btnSelectMode = new Button(this);
        btnSelectMode.setText(isTextEditingSelecting ? "✓ Selecting" : "Select");
        btnSelectMode.setTextSize(11);
        btnSelectMode.setAllCaps(false);
        btnSelectMode.setTextColor(Color.WHITE);
        btnSelectMode.setBackground(createChipDrawable(
                isTextEditingSelecting ? theme.accentColor : theme.actionKeyColor,
                theme.dividerColor, 8));
        LinearLayout.LayoutParams selP = new LinearLayout.LayoutParams(0, dpToPx(34), 1.3f);
        selP.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btnSelectMode.setLayoutParams(selP);
        btnSelectMode.setOnClickListener(v -> {
            isTextEditingSelecting = !isTextEditingSelecting;
            btnSelectMode.setText(isTextEditingSelecting ? "✓ Selecting" : "Select");
            btnSelectMode.setBackground(createChipDrawable(
                    isTextEditingSelecting ? theme.accentColor : theme.actionKeyColor,
                    theme.dividerColor, 8));
            triggerHaptic(15);
        });
        opRow.addView(btnSelectMode);

        Button btnSelectAll = createTextEditBtn("Select All", 1.2f, theme, () -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.performContextMenuAction(android.R.id.selectAll);
            triggerHaptic(15);
        });
        opRow.addView(btnSelectAll);

        Button btnCut = createTextEditBtn("Cut", 0.9f, theme, () -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.performContextMenuAction(android.R.id.cut);
            triggerHaptic(15);
        });
        opRow.addView(btnCut);

        Button btnCopy = createTextEditBtn("Copy", 0.9f, theme, () -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.performContextMenuAction(android.R.id.copy);
            triggerHaptic(15);
        });
        opRow.addView(btnCopy);

        Button btnPaste = createTextEditBtn("Paste", 0.9f, theme, () -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.performContextMenuAction(android.R.id.paste);
            triggerHaptic(15);
        });
        opRow.addView(btnPaste);

        Button btnDel = createTextEditBtn("⌫", 0.8f, theme, this::handleBackspace);
        opRow.addView(btnDel);

        layout.addView(opRow);

        // 2. Center Precision 4-Way D-Pad
        LinearLayout dpadContainer = new LinearLayout(this);
        dpadContainer.setOrientation(LinearLayout.VERTICAL);
        dpadContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dpadParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        dpadContainer.setLayoutParams(dpadParams);

        // D-Pad Up
        LinearLayout dpadRow1 = new LinearLayout(this);
        dpadRow1.setOrientation(LinearLayout.HORIZONTAL);
        dpadRow1.setGravity(Gravity.CENTER);
        Button btnUp = createDpadArrowBtn("▲", KeyEvent.KEYCODE_DPAD_UP, theme);
        dpadRow1.addView(btnUp);
        dpadContainer.addView(dpadRow1);

        // D-Pad Left | Enter | Right
        LinearLayout dpadRow2 = new LinearLayout(this);
        dpadRow2.setOrientation(LinearLayout.HORIZONTAL);
        dpadRow2.setGravity(Gravity.CENTER);
        Button btnLeft = createDpadArrowBtn("◀", KeyEvent.KEYCODE_DPAD_LEFT, theme);
        dpadRow2.addView(btnLeft);

        Button btnEnter = new Button(this);
        btnEnter.setText("⏎");
        btnEnter.setTextSize(16);
        btnEnter.setTextColor(Color.WHITE);
        btnEnter.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
        LinearLayout.LayoutParams enterP = new LinearLayout.LayoutParams(dpToPx(56), dpToPx(42));
        enterP.setMargins(dpToPx(12), 0, dpToPx(12), 0);
        btnEnter.setLayoutParams(enterP);
        btnEnter.setOnClickListener(v -> handleEnterKey());
        dpadRow2.addView(btnEnter);

        Button btnRight = createDpadArrowBtn("▶", KeyEvent.KEYCODE_DPAD_RIGHT, theme);
        dpadRow2.addView(btnRight);
        dpadContainer.addView(dpadRow2);

        // D-Pad Down
        LinearLayout dpadRow3 = new LinearLayout(this);
        dpadRow3.setOrientation(LinearLayout.HORIZONTAL);
        dpadRow3.setGravity(Gravity.CENTER);
        Button btnDown = createDpadArrowBtn("▼", KeyEvent.KEYCODE_DPAD_DOWN, theme);
        dpadRow3.addView(btnDown);
        dpadContainer.addView(dpadRow3);

        layout.addView(dpadContainer);

        // 3. Bottom Row: [⇤ Start] [⇥ End] [⌨️ Keyboard]
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40)));

        Button btnHome = createTextEditBtn("⇤ Line Start", 1.0f, theme, () -> {
            sendDpadKey(KeyEvent.KEYCODE_MOVE_HOME, isTextEditingSelecting);
        });
        bottomRow.addView(btnHome);

        Button btnEnd = createTextEditBtn("⇥ Line End", 1.0f, theme, () -> {
            sendDpadKey(KeyEvent.KEYCODE_MOVE_END, isTextEditingSelecting);
        });
        bottomRow.addView(btnEnd);

        Button btnReturn = new Button(this);
        btnReturn.setText("⌨️ Return to Keyboard");
        btnReturn.setTextSize(12);
        btnReturn.setTextColor(Color.WHITE);
        btnReturn.setAllCaps(false);
        btnReturn.setBackground(createChipDrawable(theme.accentColor, Color.TRANSPARENT, 8));
        LinearLayout.LayoutParams retP = new LinearLayout.LayoutParams(0, dpToPx(36), 1.5f);
        retP.setMargins(dpToPx(4), 0, dpToPx(2), 0);
        btnReturn.setLayoutParams(retP);
        btnReturn.setOnClickListener(v -> showPanel(PanelMode.KEYBOARD));
        bottomRow.addView(btnReturn);

        layout.addView(bottomRow);

        panelContainer.addView(layout);
    }

    private Button createTextEditBtn(String text, float weight, KeyboardTheme theme, Runnable action) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(11);
        btn.setAllCaps(false);
        btn.setTextColor(theme.textColor);
        btn.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dpToPx(34), weight);
        p.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(p);
        btn.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        return btn;
    }

    private Button createDpadArrowBtn(String arrow, final int keyCode, KeyboardTheme theme) {
        Button btn = new Button(this);
        btn.setText(arrow);
        btn.setTextSize(18);
        btn.setTextColor(theme.textColor);
        btn.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dpToPx(56), dpToPx(42));
        p.setMargins(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2));
        btn.setLayoutParams(p);
        btn.setOnClickListener(v -> sendDpadKey(keyCode, isTextEditingSelecting));
        return btn;
    }

    private void sendDpadKey(int keyCode, boolean selecting) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long now = System.currentTimeMillis();
        if (selecting) {
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_SHIFT_ON));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, KeyEvent.META_SHIFT_ON));
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }
        triggerHaptic(10);
        playKeyClick();
    }

    private void updateEnterKeyAction(EditorInfo ei) {
        if (btnEnterKey == null || ei == null) return;
        int action = ei.imeOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION);

        switch (action) {
            case EditorInfo.IME_ACTION_SEARCH:
                btnEnterKey.setText("🔍");
                break;
            case EditorInfo.IME_ACTION_SEND:
                btnEnterKey.setText("➤");
                break;
            case EditorInfo.IME_ACTION_GO:
                btnEnterKey.setText("➔");
                break;
            case EditorInfo.IME_ACTION_NEXT:
                btnEnterKey.setText("➜");
                break;
            case EditorInfo.IME_ACTION_DONE:
                btnEnterKey.setText("✓");
                break;
            default:
                btnEnterKey.setText("⏎");
                break;
        }
    }

    private void captureSystemClipboard() {
        if (systemClipboard != null && systemClipboard.hasPrimaryClip() && systemClipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = systemClipboard.getPrimaryClip().getItemAt(0).getText();
            if (text != null && text.length() > 0) {
                String str = text.toString();
                clipboardManager.addClip(str);
                lastCopiedSnippet = str;
            }
        }
    }

    // --- SEPARATE DEDICATED VOICE DICTATION & DIGIT COLLAPSING ---

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    if (tvVoiceStatus != null) {
                        tvVoiceStatus.setText("🎙️ Listening... Speak naturally");
                    }
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (isListening && tvVoiceStatus != null) {
                        int dots = Math.max(1, Math.min(4, (int) ((rmsdB + 2) / 2.5)));
                        StringBuilder sb = new StringBuilder("🎙️ Listening");
                        for (int i = 0; i < dots; i++) sb.append(" •");
                        tvVoiceStatus.setText(sb.toString());
                    }
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    if (tvVoiceStatus != null) {
                        tvVoiceStatus.setText("✨ AI Polishing speech with Gemini...");
                    }
                }

                @Override
                public void onError(int error) {
                    Log.w(TAG, "SpeechRecognizer error: " + error);
                    isListening = false;
                    hideVoiceBanner();
                    Toast.makeText(WisprKeyboardService.this, "Voice recognition ended (code " + error + ")", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    hideVoiceBanner();
                    ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        processSpokenText(matches.get(0));
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty() && tvVoiceStatus != null) {
                        tvVoiceStatus.setText("“" + matches.get(0) + "”");
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void toggleVoiceDictation() {
        if (isListening) {
            stopVoiceDictation();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required! Opening Settings...", Toast.LENGTH_LONG).show();
            openSettingsActivity();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Google Speech Recognizer not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer == null) {
            initSpeechRecognizer();
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        isListening = true;
        triggerHaptic(25);

        if (voiceStatusBanner != null) {
            voiceStatusBanner.setVisibility(View.VISIBLE);
            tvVoiceStatus.setText("🎙️ Initializing voice recognition...");
        }

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            isListening = false;
            hideVoiceBanner();
            Toast.makeText(this, "Could not start mic: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVoiceDictation() {
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
        }
        isListening = false;
        hideVoiceBanner();
    }

    private void hideVoiceBanner() {
        if (voiceStatusBanner != null) {
            voiceStatusBanner.setVisibility(View.GONE);
        }
    }

    private void processSpokenText(final String rawText) {
        final FlowMode mode = FlowMode.fromId(prefs.getFlowMode());
        final String model = prefs.getSelectedModel();
        final String apiKey = prefs.getApiKey();

        Toast.makeText(this, "✨ Polishing with " + mode.getDisplayName() + "...", Toast.LENGTH_SHORT).show();

        GeminiFlowClient.polishSpeechWithFallback(rawText, apiKey, model, mode, new GeminiFlowClient.PolishResultCallback() {
            @Override
            public void onSuccess(String polishedText, long latencyMs, String modelUsed, boolean isFallback) {
                // Collapse any remaining spoken spaced digits (e.g. OTPs, phone numbers)
                String formatted = GeminiFlowClient.collapseSpokenDigits(polishedText);

                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !formatted.isEmpty()) {
                    ic.commitText(formatted + " ", 1);
                    triggerHaptic(new long[]{20, 40});
                }

                // Show transparent engine indicator
                if (modelUsed != null && (modelUsed.toLowerCase().contains("offline") || modelUsed.contains("Local"))) {
                    Toast.makeText(WisprKeyboardService.this, "⚪ Offline Polishing Engine", Toast.LENGTH_SHORT).show();
                } else if (isFallback) {
                    Toast.makeText(WisprKeyboardService.this, "🟡 Fallback AI: " + modelUsed + " (" + latencyMs + "ms)", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(WisprKeyboardService.this, "🟢 Pure AI: " + modelUsed + " (" + latencyMs + "ms)", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage, String fallbackText) {
                String formatted = GeminiFlowClient.collapseSpokenDigits(fallbackText);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !formatted.isEmpty()) {
                    ic.commitText(formatted + " ", 1);
                }
                Toast.makeText(WisprKeyboardService.this, "⚪ Offline Polishing Engine", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cycleNextMode() {
        FlowMode[] modes = FlowMode.values();
        FlowMode current = FlowMode.fromId(prefs.getFlowMode());
        int nextIndex = (current.ordinal() + 1) % modes.length;
        FlowMode next = modes[nextIndex];
        prefs.setFlowMode(next.getId());
        updateModeChipText();
        Toast.makeText(this, "AI Mode: " + next.getDisplayName(), Toast.LENGTH_SHORT).show();
        triggerHaptic(20);
    }

    private void updateModeChipText() {
        if (modeChipButton != null) {
            FlowMode mode = FlowMode.fromId(prefs.getFlowMode());
            modeChipButton.setText(mode.getDisplayName());
        }
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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

    // --- THEME CHOOSER PANEL & DYNAMIC LIVE THEME APPLICATION ---

    private void buildThemesPanel() {
        panelContainer.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        header.setBackgroundColor(Color.parseColor("#0e1726"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🎨 Keyboard Themes");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(tP);
        header.addView(tvTitle);

        Button btnClose = new Button(this);
        btnClose.setText("✕ Close");
        btnClose.setTextSize(11);
        btnClose.setTextColor(Color.parseColor("#94a3b8"));
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setOnClickListener(v -> showPanel(PanelMode.KEYBOARD));
        header.addView(btnClose);

        panelContainer.addView(header);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout themesList = new LinearLayout(this);
        themesList.setOrientation(LinearLayout.VERTICAL);
        themesList.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(12));

        String currentId = prefs.getThemeId();

        for (final KeyboardTheme t : KeyboardTheme.getAllThemes()) {
            final boolean isActive = t.id.equalsIgnoreCase(currentId);

            LinearLayout themeCard = new LinearLayout(this);
            themeCard.setOrientation(LinearLayout.HORIZONTAL);
            themeCard.setGravity(Gravity.CENTER_VERTICAL);
            themeCard.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

            GradientDrawable cd = new GradientDrawable();
            cd.setShape(GradientDrawable.RECTANGLE);
            cd.setCornerRadius(dpToPx(8));
            cd.setColor(t.backgroundColor);
            cd.setStroke(dpToPx(isActive ? 2 : 1), isActive ? t.accentColor : Color.parseColor("#334155"));
            themeCard.setBackground(cd);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, dpToPx(4), 0, dpToPx(6));
            themeCard.setLayoutParams(cardParams);

            // Color Swatches
            LinearLayout swatches = new LinearLayout(this);
            swatches.setOrientation(LinearLayout.HORIZONTAL);
            swatches.setGravity(Gravity.CENTER_VERTICAL);

            swatches.addView(createSwatchCircle(t.backgroundColor, t.accentColor));
            swatches.addView(createSwatchCircle(t.keyColor, Color.TRANSPARENT));
            swatches.addView(createSwatchCircle(t.accentColor, Color.TRANSPARENT));
            themeCard.addView(swatches);

            // Name
            TextView tvName = new TextView(this);
            tvName.setText(t.icon + "  " + t.name + (isActive ? "  ✓ Active" : ""));
            tvName.setTextColor(t.textColor);
            tvName.setTextSize(13);
            tvName.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            nameParams.setMargins(dpToPx(12), 0, 0, 0);
            tvName.setLayoutParams(nameParams);
            themeCard.addView(tvName);

            themeCard.setOnClickListener(v -> {
                prefs.setThemeId(t.id);
                applyCurrentTheme();
                buildThemesPanel();
                Toast.makeText(WisprKeyboardService.this, "🎨 Applied " + t.name, Toast.LENGTH_SHORT).show();
            });

            themesList.addView(themeCard);
        }

        sv.addView(themesList);
        panelContainer.addView(sv);
    }

    private View createSwatchCircle(int fillColor, int strokeColor) {
        View dot = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) {
            gd.setStroke(dpToPx(1), strokeColor);
        }
        dot.setBackground(gd);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dpToPx(14), dpToPx(14));
        p.setMargins(0, 0, dpToPx(4), 0);
        dot.setLayoutParams(p);
        return dot;
    }

    private void applyCurrentTheme() {
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs != null ? prefs.getThemeId() : null);
        if (keyboardRoot != null) {
            keyboardRoot.setBackgroundColor(theme.backgroundColor);
        }
        if (toolbarContainer != null) {
            toolbarContainer.setBackgroundColor(theme.toolbarColor);
        }
        if (keysContainer != null) {
            renderKeyRows();
        }
    }

    // --- CONTACT CARD SCANNER INTEGRATION & MEDIA INSERTION ---

    private void openCardScanner() {
        try {
            Intent intent = new Intent(this, CardScannerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Card Scanner: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendImageMedia(Uri imageUri, String mimeType) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || imageUri == null) return;
        try {
            InputContentInfoCompat inputContentInfo = new InputContentInfoCompat(
                    imageUri,
                    new ClipDescription("Contact Card Image", new String[]{mimeType}),
                    null
            );
            EditorInfo editorInfo = getCurrentInputEditorInfo();
            int flags = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
            }
            boolean committed = InputConnectionCompat.commitContent(ic, editorInfo, inputContentInfo, flags, null);
            if (committed) {
                Toast.makeText(this, "📇 Contact Card & Image delivered!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "📇 Card Text inserted! (Image ready on clipboard)", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in sendImageMedia", e);
            Toast.makeText(this, "📇 Contact card text inserted!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Downloads/caches the GIF file in a background worker thread and delivers it via commitContent.
     * Prevents raw URLs from ever being pasted.
     */
    private void sendGifMedia(final GifReactionsData.GifItem item) {
        if (item == null) return;

        // If it's a kaomoji or text sticker (no gifUrl)
        if (item.gifUrl == null || item.gifUrl.trim().isEmpty()) {
            insertText(item.fallbackText + " ");
            triggerHaptic(18);
            showPanel(PanelMode.KEYBOARD);
            return;
        }

        triggerHaptic(18);
        Toast.makeText(this, "⏳ Preparing " + item.title + " GIF...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                File gifDir = new File(getCacheDir(), "gifs");
                if (!gifDir.exists()) {
                    gifDir.mkdirs();
                }

                String safeName = "gif_" + Math.abs(item.gifUrl.hashCode()) + ".gif";
                File targetFile = new File(gifDir, safeName);

                if (!targetFile.exists() || targetFile.length() < 100) {
                    URL url = new URL(item.gifUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(12000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; SmartAIKeyboard)");
                    conn.connect();

                    InputStream in = conn.getInputStream();
                    FileOutputStream out = new FileOutputStream(targetFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.flush();
                    out.close();
                    in.close();
                }

                Uri contentUri = FileProvider.getUriForFile(
                        WisprKeyboardService.this,
                        "com.wisprflow.keyboard.fileprovider",
                        targetFile
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    deliverGifToApp(contentUri, item.title, item.fallbackText);
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed downloading GIF: " + item.gifUrl, e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    insertText(item.fallbackText + " ");
                    Toast.makeText(WisprKeyboardService.this, "Could not load GIF. Sent: " + item.fallbackText, Toast.LENGTH_SHORT).show();
                    showPanel(PanelMode.KEYBOARD);
                });
            }
        }).start();
    }

    private void deliverGifToApp(Uri contentUri, String title, String fallbackText) {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (ic == null || contentUri == null) {
            insertText(fallbackText + " ");
            showPanel(PanelMode.KEYBOARD);
            return;
        }

        boolean isSupported = false;
        if (editorInfo != null) {
            String[] mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo);
            if (mimeTypes != null) {
                for (String mime : mimeTypes) {
                    if (mime.equalsIgnoreCase("image/gif") || mime.equalsIgnoreCase("image/*") || mime.startsWith("image/")) {
                        isSupported = true;
                        break;
                    }
                }
            }
        }

        if (isSupported) {
            try {
                InputContentInfoCompat contentInfo = new InputContentInfoCompat(
                        contentUri,
                        new ClipDescription(title, new String[]{"image/gif"}),
                        null
                );

                int flags = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
                }

                boolean committed = InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, flags, null);
                if (committed) {
                    Toast.makeText(this, "✓ GIF delivered!", Toast.LENGTH_SHORT).show();
                    showPanel(PanelMode.KEYBOARD);
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "commitContent failed: " + e.getMessage());
            }
        }

        // Target field does not accept direct commitContent rich image:
        // Copy the actual GIF image to clipboard so the user can easily paste it as an image,
        // and insert the clean emoji/title fallback instead of an ugly raw URL!
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newUri(getContentResolver(), title, contentUri);
                cm.setPrimaryClip(clip);
                Toast.makeText(this, "GIF image copied to clipboard! Tap Paste to send image.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {}

        showPanel(PanelMode.KEYBOARD);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        stopBackspaceRepeat();
        if (systemClipboard != null && clipListener != null) {
            try {
                systemClipboard.removePrimaryClipChangedListener(clipListener);
            } catch (Exception ignored) {}
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
        }
        if (suggestionEngine != null) {
            suggestionEngine.saveLearnedPatterns();
        }
        super.onDestroy();
    }
}
