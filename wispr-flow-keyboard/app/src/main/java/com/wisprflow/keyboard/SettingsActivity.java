package com.wisprflow.keyboard;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Settings and Setup Activity for Smart AI Keyboard.
 * - System setup (Enabling and selecting IME)
 * - Microphone permissions management
 * - Gemini API key and model selection
 * - Appearance & theme switcher with dynamic logo and UI skinning
 * - Meeting action items and summary notes manager
 * - Custom text shortcuts manager (e.g. GM -> Good Morning)
 * - Typing pattern and collocation statistics
 * - Dedicated Contact Card Scanner launcher
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_MIC_PERMISSION = 101;

    private KeyboardPreferences prefs;
    private ShortcutsManager shortcutsManager;
    private SuggestionEngine suggestionEngine;

    private ScrollView settingsScrollView;
    private ImageView ivHeaderLogo;
    private TextView tvBrandTitle;
    private TextView tvBrandSubtitle;

    private LinearLayout cardActivation;
    private LinearLayout cardMic;
    private LinearLayout cardGemini;
    private LinearLayout cardAppearance;
    private LinearLayout cardScanner;
    private LinearLayout cardActionItems;
    private LinearLayout cardShortcuts;
    private LinearLayout cardPatterns;
    private LinearLayout cardHowItWorks;
    private LinearLayout cardAiDiagnostics;

    private Button btnEnableKeyboard;
    private Button btnSelectKeyboard;
    private TextView tvActivationStatus;

    private TextView tvMicStatus;
    private Button btnGrantMicPermission;

    private EditText etApiKey;
    private Button btnSaveApiKey;
    private Spinner spinnerModel;
    private Spinner spinnerMode;
    private Spinner spinnerTheme;
    private CheckBox cbShowNumberRow;

    private Button btnLaunchScanner;
    private TextView tvActionItemsBadge;
    private EditText etNewActionItem;
    private Button btnAddActionItem;
    private LinearLayout llActionItemsList;
    private Button btnClearCompletedActions;
    private Button btnViewMeetingSummaries;

    private LinearLayout llShortcutsList;
    private Button btnAddShortcut;
    private Button btnToggleShortcutsExpander;
    private boolean isShortcutsListExpanded = false;
    private TextView tvPatternStats;
    private Button btnResetPatterns;
    private Button btnTestPureAiVoice;
    private Button btnTestPureAiCard;
    private Button btnTestLiveWs;
    private TextView tvPureAiTestResult;

    private ThemedSpinnerAdapter modelAdapter;
    private ThemedSpinnerAdapter modeAdapter;
    private ThemedSpinnerAdapter themeAdapter;
    private List<String> currentModels;
    private KeyboardTheme currentActiveTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new KeyboardPreferences(this);
        shortcutsManager = new ShortcutsManager(this);
        suggestionEngine = new SuggestionEngine(this, shortcutsManager);

        initViews();
        setupListeners();
        populateData();

        // Apply current theme (defaults to Clean Light or OS mode)
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs.getThemeId());
        applyTheme(theme);
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs.getThemeId());
        applyTheme(theme);
        checkActivationState(theme);
        checkMicPermission(theme);
        updatePatternStats(theme);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // If user is using System Auto theme, refresh immediately on OS dark/light switch
        KeyboardTheme theme = KeyboardTheme.resolveTheme(this, prefs.getThemeId());
        applyTheme(theme);
    }

    private void initViews() {
        settingsScrollView = findViewById(R.id.settingsScrollView);
        ivHeaderLogo = findViewById(R.id.ivHeaderLogo);
        tvBrandTitle = findViewById(R.id.tvBrandTitle);
        tvBrandSubtitle = findViewById(R.id.tvBrandSubtitle);

        cardActivation = findViewById(R.id.cardActivation);
        cardMic = findViewById(R.id.cardMic);
        cardGemini = findViewById(R.id.cardGemini);
        cardAppearance = findViewById(R.id.cardAppearance);
        cardScanner = findViewById(R.id.cardScanner);
        cardActionItems = findViewById(R.id.cardActionItems);
        cardShortcuts = findViewById(R.id.cardShortcuts);
        cardPatterns = findViewById(R.id.cardPatterns);
        cardHowItWorks = findViewById(R.id.cardHowItWorks);
        cardAiDiagnostics = findViewById(R.id.cardAiDiagnostics);

        btnEnableKeyboard = findViewById(R.id.btnEnableKeyboard);
        btnSelectKeyboard = findViewById(R.id.btnSelectKeyboard);
        tvActivationStatus = findViewById(R.id.tvActivationStatus);

        tvMicStatus = findViewById(R.id.tvMicStatus);
        btnGrantMicPermission = findViewById(R.id.btnGrantMicPermission);

        etApiKey = findViewById(R.id.etApiKey);
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
        spinnerModel = findViewById(R.id.spinnerModel);
        spinnerMode = findViewById(R.id.spinnerMode);
        spinnerTheme = findViewById(R.id.spinnerTheme);
        cbShowNumberRow = findViewById(R.id.cbShowNumberRow);

        btnLaunchScanner = findViewById(R.id.btnLaunchScanner);
        tvActionItemsBadge = findViewById(R.id.tvActionItemsBadge);
        etNewActionItem = findViewById(R.id.etNewActionItem);
        btnAddActionItem = findViewById(R.id.btnAddActionItem);
        llActionItemsList = findViewById(R.id.llActionItemsList);
        btnClearCompletedActions = findViewById(R.id.btnClearCompletedActions);
        btnViewMeetingSummaries = findViewById(R.id.btnViewMeetingSummaries);

        llShortcutsList = findViewById(R.id.llShortcutsList);
        btnAddShortcut = findViewById(R.id.btnAddShortcut);
        btnToggleShortcutsExpander = findViewById(R.id.btnToggleShortcutsExpander);
        tvPatternStats = findViewById(R.id.tvPatternStats);
        btnResetPatterns = findViewById(R.id.btnResetPatterns);

        btnTestPureAiVoice = findViewById(R.id.btnTestPureAiVoice);
        btnTestPureAiCard = findViewById(R.id.btnTestPureAiCard);
        btnTestLiveWs = findViewById(R.id.btnTestLiveWs);
        tvPureAiTestResult = findViewById(R.id.tvPureAiTestResult);
    }

    private void setupListeners() {
        // Step 1: Open System Settings to Enable Wispr Flow
        if (btnEnableKeyboard != null) {
            btnEnableKeyboard.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Toast.makeText(this, "Enable 'Smart AI Keyboard' in the list", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Step 2: Switch Active Keyboard
        if (btnSelectKeyboard != null) {
            btnSelectKeyboard.setOnClickListener(v -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            });
        }

        // Launch Contact Card Scanner
        if (btnLaunchScanner != null) {
            btnLaunchScanner.setOnClickListener(v -> {
                Intent intent = new Intent(this, CardScannerActivity.class);
                startActivity(intent);
            });
        }

        // Toggle Number Row
        if (cbShowNumberRow != null) {
            cbShowNumberRow.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.setShowNumberRow(isChecked);
                Toast.makeText(this, isChecked ? "Permanent Number Row enabled" : "Text & Numbers separated (Default)", Toast.LENGTH_SHORT).show();
            });
        }

        // Microphone Permission Request
        if (btnGrantMicPermission != null) {
            btnGrantMicPermission.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission is already granted!", Toast.LENGTH_SHORT).show();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERMISSION);
                }
            });
        }

        // Save API Key & Fetch Models
        if (btnSaveApiKey != null) {
            btnSaveApiKey.setOnClickListener(v -> {
                String apiKey = etApiKey.getText().toString().trim();
                if (apiKey.isEmpty()) {
                    prefs.setApiKey("");
                    Toast.makeText(this, "Using built-in offline rule-based polishing engine.", Toast.LENGTH_SHORT).show();
                    return;
                }

                prefs.setApiKey(apiKey);
                btnSaveApiKey.setEnabled(false);
                btnSaveApiKey.setText("⏳ Fetching Available Models...");

                GeminiFlowClient.fetchAvailableModels(apiKey, new GeminiFlowClient.ModelsCallback() {
                    @Override
                    public void onSuccess(List<String> models) {
                        runOnUiThread(() -> {
                            btnSaveApiKey.setEnabled(true);
                            btnSaveApiKey.setText("Save API Key & Fetch Available Models");
                            if (models != null && !models.isEmpty()) {
                                prefs.setAvailableModels(models);
                                updateModelsSpinner(models);
                                Toast.makeText(SettingsActivity.this, "✓ Connected! Found " + models.size() + " models.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(SettingsActivity.this, "✓ API Key saved successfully.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            btnSaveApiKey.setEnabled(true);
                            btnSaveApiKey.setText("Save API Key & Fetch Available Models");
                            Toast.makeText(SettingsActivity.this, "Saved! Note: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        }

        // Add Shortcut Dialog
        if (btnAddShortcut != null) {
            btnAddShortcut.setOnClickListener(v -> showAddShortcutDialog());
        }

        // Reset Learned Patterns
        if (btnResetPatterns != null) {
            btnResetPatterns.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Reset Learned Patterns?")
                    .setMessage("This will clear learned user frequencies and bigram collocations, restoring default vocabulary.")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        suggestionEngine.resetLearnedPatterns();
                        updatePatternStats(currentActiveTheme);
                        Toast.makeText(this, "Typing pattern history reset.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        // Shortcuts Expander Toggle
        if (btnToggleShortcutsExpander != null) {
            btnToggleShortcutsExpander.setOnClickListener(v -> {
                isShortcutsListExpanded = !isShortcutsListExpanded;
                renderShortcutsList(currentActiveTheme);
            });
        }

        // Action Items: Add Task
        if (btnAddActionItem != null) {
            btnAddActionItem.setOnClickListener(v -> {
                String text = etNewActionItem != null ? etNewActionItem.getText().toString().trim() : "";
                if (!text.isEmpty()) {
                    ActionItemsManager.getInstance(this).addCustomActionItem(text, "", "");
                    etNewActionItem.setText("");
                    renderActionItemsList(currentActiveTheme);
                    Toast.makeText(this, "✓ Action item added!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Please enter a task description", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Action Items: Clear Completed Tasks
        if (btnClearCompletedActions != null) {
            btnClearCompletedActions.setOnClickListener(v -> {
                ActionItemsManager.getInstance(this).clearCompleted();
                renderActionItemsList(currentActiveTheme);
                Toast.makeText(this, "Completed action items cleared.", Toast.LENGTH_SHORT).show();
            });
        }

        // Action Items: View Past Meeting Summaries Dialog
        if (btnViewMeetingSummaries != null) {
            btnViewMeetingSummaries.setOnClickListener(v -> showMeetingSummariesDialog());
        }

        // Test Pure AI Voice (Strict No-Fallback)
        if (btnTestPureAiVoice != null) {
            btnTestPureAiVoice.setOnClickListener(v -> {
                String apiKey = prefs.getApiKey();
                if (apiKey.isEmpty()) {
                    tvPureAiTestResult.setText("❌ Error: API Key is required for Pure AI testing. Enter your key in Step 3.");
                    return;
                }
                String model = prefs.getSelectedModel();
                btnTestPureAiVoice.setEnabled(false);
                tvPureAiTestResult.setText("⏳ Testing Pure AI Voice with model " + model + " (Strict, no fallback)...");

                String sample = "um hello team uh please find the quarterly report attached and let me know your thoughts";
                GeminiFlowClient.testPureAiVoice(sample, apiKey, model, new GeminiFlowClient.StrictAiCallback() {
                    @Override
                    public void onSuccess(String result, long latencyMs, String modelUsed) {
                        runOnUiThread(() -> {
                            btnTestPureAiVoice.setEnabled(true);
                            tvPureAiTestResult.setText("🟢 Pure AI Voice Test: SUCCESS (" + latencyMs + "ms)\n\n" +
                                    "Model: " + modelUsed + "\n" +
                                    "Input: \"" + sample + "\"\n\n" +
                                    "Polished Output:\n" + result);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            btnTestPureAiVoice.setEnabled(true);
                            tvPureAiTestResult.setText("❌ Pure AI Voice Test FAILED:\n" + errorMessage);
                        });
                    }
                });
            });
        }

        // Test Pure AI Contact Card Extraction (Strict 6 Fields)
        if (btnTestPureAiCard != null) {
            btnTestPureAiCard.setOnClickListener(v -> {
                String apiKey = prefs.getApiKey();
                if (apiKey.isEmpty()) {
                    tvPureAiTestResult.setText("❌ Error: API Key is required for Pure AI testing. Enter your key in Step 3.");
                    return;
                }
                String model = prefs.getSelectedModel();
                btnTestPureAiCard.setEnabled(false);
                tvPureAiTestResult.setText("⏳ Testing Pure AI Contact Card Extraction (Strict 6 Fields)...");

                String mockCardText = "Sarah Jenkins\nVP of Enterprise Growth\nApex Cloud Solutions\nsarah.jenkins@apexcloud.io\nDirect: +1 415-555-0198\nMarket Street, San Francisco, CA";
                GeminiFlowClient.formatExtractedCardText(mockCardText, apiKey, model, new GeminiFlowClient.CardScanResultCallback() {
                    @Override
                    public void onSuccess(String formattedContactCard, long latencyMs, String modelUsed, boolean isFallback) {
                        runOnUiThread(() -> {
                            btnTestPureAiCard.setEnabled(true);
                            tvPureAiTestResult.setText("🟢 Pure AI Contact Card Test: SUCCESS (" + latencyMs + "ms)\n\n" +
                                    "Model: " + modelUsed + (isFallback ? " (Fallback Model)" : " (Primary AI Model)") + "\n\n" +
                                    "Strict 6-Field Output (No N/A):\n" + formattedContactCard);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            btnTestPureAiCard.setEnabled(true);
                            tvPureAiTestResult.setText("❌ Pure AI Card Extraction FAILED:\n" + errorMessage);
                        });
                    }
                });
            });
        }

        // Test Live WebSocket (wss://)
        if (btnTestLiveWs != null) {
            btnTestLiveWs.setOnClickListener(v -> {
                String apiKey = prefs.getApiKey();
                if (apiKey.isEmpty()) {
                    tvPureAiTestResult.setText("❌ Error: API Key is required for WebSocket testing. Enter your key in Step 3.");
                    return;
                }
                btnTestLiveWs.setEnabled(false);
                tvPureAiTestResult.setText("⏳ Initiating Gemini Multimodal Live WebSocket handshake (wss://generativelanguage.googleapis.com)...");

                GeminiLiveWebSocketClient.testConnection(apiKey, new GeminiLiveWebSocketClient.TestCallback() {
                    @Override
                    public void onResult(boolean success, String message) {
                        runOnUiThread(() -> {
                            btnTestLiveWs.setEnabled(true);
                            if (success) {
                                tvPureAiTestResult.setText("🟢 Gemini Live WebSocket: CONNECTED\n\n" +
                                        "Protocol: wss:// Bidirectional Multimodal Streaming\n" +
                                        "Target: Google GenerativeService.BidiGenerateContent\n\n" +
                                        message);
                            } else {
                                tvPureAiTestResult.setText("❌ Gemini Live WebSocket Handshake FAILED:\n\n" + message);
                            }
                        });
                    }
                });
            });
        }
    }

    private void populateData() {
        KeyboardTheme initialTheme = KeyboardTheme.resolveTheme(this, prefs.getThemeId());

        // API Key
        etApiKey.setText(prefs.getApiKey());

        // Models Spinner
        currentModels = prefs.getAvailableModels();
        modelAdapter = new ThemedSpinnerAdapter(this, currentModels, initialTheme);
        spinnerModel.setAdapter(modelAdapter);

        String selectedModel = prefs.getSelectedModel();
        int modelIdx = currentModels.indexOf(selectedModel);
        if (modelIdx >= 0) {
            spinnerModel.setSelection(modelIdx);
        }

        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < currentModels.size()) {
                    prefs.setSelectedModel(currentModels.get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Modes Spinner
        List<String> modeNames = new ArrayList<>();
        final FlowMode[] modes = FlowMode.values();
        int selectedModeIdx = 0;
        String curModeId = prefs.getFlowMode();

        for (int i = 0; i < modes.length; i++) {
            modeNames.add(modes[i].getDisplayName() + " - " + modes[i].getDescription());
            if (modes[i].getId().equalsIgnoreCase(curModeId)) {
                selectedModeIdx = i;
            }
        }

        modeAdapter = new ThemedSpinnerAdapter(this, modeNames, initialTheme);
        spinnerMode.setAdapter(modeAdapter);
        spinnerMode.setSelection(selectedModeIdx);

        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < modes.length) {
                    prefs.setFlowMode(modes[position].getId());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Theme Spinner with Live Dynamic Theme Updating
        final List<KeyboardTheme> allThemes = KeyboardTheme.getAllThemes();
        List<String> themeDisplayNames = new ArrayList<>();
        int selectedThemeIdx = 0;
        String curThemeId = prefs.getThemeId();

        for (int i = 0; i < allThemes.size(); i++) {
            KeyboardTheme t = allThemes.get(i);
            themeDisplayNames.add(t.icon + "  " + t.name);
            if (t.id.equalsIgnoreCase(curThemeId)) {
                selectedThemeIdx = i;
            }
        }

        themeAdapter = new ThemedSpinnerAdapter(this, themeDisplayNames, initialTheme);
        spinnerTheme.setAdapter(themeAdapter);
        spinnerTheme.setSelection(selectedThemeIdx);

        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allThemes.size()) {
                    String newThemeId = allThemes.get(position).id;
                    prefs.setThemeId(newThemeId);
                    KeyboardTheme newResolvedTheme = KeyboardTheme.resolveTheme(SettingsActivity.this, newThemeId);
                    applyTheme(newResolvedTheme);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Permanent Number Row CheckBox
        if (cbShowNumberRow != null) {
            cbShowNumberRow.setChecked(prefs.isShowNumberRow());
        }
    }

    private void updateModelsSpinner(List<String> models) {
        currentModels = models;
        if (modelAdapter != null) {
            modelAdapter.clear();
            modelAdapter.addAll(currentModels);
            modelAdapter.notifyDataSetChanged();
        }

        String selected = prefs.getSelectedModel();
        int idx = currentModels.indexOf(selected);
        if (idx >= 0 && spinnerModel != null) {
            spinnerModel.setSelection(idx);
        }
    }

    /**
     * Dynamically skins the Settings Page to match the selected theme in real-time.
     * Clean Light is the default theme; selecting any theme instantly adapts all cards,
     * buttons, inputs, drop-downs, and system bars.
     */
    private void applyTheme(KeyboardTheme theme) {
        if (theme == null) return;
        currentActiveTheme = theme;

        // 1. System Bar Colors & Light Icons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(theme.backgroundColor);
            getWindow().setNavigationBarColor(theme.backgroundColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = getWindow().getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (theme.isLight) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decor.setSystemUiVisibility(flags);
            }
        }

        // 2. Root ScrollView Background
        if (settingsScrollView != null) {
            settingsScrollView.setBackgroundColor(theme.backgroundColor);
        }

        // 3. Header Brand Titles
        if (tvBrandTitle != null) tvBrandTitle.setTextColor(theme.textColor);
        if (tvBrandSubtitle != null) tvBrandSubtitle.setTextColor(theme.accentColor);

        // 3b. Dynamic Theme Logo Adaptability
        if (ivHeaderLogo != null) {
            if ("clean_light".equals(theme.id) || ("system_auto".equals(theme.id) && theme.isLight)) {
                ivHeaderLogo.setImageResource(R.drawable.smart_ai_logo_light);
            } else if ("oled_black".equals(theme.id)) {
                ivHeaderLogo.setImageResource(R.drawable.smart_ai_logo_oled);
            } else if ("cyberpunk_neon".equals(theme.id)) {
                ivHeaderLogo.setImageResource(R.drawable.smart_ai_logo_cyberpunk);
            } else {
                ivHeaderLogo.setImageResource(R.drawable.smart_ai_logo);
            }
        }

        // 4. Style all Cards
        LinearLayout[] cards = {
            cardActivation, cardMic, cardGemini, cardAppearance,
            cardScanner, cardActionItems, cardShortcuts, cardPatterns, cardHowItWorks,
            cardAiDiagnostics
        };
        for (LinearLayout card : cards) {
            if (card != null) {
                card.setBackground(createChipDrawable(theme.toolbarColor, theme.dividerColor, 14));
                styleCardChildren(card, theme);
            }
        }

        // 5. Input Fields
        GradientDrawable inputBg = createChipDrawable(theme.keyColor, theme.dividerColor, 10);
        if (etApiKey != null) {
            etApiKey.setBackground(inputBg);
            etApiKey.setTextColor(theme.textColor);
            etApiKey.setHintTextColor(theme.subTextColor);
        }
        if (etNewActionItem != null) {
            etNewActionItem.setBackground(inputBg);
            etNewActionItem.setTextColor(theme.textColor);
            etNewActionItem.setHintTextColor(theme.subTextColor);
        }

        // 6. Primary Action Buttons (Executive Indigo & Cyan)
        Button[] primaryButtons = { btnEnableKeyboard, btnSaveApiKey, btnLaunchScanner, btnAddShortcut, btnAddActionItem, btnTestPureAiVoice };
        for (Button btn : primaryButtons) {
            if (btn != null) {
                btn.setBackground(createChipDrawable(theme.accentColor, theme.accentColor, 10));
                btn.setTextColor(Color.WHITE);
            }
        }

        // 7. Secondary Action Buttons
        int secTextColor = theme.isLight ? theme.textColor : Color.WHITE;
        Button[] secondaryButtons = { btnSelectKeyboard, btnGrantMicPermission, btnResetPatterns, btnTestPureAiCard, btnTestLiveWs, btnToggleShortcutsExpander, btnClearCompletedActions, btnViewMeetingSummaries };
        for (Button btn : secondaryButtons) {
            if (btn != null) {
                btn.setBackground(createChipDrawable(theme.actionKeyColor, theme.dividerColor, 10));
                btn.setTextColor(secTextColor);
            }
        }

        // 7b. Diagnostics Result Console Styling
        if (tvPureAiTestResult != null) {
            tvPureAiTestResult.setBackground(createChipDrawable(theme.isLight ? Color.parseColor("#0f172a") : Color.parseColor("#020617"),
                    theme.dividerColor, 10));
            tvPureAiTestResult.setTextColor(Color.parseColor("#38bdf8"));
        }

        // 8. Spinners & Dropdown Adapters
        if (spinnerModel != null) spinnerModel.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 10));
        if (spinnerMode != null) spinnerMode.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 10));
        if (spinnerTheme != null) spinnerTheme.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 10));

        if (modelAdapter != null) modelAdapter.setTheme(theme);
        if (modeAdapter != null) modeAdapter.setTheme(theme);
        if (themeAdapter != null) themeAdapter.setTheme(theme);

        // 9. Checkbox
        if (cbShowNumberRow != null) {
            cbShowNumberRow.setTextColor(theme.textColor);
        }

        // 10. Shortcuts List, Action Items List & Status Badges
        renderShortcutsList(theme);
        renderActionItemsList(theme);
        checkActivationState(theme);
        checkMicPermission(theme);
        updatePatternStats(theme);
    }

    private void styleCardChildren(ViewGroup group, KeyboardTheme theme) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && !(child instanceof Button) && !(child instanceof EditText) && !(child instanceof CheckBox)) {
                TextView tv = (TextView) child;
                int viewId = tv.getId();
                if (viewId == R.id.tvActivationStatus || viewId == R.id.tvMicStatus || viewId == R.id.tvPatternStats) {
                    continue;
                }
                float textSize = tv.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
                if (textSize >= 15) {
                    // Card Section Title
                    tv.setTextColor(theme.textColor);
                } else if (textSize <= 12 && tv.getTypeface() != null && tv.getTypeface().isBold()) {
                    // Field Label
                    tv.setTextColor(theme.accentColor);
                } else {
                    // Description or Subtitle
                    tv.setTextColor(theme.subTextColor);
                }
            } else if (child instanceof ViewGroup && !(child instanceof Spinner)) {
                styleCardChildren((ViewGroup) child, theme);
            }
        }
    }

    private void checkActivationState(KeyboardTheme theme) {
        if (tvActivationStatus == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        boolean isEnabled = false;
        String myPackage = getPackageName();

        List<InputMethodInfo> enabledMethods = imm.getEnabledInputMethodList();
        for (InputMethodInfo info : enabledMethods) {
            if (info.getPackageName().equals(myPackage)) {
                isEnabled = true;
                break;
            }
        }

        String currentIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean isCurrent = currentIme != null && currentIme.contains(myPackage);

        boolean isLight = theme != null && theme.isLight;
        if (isCurrent) {
            tvActivationStatus.setText("✓ Smart AI Keyboard is currently ENABLED and ACTIVE as your keyboard!");
            tvActivationStatus.setTextColor(isLight ? Color.parseColor("#16a34a") : Color.parseColor("#34d399"));
        } else if (isEnabled) {
            tvActivationStatus.setText("✓ Smart AI Keyboard is enabled. Tap '2. Switch to Smart AI Keyboard' to select it.");
            tvActivationStatus.setTextColor(isLight ? Color.parseColor("#d97706") : Color.parseColor("#fbbf24"));
        } else {
            tvActivationStatus.setText("⚠️ Not yet enabled. Tap '1. Enable Smart AI in Settings' to begin.");
            tvActivationStatus.setTextColor(isLight ? Color.parseColor("#dc2626") : Color.parseColor("#f87171"));
        }
    }

    private void checkMicPermission(KeyboardTheme theme) {
        if (tvMicStatus == null) return;
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean isLight = theme != null && theme.isLight;

        if (granted) {
            tvMicStatus.setText("✓ Microphone Permission GRANTED (Voice Dictation Ready)");
            tvMicStatus.setTextColor(isLight ? Color.parseColor("#16a34a") : Color.parseColor("#34d399"));
            if (btnGrantMicPermission != null) btnGrantMicPermission.setVisibility(View.GONE);
        } else {
            tvMicStatus.setText("⚠️ Microphone Permission NOT Granted. Tap below to enable voice dictation:");
            tvMicStatus.setTextColor(isLight ? Color.parseColor("#dc2626") : Color.parseColor("#f87171"));
            if (btnGrantMicPermission != null) btnGrantMicPermission.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERMISSION) {
            checkMicPermission(currentActiveTheme);
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✓ Microphone access granted! You can now dictate in any app.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission is required for voice typing.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void renderShortcutsList(KeyboardTheme theme) {
        if (llShortcutsList == null) return;
        llShortcutsList.removeAllViews();
        List<ShortcutsManager.ShortcutItem> list = shortcutsManager.getAllShortcuts();

        int maxItemsToShow = (list.size() > 3 && !isShortcutsListExpanded) ? 3 : list.size();
        for (int i = 0; i < maxItemsToShow; i++) {
            final ShortcutsManager.ShortcutItem item = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
            row.setPadding(dpToPx(12), dpToPx(8), dpToPx(8), dpToPx(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dpToPx(6);
            row.setLayoutParams(lp);

            TextView tvTrigger = new TextView(this);
            tvTrigger.setText(item.trigger);
            tvTrigger.setTextColor(theme.accentColor);
            tvTrigger.setTextSize(13);
            tvTrigger.setTypeface(null, Typeface.BOLD);
            row.addView(tvTrigger);

            TextView tvArrow = new TextView(this);
            tvArrow.setText(" ➔ ");
            tvArrow.setTextColor(theme.subTextColor);
            row.addView(tvArrow);

            TextView tvExp = new TextView(this);
            tvExp.setText(item.expansion);
            tvExp.setTextColor(theme.textColor);
            tvExp.setTextSize(13);
            LinearLayout.LayoutParams expP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvExp.setLayoutParams(expP);
            row.addView(tvExp);

            Button btnDel = new Button(this);
            btnDel.setText("✕");
            btnDel.setTextSize(12);
            btnDel.setTextColor(theme.isLight ? Color.parseColor("#dc2626") : Color.parseColor("#ef4444"));
            btnDel.setBackgroundColor(Color.TRANSPARENT);
            btnDel.setOnClickListener(v -> {
                shortcutsManager.deleteShortcut(item.trigger);
                renderShortcutsList(currentActiveTheme);
            });
            row.addView(btnDel);

            llShortcutsList.addView(row);
        }

        // Configure Expander Button for > 3 shortcuts
        if (btnToggleShortcutsExpander != null) {
            if (list.size() > 3) {
                btnToggleShortcutsExpander.setVisibility(View.VISIBLE);
                btnToggleShortcutsExpander.setText(isShortcutsListExpanded
                        ? "▲ Show Less (Showing All " + list.size() + ")"
                        : "▼ Show All (" + list.size() + ") Shortcuts");
            } else {
                btnToggleShortcutsExpander.setVisibility(View.GONE);
            }
        }
    }

    private void renderActionItemsList(KeyboardTheme theme) {
        if (llActionItemsList == null) return;
        llActionItemsList.removeAllViews();
        List<ActionItemsManager.ActionItem> items = ActionItemsManager.getInstance(this).getActionItems();

        int pendingCount = 0;
        int completedCount = 0;
        for (ActionItemsManager.ActionItem it : items) {
            if (it.isCompleted) completedCount++;
            else pendingCount++;
        }

        if (tvActionItemsBadge != null) {
            tvActionItemsBadge.setText(pendingCount + " Pending • " + completedCount + " Done");
        }

        if (items.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No action items yet.\nRecord a meeting on the keyboard to automatically extract action items, or type one above.");
            tvEmpty.setTextColor(theme != null ? theme.subTextColor : Color.parseColor("#94a3b8"));
            tvEmpty.setTextSize(12);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, dpToPx(12), 0, dpToPx(12));
            llActionItemsList.addView(tvEmpty);
            return;
        }

        for (final ActionItemsManager.ActionItem item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(createChipDrawable(
                    item.isCompleted ? (theme != null && theme.isLight ? Color.parseColor("#f1f5f9") : Color.parseColor("#0b101b"))
                                     : (theme != null && theme.isLight ? Color.parseColor("#ffffff") : Color.parseColor("#151d2d")),
                    theme != null ? theme.dividerColor : Color.parseColor("#334155"),
                    8
            ));
            row.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, 0, 0, dpToPx(6));
            row.setLayoutParams(rp);

            // Checkbox
            CheckBox cb = new CheckBox(this);
            cb.setChecked(item.isCompleted);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ActionItemsManager.getInstance(SettingsActivity.this).toggleCompleted(item.id);
                renderActionItemsList(currentActiveTheme);
            });
            row.addView(cb);

            // Text container (Title + Owner/Due badges)
            LinearLayout textContainer = new LinearLayout(this);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tcp.setMargins(dpToPx(6), 0, dpToPx(6), 0);
            textContainer.setLayoutParams(tcp);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(item.title);
            tvTitle.setTextSize(13);
            if (item.isCompleted) {
                tvTitle.setTextColor(theme != null ? theme.subTextColor : Color.GRAY);
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tvTitle.setTextColor(theme != null ? theme.textColor : Color.WHITE);
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            }
            textContainer.addView(tvTitle);

            if (!item.owner.isEmpty() || !item.dueDate.isEmpty()) {
                TextView tvMeta = new TextView(this);
                String meta = "";
                if (!item.owner.isEmpty()) meta += "👤 " + item.owner + "  ";
                if (!item.dueDate.isEmpty()) meta += "📅 " + item.dueDate;
                tvMeta.setText(meta.trim());
                tvMeta.setTextSize(10);
                tvMeta.setTextColor(theme != null ? theme.accentColor : Color.parseColor("#38bdf8"));
                tvMeta.setPadding(0, dpToPx(2), 0, 0);
                textContainer.addView(tvMeta);
            }

            row.addView(textContainer);

            // Delete Button
            TextView btnDelete = new TextView(this);
            btnDelete.setText("✕");
            btnDelete.setTextSize(14);
            btnDelete.setTextColor(Color.parseColor("#ef4444"));
            btnDelete.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            btnDelete.setOnClickListener(v -> {
                ActionItemsManager.getInstance(SettingsActivity.this).deleteActionItem(item.id);
                renderActionItemsList(currentActiveTheme);
            });
            row.addView(btnDelete);

            llActionItemsList.addView(row);
        }
    }

    private void showMeetingSummariesDialog() {
        KeyboardTheme theme = currentActiveTheme != null ? currentActiveTheme : KeyboardTheme.getDefaultTheme();
        List<ActionItemsManager.MeetingSummary> summaries = ActionItemsManager.getInstance(this).getMeetingSummaries();

        if (summaries.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("📑 Past Meeting Summaries")
                    .setMessage("No meeting summaries saved yet.\n\nUse the '🎙️💬 Meeting' button on the keyboard to record a conversation and extract an AI summary.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(18), dpToPx(12), dpToPx(18), dpToPx(16));
        content.setBackgroundColor(theme.backgroundColor);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());

        for (final ActionItemsManager.MeetingSummary s : summaries) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(createChipDrawable(theme.toolbarColor, theme.dividerColor, 10));
            card.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, dpToPx(12));
            card.setLayoutParams(cp);

            // Date & Action Items Count Header
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvDate = new TextView(this);
            tvDate.setText("🕒 " + sdf.format(new Date(s.timestamp)));
            tvDate.setTextColor(theme.accentColor);
            tvDate.setTextSize(12);
            tvDate.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams dpParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvDate.setLayoutParams(dpParams);
            header.addView(tvDate);

            TextView tvCount = new TextView(this);
            tvCount.setText(s.actionItemsCount + " Tasks");
            tvCount.setTextColor(theme.subTextColor);
            tvCount.setTextSize(11);
            header.addView(tvCount);

            card.addView(header);

            // Summary
            if (!s.summary.isEmpty()) {
                TextView lbl = new TextView(this);
                lbl.setText("\n📋 Executive Summary:");
                lbl.setTextColor(theme.textColor);
                lbl.setTextSize(11);
                lbl.setTypeface(null, Typeface.BOLD);
                card.addView(lbl);

                TextView tvSum = new TextView(this);
                tvSum.setText(s.summary);
                tvSum.setTextColor(theme.textColor);
                tvSum.setTextSize(12);
                card.addView(tvSum);
            }

            // Key Points
            if (!s.keyPoints.isEmpty()) {
                TextView lbl2 = new TextView(this);
                lbl2.setText("\n💡 Key Points:");
                lbl2.setTextColor(theme.textColor);
                lbl2.setTextSize(11);
                lbl2.setTypeface(null, Typeface.BOLD);
                card.addView(lbl2);

                TextView tvPts = new TextView(this);
                tvPts.setText(s.keyPoints);
                tvPts.setTextColor(theme.textColor);
                tvPts.setTextSize(12);
                card.addView(tvPts);
            }

            // Action Items
            if (!s.actionItemsText.isEmpty()) {
                TextView lbl3 = new TextView(this);
                lbl3.setText("\n⏳ Action Items:");
                lbl3.setTextColor(Color.parseColor("#10b981"));
                lbl3.setTextSize(11);
                lbl3.setTypeface(null, Typeface.BOLD);
                card.addView(lbl3);

                TextView tvAct = new TextView(this);
                tvAct.setText(s.actionItemsText);
                tvAct.setTextColor(theme.textColor);
                tvAct.setTextSize(12);
                card.addView(tvAct);
            }

            content.addView(card);
        }

        scroll.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("📑 Past Meeting Summaries (" + summaries.size() + ")")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showAddShortcutDialog() {
        KeyboardTheme theme = currentActiveTheme != null ? currentActiveTheme : KeyboardTheme.getDefaultTheme();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(10));

        final EditText etTrigger = new EditText(this);
        etTrigger.setHint("Shortcut Code (e.g. GM, addr, mymail)");
        etTrigger.setTextColor(theme.textColor);
        etTrigger.setHintTextColor(theme.subTextColor);
        etTrigger.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
        etTrigger.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        layout.addView(etTrigger);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dpToPx(10)));
        layout.addView(spacer);

        final EditText etExpansion = new EditText(this);
        etExpansion.setHint("Full Expansion (e.g. Good Morning)");
        etExpansion.setTextColor(theme.textColor);
        etExpansion.setHintTextColor(theme.subTextColor);
        etExpansion.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 8));
        etExpansion.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        layout.addView(etExpansion);

        new AlertDialog.Builder(this)
            .setTitle("Add Custom Shortcut")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                String trig = etTrigger.getText().toString().trim();
                String exp = etExpansion.getText().toString().trim();
                if (!trig.isEmpty() && !exp.isEmpty()) {
                    shortcutsManager.addShortcut(trig, exp);
                    renderShortcutsList(currentActiveTheme);
                    Toast.makeText(this, String.format(Locale.getDefault(), "Saved shortcut: %s ➔ %s", trig, exp), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Both shortcut trigger and expansion are required", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updatePatternStats(KeyboardTheme theme) {
        if (suggestionEngine != null && tvPatternStats != null) {
            int words = suggestionEngine.getLearnedWordsCount();
            int collocations = suggestionEngine.getLearnedCollocationsCount();
            tvPatternStats.setText(String.format(Locale.getDefault(), "Learned Words: %d | Learned Collocations: %d", words, collocations));
            if (theme != null) {
                tvPatternStats.setTextColor(theme.subTextColor);
            }
        }
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

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    /**
     * Custom Themed Spinner Adapter ensuring high-contrast labels and dropdowns across all themes.
     */
    private static class ThemedSpinnerAdapter extends ArrayAdapter<String> {
        private KeyboardTheme currentTheme;
        private final int itemPadding;

        public ThemedSpinnerAdapter(Context context, List<String> items, KeyboardTheme theme) {
            super(context, 0, items);
            this.currentTheme = theme;
            this.itemPadding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
        }

        public void setTheme(KeyboardTheme theme) {
            this.currentTheme = theme;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(getContext());
            tv.setText(getItem(position));
            tv.setTextColor(currentTheme != null ? currentTheme.textColor : Color.BLACK);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setSingleLine(true);
            tv.setPadding(itemPadding, itemPadding / 2, itemPadding, itemPadding / 2);
            return tv;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(getContext());
            tv.setText(getItem(position));
            tv.setTextColor(currentTheme != null ? currentTheme.textColor : Color.BLACK);
            tv.setBackgroundColor(currentTheme != null ? currentTheme.toolbarColor : Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);
            return tv;
        }
    }
}
