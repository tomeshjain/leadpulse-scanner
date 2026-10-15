package com.wisprflow.keyboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings & Launcher Setup Activity for Wispr Flow Keyboard.
 * Provides 1-tap activation, Gemini API key configuration, and model selection.
 */
public class SettingsActivity extends AppCompatActivity {

    private KeyboardPreferences prefs;
    private TextView tvActivationStatus;
    private EditText etApiKey;
    private Button btnSaveApiKey;
    private Spinner spinnerModel;
    private Spinner spinnerMode;
    private EditText etTestInput;
    private Button btnTestSampleVoice;

    private ArrayAdapter<String> modelAdapter;
    private List<String> currentModels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new KeyboardPreferences(this);

        initViews();
        setupListeners();
        populateData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkActivationState();
    }

    private void initViews() {
        tvActivationStatus = findViewById(R.id.tvActivationStatus);
        etApiKey = findViewById(R.id.etApiKey);
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
        spinnerModel = findViewById(R.id.spinnerModel);
        spinnerMode = findViewById(R.id.spinnerMode);
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
                Toast.makeText(this, "Enable 'Wispr Flow AI Keyboard' in list", Toast.LENGTH_LONG).show();
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
            btnSaveApiKey.setText("Validating Key...");

            GeminiFlowClient.fetchAvailableModels(apiKey, new GeminiFlowClient.ModelsCallback() {
                @Override
                public void onSuccess(List<String> models) {
                    btnSaveApiKey.setEnabled(true);
                    btnSaveApiKey.setText("Save Key & Fetch Models");
                    prefs.setAvailableModels(models);
                    updateModelSpinner(models);
                    Toast.makeText(SettingsActivity.this, "✓ API Key valid! Discovered " + models.size() + " models.", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String errorMessage) {
                    btnSaveApiKey.setEnabled(true);
                    btnSaveApiKey.setText("Save Key & Fetch Models");
                    Toast.makeText(SettingsActivity.this, "Note: " + errorMessage + " (Saved key locally)", Toast.LENGTH_LONG).show();
                }
            });
        });

        // Model Selection Listener
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (currentModels != null && position < currentModels.size()) {
                    String selected = currentModels.get(position);
                    prefs.setSelectedModel(selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Mode Selection Listener
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                FlowMode[] modes = FlowMode.values();
                if (position < modes.length) {
                    prefs.setFlowMode(modes[position].getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Quick Test Sample Speech Button
        btnTestSampleVoice.setOnClickListener(v -> {
            String sample = "um basically you know we need to deploy the new microservice by Thursday afternoon so yeah please review the PR";
            etTestInput.setText("Raw Speech: \"" + sample + "\"\n\nPolishing with " + prefs.getSelectedModel() + "...");

            FlowMode activeMode = FlowMode.fromId(prefs.getFlowMode());
            GeminiFlowClient.polishSpeech(sample, prefs.getApiKey(), prefs.getSelectedModel(), activeMode, new GeminiFlowClient.PolishCallback() {
                @Override
                public void onSuccess(String polishedText, long latencyMs) {
                    etTestInput.setText(polishedText);
                    Toast.makeText(SettingsActivity.this, "✓ Polished in " + latencyMs + "ms!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String errorMessage, String fallbackText) {
                    etTestInput.setText(fallbackText);
                    Toast.makeText(SettingsActivity.this, "Polished via offline fallback.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void populateData() {
        etApiKey.setText(prefs.getApiKey());

        // Models
        currentModels = prefs.getAvailableModels();
        updateModelSpinner(currentModels);

        // Modes
        List<String> modeNames = new ArrayList<>();
        FlowMode[] modes = FlowMode.values();
        int selectedModeIndex = 0;
        String currentModeId = prefs.getFlowMode();

        for (int i = 0; i < modes.length; i++) {
            modeNames.add(modes[i].getDisplayName() + " - " + modes[i].getDescription());
            if (modes[i].getId().equalsIgnoreCase(currentModeId)) {
                selectedModeIndex = i;
            }
        }

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, modeNames);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);
        spinnerMode.setSelection(selectedModeIndex);
    }

    private void updateModelSpinner(List<String> models) {
        this.currentModels = models;
        modelAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, models);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(modelAdapter);

        String currentModel = prefs.getSelectedModel();
        int index = models.indexOf(currentModel);
        if (index >= 0) {
            spinnerModel.setSelection(index);
        } else if (!models.isEmpty()) {
            spinnerModel.setSelection(0);
            prefs.setSelectedModel(models.get(0));
        }
    }

    private void checkActivationState() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        boolean isEnabled = false;
        String myPackage = getPackageName();

        List<InputMethodInfo> enabledList = imm.getEnabledInputMethodList();
        for (InputMethodInfo imi : enabledList) {
            if (imi.getPackageName().equals(myPackage)) {
                isEnabled = true;
                break;
            }
        }

        String currentIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean isCurrentActive = currentIme != null && currentIme.contains(myPackage);

        if (isCurrentActive) {
            tvActivationStatus.setText("✓ Wispr Flow is ENABLED and set as your DEFAULT KEYBOARD!");
            tvActivationStatus.setTextColor(getResources().getColor(R.color.accent_emerald));
        } else if (isEnabled) {
            tvActivationStatus.setText("✓ Enabled in settings. Tap '2. Switch Active Keyboard' to select it.");
            tvActivationStatus.setTextColor(getResources().getColor(R.color.accent_cyan));
        } else {
            tvActivationStatus.setText("⚠️ Not enabled yet. Tap '1. Enable Wispr Flow in Settings' above.");
            tvActivationStatus.setTextColor(getResources().getColor(R.color.accent_pink));
        }
    }
}
