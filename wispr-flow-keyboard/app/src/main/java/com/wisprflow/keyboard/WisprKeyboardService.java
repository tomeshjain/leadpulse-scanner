package com.wisprflow.keyboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wispr Flow Professional Extended Android Keyboard (InputMethodService)
 * 
 * Comprehensive Features:
 * 1. Clean, state-of-the-art QWERTY keyboard with Caps Lock, punctuation, and dynamic action keys.
 * 2. Next-Word Predictions & Collocated Word Learning (learns user's typing patterns over time).
 * 3. Text Shortcuts (e.g. GM -> Good Morning) with auto-suggestions and inline expansion.
 * 4. Separate dedicated 🎙️ Voice Microphone (no hijacking of normal spacebar).
 * 5. Gemini 2.0 Flash AI Voice Dictation & Text Polishing with offline rule-based fallback.
 * 6. Categorized Emoji Panel (Smileys, Gestures, Hearts, Activities, Food, Work).
 * 7. GIFs & Reaction Stickers (Kaomoji and express reactions).
 * 8. Clipboard Manager with Recent History and 📌 Pinned Favorites.
 * 9. Quick ⚙️ Settings button for instant access to API key and model selection.
 */
public class WisprKeyboardService extends InputMethodService {

    private static final String TAG = "WisprKeyboardService";

    // Panel View Modes
    private enum PanelMode {
        KEYBOARD,
        EMOJI,
        GIFS,
        CLIPBOARD,
        SHORTCUTS
    }

    private KeyboardPreferences prefs;
    private ShortcutsManager shortcutsManager;
    private SuggestionEngine suggestionEngine;
    private ClipboardHistoryManager clipboardManager;
    private Vibrator vibrator;
    private SpeechRecognizer speechRecognizer;

    private PanelMode currentPanel = PanelMode.KEYBOARD;
    private boolean isListening = false;
    private boolean isShifted = false;
    private boolean isCapsLocked = false;
    private boolean isSymbolMode = false;
    private long lastShiftClickTime = 0;

    // Typing tracking for Suggestions & Collocations
    private final StringBuilder currentWordBuffer = new StringBuilder();
    private String lastCommittedWord = "";

    // UI Components
    private LinearLayout keyboardRoot;
    private LinearLayout toolbarContainer;
    private LinearLayout suggestionsBar;
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
    }

    @Override
    public View onCreateInputView() {
        keyboardRoot = new LinearLayout(this);
        keyboardRoot.setOrientation(LinearLayout.VERTICAL);
        keyboardRoot.setBackgroundColor(Color.parseColor("#090d16"));
        keyboardRoot.setPadding(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(6));

        // 1. Build Top Smart Toolbar (Suggestions + Quick Action Buttons)
        buildToolbar();

        // 2. Main Content Container (Holds either Key Rows or Active Panel like Emojis/Clipboard)
        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        keysContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        keyboardRoot.addView(keysContainer);

        panelContainer = new LinearLayout(this);
        panelContainer.setOrientation(LinearLayout.VERTICAL);
        panelContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(245)));
        panelContainer.setVisibility(View.GONE);
        keyboardRoot.addView(panelContainer);

        // Render initial view
        showPanel(PanelMode.KEYBOARD);

        // Initialize Speech Recognizer
        initSpeechRecognizer();

        // Update suggestions for initial empty state
        updateSuggestions();

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
        showPanel(PanelMode.KEYBOARD);

        // Check clipboard for any new external clips copied by user
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

    // --- TOOLBAR & SMART ACTIONS ---

    private void buildToolbar() {
        toolbarContainer = new LinearLayout(this);
        toolbarContainer.setOrientation(LinearLayout.VERTICAL);
        toolbarContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        toolbarContainer.setBackgroundColor(Color.parseColor("#0d1424"));
        toolbarContainer.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(3));

        // Row A: Suggestions Strip & Quick Action Icons
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40)));

        // Suggestions Area (takes dynamic remaining space)
        suggestionsBar = new LinearLayout(this);
        suggestionsBar.setOrientation(LinearLayout.HORIZONTAL);
        suggestionsBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sugParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        suggestionsBar.setLayoutParams(sugParams);
        actionRow.addView(suggestionsBar);

        // Quick Action Buttons Row (Mic, Emoji, GIF, Clipboard, Shortcuts, Mode, Settings)
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        hsv.addView(iconRow);

        // 🎙️ SEPARATE Dedicated Microphone Button
        Button btnMic = createToolbarIconButton("🎙️", Color.parseColor("#4338ca"));
        btnMic.setOnClickListener(v -> toggleVoiceDictation());
        iconRow.addView(btnMic);

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
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(28));
        chipParams.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        modeChipButton.setLayoutParams(chipParams);
        modeChipButton.setOnClickListener(v -> cycleNextMode());
        iconRow.addView(modeChipButton);

        // ⚙️ Settings Button (Launches SettingsActivity)
        Button btnSettings = createToolbarIconButton("⚙️", Color.TRANSPARENT);
        btnSettings.setOnClickListener(v -> openSettingsActivity());
        iconRow.addView(btnSettings);

        actionRow.addView(hsv);
        toolbarContainer.addView(actionRow);

        // Row B: Voice Listening Banner (Visible only while speaking)
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

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(34), dpToPx(34));
        params.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    // --- SMART SUGGESTIONS & COLLOCATIONS ---

    private void updateSuggestions() {
        if (suggestionsBar == null) return;
        suggestionsBar.removeAllViews();

        String currentWord = currentWordBuffer.toString();
        List<String> suggestions = suggestionEngine.getSuggestions(lastCommittedWord, currentWord, 3);

        for (int i = 0; i < suggestions.size(); i++) {
            final String suggestion = suggestions.get(i);
            Button sugBtn = new Button(this);
            sugBtn.setText(suggestion);
            sugBtn.setTextColor(i == 0 ? Color.parseColor("#a5b4fc") : Color.WHITE);
            sugBtn.setTextSize(12);
            sugBtn.setAllCaps(false);
            sugBtn.setSingleLine(true);
            sugBtn.setGravity(Gravity.CENTER);

            // Highlight first suggestion
            if (i == 0 && !currentWord.isEmpty()) {
                sugBtn.setBackground(createChipDrawable("#312e81", "#6366f1", 8));
            } else {
                sugBtn.setBackground(createChipDrawable("#162032", "#23334d", 8));
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

        // If user was typing a partial prefix, delete that prefix first
        int len = currentWordBuffer.length();
        if (len > 0) {
            ic.deleteSurroundingText(len, 0);
        }

        // Commit the selected suggestion + trailing space
        ic.commitText(word + " ", 1);

        // Learn user collocation
        suggestionEngine.recordWordPair(lastCommittedWord, word);
        lastCommittedWord = word;
        currentWordBuffer.setLength(0);

        triggerHaptic(15);
        updateSuggestions();
    }

    // --- PANEL NAVIGATION (KEYBOARD, EMOJI, GIF, CLIPBOARD, SHORTCUTS) ---

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

        // Category Tab Bar
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(38)));

        final LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(tabs);
        layout.addView(tabScroll);

        // Emoji Grid Container inside ScrollView
        ScrollView emojiScroll = new ScrollView(this);
        emojiScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final LinearLayout emojiContent = new LinearLayout(this);
        emojiContent.setOrientation(LinearLayout.VERTICAL);
        emojiContent.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        emojiScroll.addView(emojiContent);
        layout.addView(emojiScroll);

        // Populate Category Tabs
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

        // Render first category
        renderEmojis(emojiContent, categories[0]);

        // Bottom Action Bar (ABC Return + Backspace)
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

        // Category Tab Bar
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
                // If it has a direct GIF URL or fallback text, insert it
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

        // Header controls (Clear Unpinned History button)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📋 Clipboard & Pinned Snippets");
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

        // Clips List inside ScrollView
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

                // Clip text
                TextView tv = new TextView(this);
                tv.setText(clip.text);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(12);
                tv.setMaxLines(2);
                LinearLayout.LayoutParams txtParam = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                tv.setLayoutParams(txtParam);
                card.addView(tv);

                // Pin / Unpin Button
                Button btnPin = new Button(this);
                btnPin.setText(clip.isPinned ? "📌" : "☆");
                btnPin.setTextSize(14);
                btnPin.setBackgroundColor(Color.TRANSPARENT);
                btnPin.setOnClickListener(v -> {
                    clipboardManager.togglePin(clip);
                    buildClipboardPanel();
                });
                card.addView(btnPin);

                // Delete Button
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

                // 1-Tap Paste
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

        // Header
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
        btnManage.setText("+ Add / Edit in Settings");
        btnManage.setTextSize(11);
        btnManage.setTextColor(Color.parseColor("#a5b4fc"));
        btnManage.setBackgroundColor(Color.TRANSPARENT);
        btnManage.setAllCaps(false);
        btnManage.setOnClickListener(v -> openSettingsActivity());
        header.addView(btnManage);
        layout.addView(header);

        // List inside ScrollView
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
        bar.setBackgroundColor(Color.parseColor("#090d16"));
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
        btnBs.setOnClickListener(v -> {
            handleBackspace();
            triggerHaptic(12);
        });
        bar.addView(btnBs);

        return bar;
    }

    // --- MAIN QWERTY & KEY ROWS ---

    private void renderKeyRows() {
        if (keysContainer == null) return;
        keysContainer.removeAllViews();

        if (isSymbolMode) {
            renderSymbolRows();
        } else {
            renderAlphabetRows();
        }

        // Clean Bottom Spacebar Row (with Separate Normal Spacebar + Separate Mic Button)
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

            if (r == 1) {
                // Natural key stagger for middle row
                rowLayout.setPadding(dpToPx(16), dpToPx(2), dpToPx(16), dpToPx(2));
            }

            for (String key : rowKeys) {
                rowLayout.addView(createKeyView(key));
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
                rowLayout.addView(createKeyView(key));
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

        // ?123 / ABC Mode Toggle
        Button numBtn = createButton(isSymbolMode ? "ABC" : "?123", true);
        LinearLayout.LayoutParams numParams = (LinearLayout.LayoutParams) numBtn.getLayoutParams();
        numParams.weight = 1.3f;
        numBtn.setOnClickListener(v -> {
            isSymbolMode = !isSymbolMode;
            renderKeyRows();
            triggerHaptic(15);
        });
        bottomRow.addView(numBtn);

        // Emoji Shortcut Key
        Button emojiBtn = createButton("😊", true);
        LinearLayout.LayoutParams emParams = (LinearLayout.LayoutParams) emojiBtn.getLayoutParams();
        emParams.weight = 1.0f;
        emojiBtn.setOnClickListener(v -> togglePanel(PanelMode.EMOJI));
        bottomRow.addView(emojiBtn);

        // Comma
        Button commaBtn = createButton(",", false);
        LinearLayout.LayoutParams commaParams = (LinearLayout.LayoutParams) commaBtn.getLayoutParams();
        commaParams.weight = 0.9f;
        commaBtn.setOnClickListener(v -> handlePunctuation(","));
        bottomRow.addView(commaBtn);

        // NORMAL CLEAN SPACEBAR (Inserts regular space when pressed)
        Button spaceBtn = new Button(this);
        spaceBtn.setText("Wispr Flow");
        spaceBtn.setTextColor(Color.parseColor("#94a3b8"));
        spaceBtn.setTextSize(13);
        spaceBtn.setAllCaps(false);
        spaceBtn.setBackground(createChipDrawable("#162032", "#23334d", 8));
        LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dpToPx(42), 4.5f);
        spaceParams.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        spaceBtn.setLayoutParams(spaceParams);

        spaceBtn.setOnClickListener(v -> handleSpaceKey());
        bottomRow.addView(spaceBtn);

        // Period
        Button dotBtn = createButton(".", false);
        LinearLayout.LayoutParams dotParams = (LinearLayout.LayoutParams) dotBtn.getLayoutParams();
        dotParams.weight = 0.9f;
        dotBtn.setOnClickListener(v -> handlePunctuation("."));
        bottomRow.addView(dotBtn);

        // SEPARATE Dedicated Microphone Key in bottom row
        Button micKey = createButton("🎙️", true);
        micKey.setTextColor(Color.parseColor("#a5b4fc"));
        micKey.setBackground(createChipDrawable("#2e1065", "#7c3aed", 8));
        LinearLayout.LayoutParams micParams = (LinearLayout.LayoutParams) micKey.getLayoutParams();
        micParams.weight = 1.0f;
        micKey.setOnClickListener(v -> toggleVoiceDictation());
        bottomRow.addView(micKey);

        // Enter / Action Key
        btnEnterKey = createButton("⏎", true);
        btnEnterKey.setTextColor(Color.WHITE);
        btnEnterKey.setBackground(createChipDrawable("#4338ca", "#6366f1", 8));
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

            // Visual highlight when Shift or Caps Lock is active
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
                // Double tap detects Caps Lock
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
                                backspaceHandler.postDelayed(this, 60);
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

            // Revert shift if not caps locked
            if (isShifted && !isCapsLocked) {
                isShifted = false;
                renderKeyRows();
            }

            updateSuggestions();
        });
        return charBtn;
    }

    private Button createButton(String text, boolean isFunctionKey) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isFunctionKey ? 14 : 17);
        btn.setAllCaps(false);
        btn.setBackground(createChipDrawable(isFunctionKey ? "#1a2538" : "#141d2c", "#243247", 8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(42), 1.0f);
        params.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private GradientDrawable createChipDrawable(String fillColor, String strokeColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(Color.parseColor(fillColor));
        if (strokeColor != null) {
            gd.setStroke(dpToPx(1), Color.parseColor(strokeColor));
        }
        gd.setCornerRadius(dpToPx(radiusDp));
        return gd;
    }

    // --- TEXT INSERTION & SHORTCUT EXPANSION ---

    private void insertText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            triggerHaptic(12);
        }
    }

    private void handleSpaceKey() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        String currentWord = currentWordBuffer.toString().trim();

        // 1. Check if the typed word is a shortcut trigger (e.g. "GM" -> "Good Morning")
        String expansion = shortcutsManager.getExpansion(currentWord);
        if (expansion != null) {
            // Delete the trigger characters typed so far
            ic.deleteSurroundingText(currentWordBuffer.length(), 0);
            ic.commitText(expansion + " ", 1);

            suggestionEngine.recordWordPair(lastCommittedWord, expansion);
            lastCommittedWord = expansion;
        } else {
            // Normal space insertion
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

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
            if (currentWordBuffer.length() > 0) {
                currentWordBuffer.deleteCharAt(currentWordBuffer.length() - 1);
            }
            updateSuggestions();
        }
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
            ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
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
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
            if (text != null && text.length() > 0) {
                clipboardManager.addClip(text.toString());
            }
        }
    }

    // --- SEPARATE DEDICATED VOICE DICTATION & GEMINI AI POLISHER ---

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

        // Verify microphone permission
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

        // Show Voice Banner
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
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !polishedText.isEmpty()) {
                    ic.commitText(polishedText + " ", 1);
                    triggerHaptic(new long[]{20, 40});
                }
            }

            @Override
            public void onError(String errorMessage, String fallbackText) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && !fallbackText.isEmpty()) {
                    ic.commitText(fallbackText + " ", 1);
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

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
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
