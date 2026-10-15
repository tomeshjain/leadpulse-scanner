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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wispr Flow Professional Extended Android Keyboard (InputMethodService)
 * 
 * Features:
 * 1. Dedicated Top Suggestion Row with Next-Word Prediction, Collocations & Real-Time Spelling Suggestions.
 * 2. Dedicated Action Toolbar Row (🎙️ Voice, 📇 Scan Card, 🎨 Themes, 😊 Emoji, 🎞️ GIF, 📋 Clipboard, ⚡ Shortcuts, ✨ Mode, ⚙️ Settings).
 * 3. Clean Separation of Text and Numeric/Symbol Keyboards (with dedicated 10-key PIN Numpad).
 * 4. Contact Card Scanner: Extracts business card details using Gemini AI and sends text + image.
 * 5. Dynamic Themes (Midnight Slate, OLED Black, Clean Light, Cyberpunk Neon, Forest Emerald, Sunset Amber).
 * 6. Single high-visibility microphone button (no duplicates) with continuous digits formatting.
 * 7. Spacebar cursor scrubbing and selected text backspace deletion.
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
        THEMES
    }

    private KeyboardPreferences prefs;
    private ShortcutsManager shortcutsManager;
    private SuggestionEngine suggestionEngine;
    private ClipboardHistoryManager clipboardManager;
    private Vibrator vibrator;
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
    private LinearLayout keysContainer;
    private LinearLayout panelContainer;

    private Button btnShiftKey;
    private Button btnEnterKey;
    private Button modeChipButton;

    private final Handler backspaceHandler = new Handler(Looper.getMainLooper());
    private Runnable backspaceRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new KeyboardPreferences(this);
        shortcutsManager = new ShortcutsManager(this);
        suggestionEngine = new SuggestionEngine(this, shortcutsManager);
        clipboardManager = new ClipboardHistoryManager(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

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
        KeyboardTheme theme = KeyboardTheme.getTheme(prefs != null ? prefs.getThemeId() : null);
        keyboardRoot = new LinearLayout(this);
        keyboardRoot.setOrientation(LinearLayout.VERTICAL);
        keyboardRoot.setBackgroundColor(theme.backgroundColor);
        keyboardRoot.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(4));

        // 1. Two-Tier Header: Tier 1 (Suggestions) + Tier 2 (Action Toolbar)
        buildTwoTierToolbar();

        // 2. Main Key Rows Container
        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        keysContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        keyboardRoot.addView(keysContainer);

        // 3. Panel Container for Emojis, GIFs, Clipboard, Shortcuts, Themes
        panelContainer = new LinearLayout(this);
        panelContainer.setOrientation(LinearLayout.VERTICAL);
        panelContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(260)));
        panelContainer.setVisibility(View.GONE);
        keyboardRoot.addView(panelContainer);

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

        applyCurrentTheme();
        showPanel(PanelMode.KEYBOARD);
        captureSystemClipboard();
        updateEnterKeyAction(info);
        updateSuggestions();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
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

        // 🎙️ SEPARATE Dedicated Microphone Button with text
        Button btnVoice = new Button(this);
        btnVoice.setText("🎙️ Voice");
        btnVoice.setTextSize(11);
        btnVoice.setTextColor(Color.WHITE);
        btnVoice.setAllCaps(false);
        btnVoice.setBackground(createChipDrawable("#4338ca", "#6366f1", 12));
        btnVoice.setPadding(dpToPx(10), 0, dpToPx(10), 0);
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        voiceParams.setMargins(dpToPx(2), 0, dpToPx(4), 0);
        btnVoice.setLayoutParams(voiceParams);
        btnVoice.setOnClickListener(v -> toggleVoiceDictation());
        iconRow.addView(btnVoice);

        // 📇 Contact Card Scanner Button
        Button btnScanCard = new Button(this);
        btnScanCard.setText("📇 Scan Card");
        btnScanCard.setTextSize(11);
        btnScanCard.setTextColor(Color.WHITE);
        btnScanCard.setAllCaps(false);
        btnScanCard.setBackground(createChipDrawable("#065f46", "#10b981", 12));
        btnScanCard.setPadding(dpToPx(10), 0, dpToPx(10), 0);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        scanParams.setMargins(dpToPx(2), 0, dpToPx(4), 0);
        btnScanCard.setLayoutParams(scanParams);
        btnScanCard.setOnClickListener(v -> openCardScanner());
        iconRow.addView(btnScanCard);

        // 🎨 Themes Picker Button
        Button btnThemes = createToolbarIconButton("🎨", Color.TRANSPARENT);
        btnThemes.setOnClickListener(v -> togglePanel(PanelMode.THEMES));
        iconRow.addView(btnThemes);

        // 😊 Emojis Toggle
        Button btnEmoji = createToolbarIconButton("😊", Color.TRANSPARENT);
        btnEmoji.setOnClickListener(v -> togglePanel(PanelMode.EMOJI));
        iconRow.addView(btnEmoji);

        // 🎞️ GIFs & Reactions Toggle
        Button btnGifs = createToolbarIconButton("🎞️", Color.TRANSPARENT);
        btnGifs.setOnClickListener(v -> togglePanel(PanelMode.GIFS));
        iconRow.addView(btnGifs);

        // 📋 Clipboard & Pinned Favorites Toggle
        Button btnClip = createToolbarIconButton("📋", Color.TRANSPARENT);
        btnClip.setOnClickListener(v -> togglePanel(PanelMode.CLIPBOARD));
        iconRow.addView(btnClip);

        // ⚡ Shortcuts Toggle (GM, GE, etc.)
        Button btnShortcuts = createToolbarIconButton("⚡", Color.TRANSPARENT);
        btnShortcuts.setOnClickListener(v -> togglePanel(PanelMode.SHORTCUTS));
        iconRow.addView(btnShortcuts);

        // ✨ AI Flow Mode Chip
        modeChipButton = new Button(this);
        updateModeChipText();
        modeChipButton.setTextSize(10);
        modeChipButton.setTextColor(Color.parseColor("#c7d2fe"));
        modeChipButton.setAllCaps(false);
        modeChipButton.setBackground(createChipDrawable("#1e1b4b", "#6366f1", 12));
        modeChipButton.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        chipParams.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        modeChipButton.setLayoutParams(chipParams);
        modeChipButton.setOnClickListener(v -> cycleNextMode());
        iconRow.addView(modeChipButton);

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

    private void updateSuggestions() {
        if (suggestionsBar == null) return;
        suggestionsBar.removeAllViews();

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
                    lastCopiedSnippet = null;
                    updateSuggestions();
                }
            });
            suggestionsBar.addView(clipBtn);
        }

        // 2. Next-Word Prediction / Typing Pattern Suggestions
        String currentWord = currentWordBuffer.toString();
        List<String> suggestions = suggestionEngine.getSuggestions(lastCommittedWord, currentWord, 4);

        for (int i = 0; i < suggestions.size(); i++) {
            final String suggestion = suggestions.get(i);
            Button sugBtn = new Button(this);
            sugBtn.setText(suggestion);
            sugBtn.setTextColor(i == 0 ? Color.parseColor("#c7d2fe") : Color.WHITE);
            sugBtn.setTextSize(12);
            sugBtn.setAllCaps(false);
            sugBtn.setSingleLine(true);
            sugBtn.setGravity(Gravity.CENTER);

            // Highlight primary suggestion
            if (i == 0 && !currentWord.isEmpty()) {
                sugBtn.setBackground(createChipDrawable("#312e81", "#6366f1", 8));
            } else {
                sugBtn.setBackground(createChipDrawable("#141d2c", "#243247", 8));
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

        int len = currentWordBuffer.length();
        if (len > 0) {
            ic.deleteSurroundingText(len, 0);
        }

        ic.commitText(word + " ", 1);

        suggestionEngine.recordWordPair(lastCommittedWord, word);
        lastCommittedWord = word;
        currentWordBuffer.setLength(0);

        triggerHaptic(15);
        updateSuggestions();
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
                    buildGifsPanel();
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

    private void buildGifsPanel() {
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

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final LinearLayout gifContent = new LinearLayout(this);
        gifContent.setOrientation(LinearLayout.VERTICAL);
        gifContent.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        scroll.addView(gifContent);
        layout.addView(scroll);

        final String[] categories = GifReactionsData.getCategoryNames();
        for (int i = 0; i < categories.length; i++) {
            final String cat = categories[i];
            Button catBtn = new Button(this);
            catBtn.setText(cat);
            catBtn.setTextSize(11);
            catBtn.setTextColor(i == 0 ? Color.parseColor("#a5b4fc") : Color.parseColor("#94a3b8"));
            catBtn.setBackgroundColor(Color.TRANSPARENT);
            catBtn.setAllCaps(false);
            catBtn.setOnClickListener(v -> {
                renderGifs(gifContent, cat);
                for (int j = 0; j < tabs.getChildCount(); j++) {
                    View child = tabs.getChildAt(j);
                    if (child instanceof Button) {
                        ((Button) child).setTextColor(child == v ? Color.parseColor("#a5b4fc") : Color.parseColor("#94a3b8"));
                    }
                }
            });
            tabs.addView(catBtn);
        }

        renderGifs(gifContent, categories[0]);
        layout.addView(createPanelBottomBar());
        panelContainer.addView(layout);
    }

    private void renderGifs(LinearLayout container, String category) {
        container.removeAllViews();
        List<GifReactionsData.GifItem> items = GifReactionsData.getItemsForCategory(category);

        for (final GifReactionsData.GifItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(createChipDrawable("#141d2c", "#243247", 10));
            card.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dpToPx(6);
            card.setLayoutParams(cp);

            TextView tvEmoji = new TextView(this);
            tvEmoji.setText(item.emojiPreview);
            tvEmoji.setTextSize(18);
            card.addView(tvEmoji);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(item.title);
            tvTitle.setTextColor(Color.WHITE);
            tvTitle.setTextSize(13);
            tvTitle.setPadding(dpToPx(12), 0, 0, 0);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvTitle.setLayoutParams(tp);
            card.addView(tvTitle);

            card.setOnClickListener(v -> {
                String toInsert = (!item.gifUrl.isEmpty()) ? item.gifUrl : item.fallbackText;
                insertText(toInsert + " ");
                triggerHaptic(18);
            });

            container.addView(card);
        }
    }

    // --- CLIPBOARD & PINNED FAVORITES PANEL ---

    private void buildClipboardPanel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📋 Clipboard History & Pinned Snippets");
        tvTitle.setTextColor(Color.parseColor("#818cf8"));
        tvTitle.setTextSize(12);
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
        btnClear.setOnClickListener(v -> {
            clipboardManager.clearUnpinned();
            buildClipboardPanel();
            Toast.makeText(this, "Cleared unpinned clipboard history", Toast.LENGTH_SHORT).show();
        });
        header.addView(btnClear);
        layout.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
        scroll.addView(content);
        layout.addView(scroll);

        List<ClipboardHistoryManager.ClipItem> clips = clipboardManager.getAllClips();
        if (clips.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Clipboard is empty.\nCopy any text or pin snippets here!");
            empty.setTextColor(Color.parseColor("#64748b"));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dpToPx(40), 0, 0);
            content.addView(empty);
        } else {
            for (final ClipboardHistoryManager.ClipItem clip : clips) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setBackground(createChipDrawable(
                        clip.isPinned ? "#22194d" : "#141d2c",
                        clip.isPinned ? "#818cf8" : "#243247",
                        10));
                card.setPadding(dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.bottomMargin = dpToPx(6);
                card.setLayoutParams(cp);

                TextView tv = new TextView(this);
                tv.setText(clip.text);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(12);
                tv.setMaxLines(2);
                LinearLayout.LayoutParams txtParam = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                tv.setLayoutParams(txtParam);
                card.addView(tv);

                Button btnPin = new Button(this);
                btnPin.setText(clip.isPinned ? "📌" : "☆");
                btnPin.setTextSize(14);
                btnPin.setBackgroundColor(Color.TRANSPARENT);
                btnPin.setOnClickListener(v -> {
                    clipboardManager.togglePin(clip);
                    buildClipboardPanel();
                });
                card.addView(btnPin);

                Button btnDel = new Button(this);
                btnDel.setText("✕");
                btnDel.setTextSize(12);
                btnDel.setTextColor(Color.parseColor("#94a3b8"));
                btnDel.setBackgroundColor(Color.TRANSPARENT);
                btnDel.setOnClickListener(v -> {
                    clipboardManager.deleteClip(clip);
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
        for (final ShortcutsManager.ShortcutItem item : list) {
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

    private void renderAlphabetRows() {
        // Optional Dedicated Top Number Row (only if user explicitly turned it ON in settings)
        if (prefs != null && prefs.isShowNumberRow()) {
            String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
            LinearLayout numRow = new LinearLayout(this);
            numRow.setOrientation(LinearLayout.HORIZONTAL);
            numRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            numRow.setPadding(0, dpToPx(1), 0, dpToPx(1));

            for (final String num : numbers) {
                Button numBtn = createButton(num, true);
                numBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) numBtn.getLayoutParams();
                p.height = dpToPx(34);
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

        // Standard QWERTY rows
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

            if (r == 1) {
                rowLayout.setPadding(dpToPx(16), dpToPx(2), dpToPx(16), dpToPx(2));
            }

            for (String key : rowKeys) {
                rowLayout.addView(createKeyView(key));
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
        abcP.weight = 1.2f;
        abcBtn.setOnClickListener(v -> {
            isNumpadMode = false;
            isSymbolMode = false;
            renderKeyRows();
            triggerHaptic(15);
        });
        bottomRow.addView(abcBtn);

        Button symBtn = createButton("?#+", true);
        LinearLayout.LayoutParams symP = (LinearLayout.LayoutParams) symBtn.getLayoutParams();
        symP.weight = 1.0f;
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

        KeyboardTheme theme = KeyboardTheme.getTheme(prefs != null ? prefs.getThemeId() : null);
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

        // ?123 / ABC Mode Toggle
        Button numBtn = createButton(isSymbolMode ? "ABC" : "?123", true);
        LinearLayout.LayoutParams numParams = (LinearLayout.LayoutParams) numBtn.getLayoutParams();
        numParams.weight = 1.3f;
        numBtn.setOnClickListener(v -> {
            isSymbolMode = !isSymbolMode;
            isNumpadMode = false;
            renderKeyRows();
            triggerHaptic(15);
        });
        bottomRow.addView(numBtn);

        // If in symbol mode, add 123 PIN button to jump into dedicated PIN Numpad!
        if (isSymbolMode) {
            Button pinBtn = createButton("123 PIN", true);
            LinearLayout.LayoutParams pinParams = (LinearLayout.LayoutParams) pinBtn.getLayoutParams();
            pinParams.weight = 1.1f;
            pinBtn.setOnClickListener(v -> {
                isNumpadMode = true;
                renderKeyRows();
                triggerHaptic(15);
            });
            bottomRow.addView(pinBtn);
        }

        // Comma
        Button commaBtn = createButton(",", false);
        LinearLayout.LayoutParams commaParams = (LinearLayout.LayoutParams) commaBtn.getLayoutParams();
        commaParams.weight = 0.9f;
        commaBtn.setOnClickListener(v -> handlePunctuation(","));
        bottomRow.addView(commaBtn);

        // NORMAL CLEAN SPACEBAR WITH CURSOR SCRUBBING
        KeyboardTheme theme = KeyboardTheme.getTheme(prefs != null ? prefs.getThemeId() : null);
        final Button spaceBtn = new Button(this);
        spaceBtn.setText("English • Wispr Flow");
        spaceBtn.setTextColor(theme.subTextColor);
        spaceBtn.setTextSize(13);
        spaceBtn.setAllCaps(false);
        spaceBtn.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dpToPx(42), 4.8f);
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
        if ("shift".equals(key)) {
            String label = isCapsLocked ? "⇪" : (isShifted ? "⇧" : "⇧");
            btnShiftKey = createButton(label, true);

            if (isCapsLocked) {
                btnShiftKey.setBackground(createChipDrawable("#db2777", "#f472b6", 8));
                btnShiftKey.setTextColor(Color.WHITE);
            } else if (isShifted) {
                btnShiftKey.setBackground(createChipDrawable("#4338ca", "#818cf8", 8));
                btnShiftKey.setTextColor(Color.WHITE);
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
                renderKeyRows();
                triggerHaptic(15);
            });
            return btnShiftKey;
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
                                backspaceHandler.postDelayed(this, 50);
                            }
                        };
                        backspaceHandler.postDelayed(backspaceRunnable, 350);
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

        // Standard Character Key
        final String displayChar = (isShifted || isCapsLocked) ? key.toUpperCase(Locale.US) : key.toLowerCase(Locale.US);
        Button charBtn = createButton(displayChar, false);
        charBtn.setOnClickListener(v -> {
            insertText(displayChar);
            currentWordBuffer.append(displayChar);

            if (isShifted && !isCapsLocked) {
                isShifted = false;
                renderKeyRows();
            }

            updateSuggestions();
        });
        return charBtn;
    }

    private Button createButton(String text, boolean isFunctionKey) {
        KeyboardTheme theme = KeyboardTheme.getTheme(prefs != null ? prefs.getThemeId() : null);
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

    // --- TEXT INSERTION & BACKSPACE (CRITICAL BUG FIX FOR SELECTED TEXT) ---

    private void insertText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            triggerHaptic(12);
        }
    }

    /**
     * Handles backspace correctly for BOTH highlighted/selected text AND standard character deletion.
     */
    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

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

        updateSuggestions();
        triggerHaptic(12);
    }

    private void handleSpaceKey() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        String currentWord = currentWordBuffer.toString().trim();

        // Check if the typed word is a shortcut trigger (e.g. "GM" -> "Good Morning")
        String expansion = shortcutsManager.getExpansion(currentWord);
        if (expansion != null) {
            ic.deleteSurroundingText(currentWordBuffer.length(), 0);
            ic.commitText(expansion + " ", 1);

            suggestionEngine.recordWordPair(lastCommittedWord, expansion);
            lastCommittedWord = expansion;
        } else {
            ic.commitText(" ", 1);
            if (!currentWord.isEmpty()) {
                suggestionEngine.recordWordPair(lastCommittedWord, currentWord);
                lastCommittedWord = currentWord;
            }
        }

        currentWordBuffer.setLength(0);
        triggerHaptic(12);
        updateSuggestions();
    }

    private void handlePunctuation(String punc) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        String currentWord = currentWordBuffer.toString().trim();
        String expansion = shortcutsManager.getExpansion(currentWord);
        if (expansion != null) {
            ic.deleteSurroundingText(currentWordBuffer.length(), 0);
            ic.commitText(expansion + punc + " ", 1);
            suggestionEngine.recordWordPair(lastCommittedWord, expansion);
            lastCommittedWord = expansion;
        } else {
            ic.commitText(punc + " ", 1);
            if (!currentWord.isEmpty()) {
                suggestionEngine.recordWordPair(lastCommittedWord, currentWord);
                lastCommittedWord = currentWord;
            }
        }

        currentWordBuffer.setLength(0);
        triggerHaptic(12);
        updateSuggestions();
    }

    private void handleEnterKey() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

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
        }

        currentWordBuffer.setLength(0);
        triggerHaptic(20);
        updateSuggestions();
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

        GeminiFlowClient.polishSpeech(rawText, apiKey, model, mode, new GeminiFlowClient.PolishCallback() {
            @Override
            public void onSuccess(String polishedText, long latencyMs) {
                // Collapse any remaining spoken spaced digits (e.g. OTPs, phone numbers)
                String formatted = GeminiFlowClient.collapseSpokenDigits(polishedText);

                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !formatted.isEmpty()) {
                    ic.commitText(formatted + " ", 1);
                    triggerHaptic(new long[]{20, 40});
                }
            }

            @Override
            public void onError(String errorMessage, String fallbackText) {
                String formatted = GeminiFlowClient.collapseSpokenDigits(fallbackText);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !formatted.isEmpty()) {
                    ic.commitText(formatted + " ", 1);
                }
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
        KeyboardTheme theme = KeyboardTheme.getTheme(prefs != null ? prefs.getThemeId() : null);
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
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
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
