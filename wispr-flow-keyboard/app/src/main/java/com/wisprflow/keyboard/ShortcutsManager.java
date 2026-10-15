package com.wisprflow.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manages text expansion shortcuts (e.g., GM -> Good Morning, TY -> Thank you very much).
 * Supports default built-in shortcuts and user customized additions/deletions.
 */
public class ShortcutsManager {

    private static final String PREF_NAME = "wispr_shortcuts_prefs";
    private static final String KEY_SHORTCUTS_JSON = "custom_shortcuts_json";

    private final SharedPreferences prefs;
    private final Map<String, String> shortcutsMap = new LinkedHashMap<>();

    public static class ShortcutItem {
        public final String trigger;
        public final String expansion;

        public ShortcutItem(String trigger, String expansion) {
            this.trigger = trigger;
            this.expansion = expansion;
        }
    }

    public ShortcutsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadShortcuts();
    }

    private void loadShortcuts() {
        shortcutsMap.clear();
        String jsonStr = prefs.getString(KEY_SHORTCUTS_JSON, null);

        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            loadDefaultShortcuts();
            saveShortcuts();
        } else {
            try {
                JSONObject obj = new JSONObject(jsonStr);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    shortcutsMap.put(key, obj.getString(key));
                }
            } catch (JSONException e) {
                loadDefaultShortcuts();
            }
        }
    }

    private void loadDefaultShortcuts() {
        shortcutsMap.put("GM", "Good Morning");
        shortcutsMap.put("GE", "Good Evening");
        shortcutsMap.put("GA", "Good Afternoon");
        shortcutsMap.put("GN", "Good Night");
        shortcutsMap.put("TY", "Thank you very much");
        shortcutsMap.put("TYS", "Thank you so much!");
        shortcutsMap.put("NP", "No problem!");
        shortcutsMap.put("BRB", "Be right back");
        shortcutsMap.put("OMW", "On my way!");
        shortcutsMap.put("IDK", "I don't know");
        shortcutsMap.put("BTW", "By the way");
        shortcutsMap.put("PLZ", "Please");
        shortcutsMap.put("ASAP", "As soon as possible");
        shortcutsMap.put("TC", "Take care");
        shortcutsMap.put("HBU", "How about you?");
    }

    private synchronized void saveShortcuts() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> entry : shortcutsMap.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
            prefs.edit().putString(KEY_SHORTCUTS_JSON, obj.toString()).apply();
        } catch (JSONException ignored) {}
    }

    /**
     * Checks if a given word (case-insensitive) is a shortcut trigger.
     * Returns the expanded text, or null if not found.
     */
    public synchronized String getExpansion(String word) {
        if (word == null || word.trim().isEmpty()) return null;
        String clean = word.trim();

        // Exact match first
        if (shortcutsMap.containsKey(clean)) {
            return shortcutsMap.get(clean);
        }

        // Case-insensitive match
        for (Map.Entry<String, String> entry : shortcutsMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(clean)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Checks if any shortcut trigger starts with the prefix.
     */
    public synchronized List<ShortcutItem> findMatchingShortcuts(String prefix) {
        List<ShortcutItem> results = new ArrayList<>();
        if (prefix == null || prefix.trim().isEmpty()) return results;
        String lowerPrefix = prefix.trim().toLowerCase(Locale.US);

        for (Map.Entry<String, String> entry : shortcutsMap.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.US).startsWith(lowerPrefix)) {
                results.add(new ShortcutItem(entry.getKey(), entry.getValue()));
            }
        }
        return results;
    }

    /**
     * Adds or updates a shortcut.
     */
    public synchronized void addShortcut(String trigger, String expansion) {
        if (trigger == null || expansion == null) return;
        String cleanTrigger = trigger.trim().toUpperCase(Locale.US);
        String cleanExpansion = expansion.trim();
        if (cleanTrigger.isEmpty() || cleanExpansion.isEmpty()) return;

        shortcutsMap.put(cleanTrigger, cleanExpansion);
        saveShortcuts();
    }

    /**
     * Deletes a shortcut.
     */
    public synchronized void deleteShortcut(String trigger) {
        if (trigger == null) return;
        String clean = trigger.trim();
        shortcutsMap.remove(clean);
        // Also remove case-insensitive
        String keyToRemove = null;
        for (String k : shortcutsMap.keySet()) {
            if (k.equalsIgnoreCase(clean)) {
                keyToRemove = k;
                break;
            }
        }
        if (keyToRemove != null) {
            shortcutsMap.remove(keyToRemove);
        }
        saveShortcuts();
    }

    /**
     * Returns all shortcuts sorted by trigger name.
     */
    public synchronized List<ShortcutItem> getAllShortcuts() {
        List<ShortcutItem> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : shortcutsMap.entrySet()) {
            list.add(new ShortcutItem(entry.getKey(), entry.getValue()));
        }
        Collections.sort(list, (a, b) -> a.trigger.compareToIgnoreCase(b.trigger));
        return list;
    }

    public synchronized void resetDefaults() {
        shortcutsMap.clear();
        loadDefaultShortcuts();
        saveShortcuts();
    }
}
