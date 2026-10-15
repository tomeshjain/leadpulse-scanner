package com.wisprflow.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages configuration, API keys, and model preferences for Wispr Flow Keyboard.
 */
public class KeyboardPreferences {

    private static final String PREF_NAME = "wispr_flow_keyboard_prefs";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_SELECTED_MODEL = "selected_model";
    private static final String KEY_FLOW_MODE = "flow_mode";
    private static final String KEY_HAPTIC = "haptic_feedback";
    private static final String KEY_AVAILABLE_MODELS = "available_models";

    public static final String DEFAULT_MODEL = "gemini-2.0-flash";
    public static final String DEFAULT_MODE = "polish";

    private final SharedPreferences prefs;

    public KeyboardPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getApiKey() {
        return prefs.getString(KEY_GEMINI_API_KEY, "").trim();
    }

    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
    }

    public boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    public String getSelectedModel() {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL);
    }

    public void setSelectedModel(String model) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model != null ? model.trim() : DEFAULT_MODEL).apply();
    }

    public String getFlowMode() {
        return prefs.getString(KEY_FLOW_MODE, DEFAULT_MODE);
    }

    public void setFlowMode(String mode) {
        prefs.edit().putString(KEY_FLOW_MODE, mode != null ? mode.trim() : DEFAULT_MODE).apply();
    }

    public boolean isHapticEnabled() {
        return prefs.getBoolean(KEY_HAPTIC, true);
    }

    public void setHapticEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply();
    }

    public List<String> getAvailableModels() {
        String raw = prefs.getString(KEY_AVAILABLE_MODELS, "");
        if (raw.isEmpty()) {
            return new ArrayList<>(Arrays.asList(
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-1.5-pro",
                "gemini-2.0-flash-lite"
            ));
        }
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public void setAvailableModels(List<String> models) {
        if (models == null || models.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(models.get(i));
        }
        prefs.edit().putString(KEY_AVAILABLE_MODELS, sb.toString()).apply();
    }
}
