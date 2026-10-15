package com.wisprflow.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages persistent storage and synchronization for Meeting Summaries and Action Items.
 * Automatically extracts actionable tasks from meeting recordings and stores them in Settings.
 */
public class ActionItemsManager {

    private static final String PREF_NAME = "smart_ai_action_items";
    private static final String KEY_ACTION_ITEMS = "action_items_json";
    private static final String KEY_MEETING_SUMMARIES = "meeting_summaries_json";

    private static ActionItemsManager instance;
    private final SharedPreferences prefs;

    public static class ActionItem {
        public String id;
        public String title;
        public String owner;
        public String dueDate;
        public boolean isCompleted;
        public long createdAt;
        public String meetingId;

        public ActionItem(String id, String title, String owner, String dueDate, boolean isCompleted, long createdAt, String meetingId) {
            this.id = id;
            this.title = title;
            this.owner = owner != null ? owner : "";
            this.dueDate = dueDate != null ? dueDate : "";
            this.isCompleted = isCompleted;
            this.createdAt = createdAt;
            this.meetingId = meetingId != null ? meetingId : "";
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("title", title);
                obj.put("owner", owner);
                obj.put("dueDate", dueDate);
                obj.put("isCompleted", isCompleted);
                obj.put("createdAt", createdAt);
                obj.put("meetingId", meetingId);
            } catch (Exception ignored) {}
            return obj;
        }

