package com.wisprflow.keyboard;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings & Launcher Setup Activity for Wispr Flow Keyboard.
 * - Live Clean Light / Custom Theme Synchronization
 * - OS Dark/Light Mode Adaptability (System Auto)
 * - 1-tap activation and input method switcher
 * - Microphone runtime permission management
 * - Gemini API key and model selection (default: gemini-2.0-flash)
 * - Custom text shortcuts manager (e.g. GM -> Good Morning)
 * - Typing pattern and collocation statistics
 * - Dedicated Contact Card Scanner launcher
 * - Interactive live keyboard test playground
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_MIC_PERMISSION = 101;

    private KeyboardPreferences prefs;
    private ShortcutsManager shortcutsManager;
    private SuggestionEngine suggestionEngine;

    private ScrollView settingsScrollView;
    private TextView tvBrandTitle;
    private TextView tvBrandSubtitle;

    private LinearLayout cardActivation;
    private LinearLayout cardMic;
    private LinearLayout cardGemini;
    private LinearLayout cardAppearance;
    private LinearLayout cardScanner;
    private LinearLayout cardShortcuts;
    private LinearLayout cardPatterns;
    private LinearLayout cardTesting;
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
    private LinearLayout llShortcutsList;
    private Button btnAddShortcut;
    private TextView tvPatternStats;
    private Button btnResetPatterns;
    private EditText etTestInput;
    private Button btnTestSampleVoice;
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
        tvBrandTitle = findViewById(R.id.tvBrandTitle);
        tvBrandSubtitle = findViewById(R.id.tvBrandSubtitle);

        cardActivation = findViewById(R.id.cardActivation);
        cardMic = findViewById(R.id.cardMic);
        cardGemini = findViewById(R.id.cardGemini);
        cardAppearance = findViewById(R.id.cardAppearance);
        cardScanner = findViewById(R.id.cardScanner);
        cardShortcuts = findViewById(R.id.cardShortcuts);
        cardPatterns = findViewById(R.id.cardPatterns);
        cardTesting = findViewById(R.id.cardTesting);
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
        llShortcutsList = findViewById(R.id.llShortcutsList);
        btnAddShortcut = findViewById(R.id.btnAddShortcut);
        tvPatternStats = findViewById(R.id.tvPatternStats);
        btnResetPatterns = findViewById(R.id.btnResetPatterns);
        etTestInput = findViewById(R.id.etTestInput);
        btnTestSampleVoice = findViewById(R.id.btnTestSampleVoice);

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
                    Toast.makeText(this, "Enable 'LeadPulse AI Keyboard' in the list", Toast.LENGTH_LONG).show();
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

        // Test Simulated Speech Button
        if (btnTestSampleVoice != null) {
            btnTestSampleVoice.setOnClickListener(v -> {
                String sample = "um hello team uh please find the quarterly report attached and let me know your thoughts";
                etTestInput.setText("Input: \"" + sample + "\"\n\nPolishing with " + prefs.getSelectedModel() + "...");

                FlowMode mode = FlowMode.fromId(prefs.getFlowMode());
                GeminiFlowClient.polishSpeech(sample, prefs.getApiKey(), prefs.getSelectedModel(), mode, new GeminiFlowClient.PolishCallback() {
                    @Override
                    public void onSuccess(String polishedText, long latencyMs) {
                        runOnUiThread(() -> etTestInput.setText("✓ " + mode.getDisplayName() + " (" + latencyMs + "ms):\n\n" + polishedText));
                    }

                    @Override
                    public void onError(String errorMessage, String fallbackText) {
                        runOnUiThread(() -> etTestInput.setText("✓ Offline Output:\n\n" + fallbackText));
                    }
                });
            });
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

        // 4. Style all Cards
        LinearLayout[] cards = {
            cardActivation, cardMic, cardGemini, cardAppearance,
            cardScanner, cardShortcuts, cardPatterns, cardTesting,
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
        if (etTestInput != null) {
            etTestInput.setBackground(createChipDrawable(theme.keyColor, theme.dividerColor, 10));
            etTestInput.setTextColor(theme.textColor);
            etTestInput.setHintTextColor(theme.subTextColor);
        }

        // 6. Primary Action Buttons (Executive Indigo & Cyan)
        Button[] primaryButtons = { btnEnableKeyboard, btnSaveApiKey, btnLaunchScanner, btnAddShortcut, btnTestPureAiVoice };
        for (Button btn : primaryButtons) {
            if (btn != null) {
                btn.setBackground(createChipDrawable(theme.accentColor, theme.accentColor, 10));
                btn.setTextColor(Color.WHITE);
            }
        }

        // 7. Secondary Action Buttons
        int secTextColor = theme.isLight ? theme.textColor : Color.WHITE;
        Button[] secondaryButtons = { btnSelectKeyboard, btnGrantMicPermission, btnTestSampleVoice, btnResetPatterns, btnTestPureAiCard, btnTestLiveWs };
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

        // 10. Shortcuts List & Status Badges
        renderShortcutsList(theme);
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
            tvActivationStatus.setText("✓ LeadPulse AI Keyboard is currently ENABLED and ACTIVE as your keyboard!");
            tvActivationStatus.setTextColor(isLight ? Color.parseColor("#16a34a") : Color.parseColor("#34d399"));
        } else if (isEnabled) {
            tvActivationStatus.setText("✓ LeadPulse AI is enabled. Tap '2. Switch to LeadPulse Keyboard' to select it.");
            tvActivationStatus.setTextColor(isLight ? Color.parseColor("#d97706") : Color.parseColor("#fbbf24"));
        } else {
            tvActivationStatus.setText("⚠️ Not yet enabled. Tap '1. Enable LeadPulse in Settings' to begin.");
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

        for (final ShortcutsManager.ShortcutItem item : list) {
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
