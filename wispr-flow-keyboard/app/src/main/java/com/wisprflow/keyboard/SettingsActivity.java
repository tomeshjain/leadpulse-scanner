package com.wisprflow.keyboard;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings & Launcher Setup Activity for Wispr Flow Keyboard.
 * - 1-tap activation and switcher
 * - Microphone runtime permission management
 * - Gemini API key and model selection
 * - Custom text shortcuts manager (e.g. GM -> Good Morning)
 * - Typing pattern and collocation statistics
 * - Live interactive keyboard test
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_MIC_PERMISSION = 101;

    private KeyboardPreferences prefs;
    private ShortcutsManager shortcutsManager;
    private SuggestionEngine suggestionEngine;

    private TextView tvActivationStatus;
    private TextView tvMicStatus;
    private Button btnGrantMicPermission;
    private EditText etApiKey;
    private Button btnSaveApiKey;
    private Spinner spinnerModel;
    private Spinner spinnerMode;
    private LinearLayout llShortcutsList;
    private Button btnAddShortcut;
    private TextView tvPatternStats;
    private Button btnResetPatterns;
    private EditText etTestInput;
    private Button btnTestSampleVoice;

    private ArrayAdapter<String> modelAdapter;
    private List<String> currentModels;

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
        renderShortcutsList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkActivationState();
        checkMicPermission();
        updatePatternStats();
    }

    private void initViews() {
        tvActivationStatus = findViewById(R.id.tvActivationStatus);
        tvMicStatus = findViewById(R.id.tvMicStatus);
        btnGrantMicPermission = findViewById(R.id.btnGrantMicPermission);
        etApiKey = findViewById(R.id.etApiKey);
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
        spinnerModel = findViewById(R.id.spinnerModel);
        spinnerMode = findViewById(R.id.spinnerMode);
        llShortcutsList = findViewById(R.id.llShortcutsList);
        btnAddShortcut = findViewById(R.id.btnAddShortcut);
        tvPatternStats = findViewById(R.id.tvPatternStats);
        btnResetPatterns = findViewById(R.id.btnResetPatterns);
        etTestInput = findViewById(R.id.etTestInput);
        btnTestSampleVoice = findViewById(R.id.btnTestSampleVoice);
    }

    private void setupListeners() {
        // Step 1: Open System Settings to Enable Wispr Flow
        findViewById(R.id.btnEnableKeyboard).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this, "Enable 'Wispr Flow AI Keyboard' in the list", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not open settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Step 2: Switch Active Keyboard
        findViewById(R.id.btnSelectKeyboard).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        // Microphone Permission Request
        btnGrantMicPermission.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission is already granted!", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERMISSION);
            }
        });

        // Save API Key & Fetch Models
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

        // Add Shortcut Dialog
        btnAddShortcut.setOnClickListener(v -> showAddShortcutDialog());

        // Reset Learned Patterns
        btnResetPatterns.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Reset Learned Patterns?")
                .setMessage("This will clear learned user frequencies and bigram collocations, restoring default vocabulary.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    suggestionEngine.resetLearnedPatterns();
                    updatePatternStats();
                    Toast.makeText(this, "Typing pattern history reset.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // Test Simulated Speech Button
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

    private void populateData() {
        // API Key
        etApiKey.setText(prefs.getApiKey());

        // Models Spinner
        currentModels = prefs.getAvailableModels();
        updateModelsSpinner(currentModels);

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

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, modeNames);
        modeAdapter.setDropDownViewResource(R.layout.spinner_item);
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
    }

    private void updateModelsSpinner(List<String> models) {
        currentModels = models;
        modelAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, currentModels);
        modelAdapter.setDropDownViewResource(R.layout.spinner_item);
        spinnerModel.setAdapter(modelAdapter);

        String selected = prefs.getSelectedModel();
        int idx = currentModels.indexOf(selected);
        if (idx >= 0) {
            spinnerModel.setSelection(idx);
        }
    }

    private void checkActivationState() {
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

        if (isCurrent) {
            tvActivationStatus.setText("✓ Wispr Flow is currently ENABLED and ACTIVE as your keyboard!");
            tvActivationStatus.setTextColor(Color.parseColor("#34d399"));
        } else if (isEnabled) {
            tvActivationStatus.setText("✓ Wispr Flow is enabled. Tap '2. Switch Active Keyboard' to select it.");
            tvActivationStatus.setTextColor(Color.parseColor("#fbbf24"));
        } else {
            tvActivationStatus.setText("⚠️ Not yet enabled. Tap '1. Enable Wispr Flow in Settings' to begin.");
            tvActivationStatus.setTextColor(Color.parseColor("#f87171"));
        }
    }

    private void checkMicPermission() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            tvMicStatus.setText("✓ Microphone Permission GRANTED (Voice Dictation Ready)");
            tvMicStatus.setTextColor(Color.parseColor("#34d399"));
            btnGrantMicPermission.setVisibility(View.GONE);
        } else {
            tvMicStatus.setText("⚠️ Microphone Permission NOT Granted. Tap below to enable voice dictation:");
            tvMicStatus.setTextColor(Color.parseColor("#f87171"));
            btnGrantMicPermission.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERMISSION) {
            checkMicPermission();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✓ Microphone access granted! You can now dictate in any app.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission is required for voice typing.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void renderShortcutsList() {
        llShortcutsList.removeAllViews();
        List<ShortcutsManager.ShortcutItem> list = shortcutsManager.getAllShortcuts();

        for (final ShortcutsManager.ShortcutItem item : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(createChipDrawable("#141d2c", "#243247", 8));
            row.setPadding(dpToPx(12), dpToPx(8), dpToPx(8), dpToPx(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dpToPx(6);
            row.setLayoutParams(lp);

            TextView tvTrigger = new TextView(this);
            tvTrigger.setText(item.trigger);
            tvTrigger.setTextColor(Color.parseColor("#818cf8"));
            tvTrigger.setTextSize(13);
            tvTrigger.setTypeface(null, Typeface.BOLD);
            row.addView(tvTrigger);

            TextView tvArrow = new TextView(this);
            tvArrow.setText(" ➔ ");
            tvArrow.setTextColor(Color.parseColor("#64748b"));
            row.addView(tvArrow);

            TextView tvExp = new TextView(this);
            tvExp.setText(item.expansion);
            tvExp.setTextColor(Color.WHITE);
            tvExp.setTextSize(13);
            LinearLayout.LayoutParams expP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvExp.setLayoutParams(expP);
            row.addView(tvExp);

            Button btnDel = new Button(this);
            btnDel.setText("✕");
            btnDel.setTextSize(12);
            btnDel.setTextColor(Color.parseColor("#ef4444"));
            btnDel.setBackgroundColor(Color.TRANSPARENT);
            btnDel.setOnClickListener(v -> {
                shortcutsManager.deleteShortcut(item.trigger);
                renderShortcutsList();
            });
            row.addView(btnDel);

            llShortcutsList.addView(row);
        }
    }

    private void showAddShortcutDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(10));

        final EditText etTrigger = new EditText(this);
        etTrigger.setHint("Shortcut Code (e.g. GM, addr, mymail)");
        etTrigger.setTextColor(Color.WHITE);
        etTrigger.setHintTextColor(Color.parseColor("#64748b"));
        etTrigger.setBackground(createChipDrawable("#162032", "#23334d", 6));
        etTrigger.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        layout.addView(etTrigger);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dpToPx(10)));
        layout.addView(spacer);

        final EditText etExpansion = new EditText(this);
        etExpansion.setHint("Full Expansion (e.g. Good Morning)");
        etExpansion.setTextColor(Color.WHITE);
        etExpansion.setHintTextColor(Color.parseColor("#64748b"));
        etExpansion.setBackground(createChipDrawable("#162032", "#23334d", 6));
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
                    renderShortcutsList();
                    Toast.makeText(this, "Saved shortcut: " + trig + " ➔ " + exp, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Both shortcut trigger and expansion are required", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updatePatternStats() {
        if (suggestionEngine != null && tvPatternStats != null) {
            int words = suggestionEngine.getLearnedWordsCount();
            int collocations = suggestionEngine.getLearnedCollocationsCount();
            tvPatternStats.setText("Learned Words: " + words + " | Learned Collocations: " + collocations);
        }
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

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