        public static ActionItem fromJson(JSONObject obj) {
            if (obj == null) return null;
            return new ActionItem(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("title", ""),
                    obj.optString("owner", ""),
                    obj.optString("dueDate", ""),
                    obj.optBoolean("isCompleted", false),
                    obj.optLong("createdAt", System.currentTimeMillis()),
                    obj.optString("meetingId", "")
            );
        }
    }

    public static class MeetingSummary {
        public String id;
        public long timestamp;
        public String summary;
        public String keyPoints;
        public String actionItemsText;
        public int actionItemsCount;

        public MeetingSummary(String id, long timestamp, String summary, String keyPoints, String actionItemsText, int actionItemsCount) {
            this.id = id;
            this.timestamp = timestamp;
            this.summary = summary != null ? summary : "";
            this.keyPoints = keyPoints != null ? keyPoints : "";
            this.actionItemsText = actionItemsText != null ? actionItemsText : "";
            this.actionItemsCount = actionItemsCount;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("timestamp", timestamp);
                obj.put("summary", summary);
                obj.put("keyPoints", keyPoints);
                obj.put("actionItemsText", actionItemsText);
                obj.put("actionItemsCount", actionItemsCount);
            } catch (Exception ignored) {}
            return obj;
        }

        public static MeetingSummary fromJson(JSONObject obj) {
            if (obj == null) return null;
            return new MeetingSummary(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optLong("timestamp", System.currentTimeMillis()),
                    obj.optString("summary", ""),
                    obj.optString("keyPoints", ""),
                    obj.optString("actionItemsText", ""),
                    obj.optInt("actionItemsCount", 0)
            );
        }
    }

    private ActionItemsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ActionItemsManager getInstance(Context context) {
        if (instance == null) {
            instance = new ActionItemsManager(context);
        }
        return instance;
    }

    public synchronized List<ActionItem> getActionItems() {
        List<ActionItem> items = new ArrayList<>();
        String json = prefs.getString(KEY_ACTION_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                ActionItem item = ActionItem.fromJson(arr.getJSONObject(i));
                if (item != null && !item.title.isEmpty()) {
                    items.add(item);
                }
            }
        } catch (Exception ignored) {}
        return items;
    }

    public synchronized void saveActionItems(List<ActionItem> items) {
        JSONArray arr = new JSONArray();
        for (ActionItem item : items) {
            arr.put(item.toJson());
        }
        prefs.edit().putString(KEY_ACTION_ITEMS, arr.toString()).apply();
    }

    public synchronized List<MeetingSummary> getMeetingSummaries() {
        List<MeetingSummary> list = new ArrayList<>();
        String json = prefs.getString(KEY_MEETING_SUMMARIES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                MeetingSummary s = MeetingSummary.fromJson(arr.getJSONObject(i));
                if (s != null) {
                    list.add(s);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public synchronized void saveMeetingSummaries(List<MeetingSummary> summaries) {
        JSONArray arr = new JSONArray();
        for (MeetingSummary s : summaries) {
            arr.put(s.toJson());
        }
        prefs.edit().putString(KEY_MEETING_SUMMARIES, arr.toString()).apply();
    }

    /**
     * Parses meeting analysis output and automatically adds parsed action items into the list.
     * Returns the count of action items added.
     */
    public synchronized int addMeeting(String summary, String keyPoints, String actionItemsBlock, String transcript) {
        String meetingId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        List<ActionItem> currentItems = getActionItems();
        int addedCount = 0;

        if (actionItemsBlock != null && !actionItemsBlock.trim().isEmpty()) {
            String[] lines = actionItemsBlock.split("\n");
            Pattern ownerPattern = Pattern.compile("(?i)(?:owner|assignee)[:\\s]+([a-zA-Z0-9\\s,/]+?)(?:[\\),;]|$)");
            Pattern duePattern = Pattern.compile("(?i)(?:due|deadline)[:\\s]+([a-zA-Z0-9\\s,/\\-]+?)(?:[\\),;]|$)");

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;

                // Strip markdown checklist tokens (- [ ], * [ ], [ ], -, *, •, 1., etc.)
                String cleanTask = line.replaceAll("^[\\-*•]\\s*(\\[[ xX]?\\])?\\s*", "")
                                       .replaceAll("^\\d+[.)]\\s*", "")
                                       .trim();

                if (cleanTask.isEmpty() || cleanTask.equalsIgnoreCase("None") || cleanTask.equalsIgnoreCase("No action items")) {
                    continue;
                }

                String owner = "";
                String due = "";

                Matcher om = ownerPattern.matcher(line);
                if (om.find()) {
                    owner = om.group(1).trim();
                }

                Matcher dm = duePattern.matcher(line);
                if (dm.find()) {
                    due = dm.group(1).trim();
                }

                // Clean parentheticals from task title if they only had owner/due
                String displayTitle = cleanTask.replaceAll("\\([Oo]wner:.*?\\)", "")
                                               .replaceAll("\\([Dd]ue:.*?\\)", "")
                                               .replaceAll("\\[[Oo]wner:.*?\\]", "")
                                               .replaceAll("\\[[Dd]ue:.*?\\]", "")
                                               .trim();
                if (displayTitle.isEmpty()) displayTitle = cleanTask;

                ActionItem item = new ActionItem(
                        UUID.randomUUID().toString(),
                        displayTitle,
                        owner,
                        due,
                        false,
                        now,
                        meetingId
                );

                currentItems.add(0, item); // Add to top of list
                addedCount++;
            }
        }

        saveActionItems(currentItems);

        // Store meeting summary record
        List<MeetingSummary> summaries = getMeetingSummaries();
        summaries.add(0, new MeetingSummary(meetingId, now, summary, keyPoints, actionItemsBlock, addedCount));
        if (summaries.size() > 50) {
            summaries = summaries.subList(0, 50); // Keep last 50 meetings
        }
        saveMeetingSummaries(summaries);

        return addedCount;
    }

    public synchronized void addCustomActionItem(String title, String owner, String due) {
        if (title == null || title.trim().isEmpty()) return;
        List<ActionItem> items = getActionItems();
        ActionItem item = new ActionItem(
                UUID.randomUUID().toString(),
                title.trim(),
                owner != null ? owner.trim() : "",
                due != null ? due.trim() : "",
                false,
                System.currentTimeMillis(),
                "manual"
        );
        items.add(0, item);
        saveActionItems(items);
    }

    public synchronized void toggleCompleted(String id) {
        List<ActionItem> items = getActionItems();
        for (ActionItem item : items) {
            if (item.id.equals(id)) {
                item.isCompleted = !item.isCompleted;
                break;
            }
        }
        saveActionItems(items);
    }

    public synchronized void deleteActionItem(String id) {
        List<ActionItem> items = getActionItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                break;
            }
        }
        saveActionItems(items);
    }

    public synchronized void clearCompleted() {
        List<ActionItem> items = getActionItems();
        List<ActionItem> remaining = new ArrayList<>();
        for (ActionItem item : items) {
            if (!item.isCompleted) {
                remaining.add(item);
            }
        }
        saveActionItems(remaining);
    }

    public synchronized void deleteMeeting(String meetingId) {
        List<MeetingSummary> summaries = getMeetingSummaries();
        for (int i = 0; i < summaries.size(); i++) {
            if (summaries.get(i).id.equals(meetingId)) {
                summaries.remove(i);
                break;
            }
        }
        saveMeetingSummaries(summaries);
    }
}
