package com.wisprflow.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Manages clipboard history and pinned favorite snippets.
 */
public class ClipboardHistoryManager {

    private static final String PREF_NAME = "wispr_clipboard_prefs";
    private static final String KEY_CLIPS_JSON = "clipboard_items_json";
    private static final int MAX_HISTORY = 40;

    public static class ClipItem {
        public String text;
        public long timestamp;
        public boolean isPinned;

        public ClipItem(String text, long timestamp, boolean isPinned) {
            this.text = text;
            this.timestamp = timestamp;
            this.isPinned = isPinned;
        }
    }

    private final SharedPreferences prefs;
    private final List<ClipItem> items = new ArrayList<>();

    public ClipboardHistoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadClips();
    }

    private synchronized void loadClips() {
        items.clear();
        String jsonStr = prefs.getString(KEY_CLIPS_JSON, null);
        if (jsonStr != null && !jsonStr.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    items.add(new ClipItem(
                        obj.getString("text"),
                        obj.optLong("timestamp", System.currentTimeMillis()),
                        obj.optBoolean("isPinned", false)
                    ));
                }
            } catch (JSONException ignored) {}
        }

        // Seed some sample pinned favorites if first launch
        if (items.isEmpty()) {
            items.add(new ClipItem("Thank you for your business! Let me know if you need any assistance.", System.currentTimeMillis(), true));
            items.add(new ClipItem("Let's connect on this tomorrow morning.", System.currentTimeMillis(), true));
            saveClips();
        }
    }

    private synchronized void saveClips() {
        try {
            JSONArray arr = new JSONArray();
            for (ClipItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("text", item.text);
                obj.put("timestamp", item.timestamp);
                obj.put("isPinned", item.isPinned);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_CLIPS_JSON, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    /**
     * Records a new clip into clipboard history.
     */
    public synchronized void addClip(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String clean = text.trim();

        // Check if item already exists
        for (ClipItem item : items) {
            if (item.text.equals(clean)) {
                // Update timestamp and move to top (if not pinned)
                item.timestamp = System.currentTimeMillis();
                saveClips();
                return;
            }
        }

        // Insert at the front (after pinned items)
        items.add(0, new ClipItem(clean, System.currentTimeMillis(), false));

        // Trim excess unpinned items
        if (items.size() > MAX_HISTORY) {
            for (int i = items.size() - 1; i >= 0 && items.size() > MAX_HISTORY; i--) {
                if (!items.get(i).isPinned) {
                    items.remove(i);
                }
            }
        }
        saveClips();
    }

    public synchronized void togglePin(ClipItem target) {
        if (target == null) return;
        for (ClipItem item : items) {
            if (item.text.equals(target.text)) {
                item.isPinned = !item.isPinned;
                target.isPinned = item.isPinned;
                saveClips();
                return;
            }
        }
    }

    public synchronized void deleteClip(ClipItem target) {
        if (target == null) return;
        Iterator<ClipItem> it = items.iterator();
        while (it.hasNext()) {
            ClipItem item = it.next();
            if (item.text.equals(target.text)) {
                it.remove();
                saveClips();
                return;
            }
        }
    }

    public synchronized int getPinnedCount() {
        int count = 0;
        for (ClipItem item : items) {
            if (item.isPinned) count++;
        }
        return count;
    }

    public synchronized int getRecentCount() {
        int count = 0;
        for (ClipItem item : items) {
            if (!item.isPinned) count++;
        }
        return count;
    }

    public synchronized void clearUnpinned() {
        Iterator<ClipItem> it = items.iterator();
        while (it.hasNext()) {
            if (!it.next().isPinned) {
                it.remove();
            }
        }
        saveClips();
    }

    /**
     * Returns all clips with pinned items first, then recent items sorted by timestamp.
     */
    public synchronized List<ClipItem> getAllClips() {
        List<ClipItem> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> {
            if (a.isPinned != b.isPinned) {
                return a.isPinned ? -1 : 1; // Pinned items first
            }
            return Long.compare(b.timestamp, a.timestamp); // Newer items first
        });
        return sorted;
    }

    public synchronized List<ClipItem> getPinnedClips() {
        List<ClipItem> list = new ArrayList<>();
        for (ClipItem item : items) {
            if (item.isPinned) list.add(item);
        }
        return list;
    }

    public synchronized List<ClipItem> getRecentClips() {
        List<ClipItem> list = new ArrayList<>();
        for (ClipItem item : items) {
            if (!item.isPinned) list.add(item);
        }
        return list;
    }

    public synchronized String getLastCopiedText() {
        if (!items.isEmpty()) {
            return items.get(0).text;
        }
        return null;
    }
}
