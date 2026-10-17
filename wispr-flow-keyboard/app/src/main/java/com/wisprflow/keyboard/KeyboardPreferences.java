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
    private static final String KEY_THEME_ID = "keyboard_theme_id";
    private static final String KEY_SHOW_NUMBER_ROW = "show_permanent_number_row";

    private static final String KEY_AUTO_CAPS = "auto_capitalization";
    private static final String KEY_AUTO_CORRECT = "auto_correction";
    private static final String KEY_DOUBLE_SPACE_PERIOD = "double_space_period";
    private static final String KEY_SOUND_CLICKS = "key_sound_clicks";
    private static final String KEY_SHOW_SECONDARY_SYMBOLS = "show_secondary_symbols";
    private static final String KEY_ONE_HANDED_MODE = "one_handed_mode"; // "full", "left", "right"

    public static final String DEFAULT_MODEL = "gemini-2.0-flash";
    public static final String DEFAULT_MODE = "polish";
    public static final String DEFAULT_THEME = "clean_light";

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

    public boolean isAutoCapsEnabled() {
        return prefs.getBoolean(KEY_AUTO_CAPS, true);
    }

    public void setAutoCapsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CAPS, enabled).apply();
    }

    public boolean isAutoCorrectEnabled() {
        return prefs.getBoolean(KEY_AUTO_CORRECT, true);
    }

    public void setAutoCorrectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CORRECT, enabled).apply();
    }

    public boolean isDoubleSpacePeriodEnabled() {
        return prefs.getBoolean(KEY_DOUBLE_SPACE_PERIOD, true);
    }

    public void setDoubleSpacePeriodEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, enabled).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_CLICKS, false);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_CLICKS, enabled).apply();
    }

    public boolean isShowSecondarySymbols() {
        return prefs.getBoolean(KEY_SHOW_SECONDARY_SYMBOLS, true);
    }

    public void setShowSecondarySymbols(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_SECONDARY_SYMBOLS, show).apply();
    }

    public String getOneHandedMode() {
        return prefs.getString(KEY_ONE_HANDED_MODE, "full");
    }

    public void setOneHandedMode(String mode) {
        prefs.edit().putString(KEY_ONE_HANDED_MODE, mode != null ? mode.trim() : "full").apply();
    }

    public String getThemeId() {
        return prefs.getString(KEY_THEME_ID, DEFAULT_THEME);
    }

    public void setThemeId(String themeId) {
        prefs.edit().putString(KEY_THEME_ID, themeId != null ? themeId.trim() : DEFAULT_THEME).apply();
    }

    public boolean isShowNumberRow() {
        return prefs.getBoolean(KEY_SHOW_NUMBER_ROW, false);
    }

    public void setShowNumberRow(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_NUMBER_ROW, show).apply();
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
