package com.wisprflow.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Intelligent Typing Suggestion Engine with:
 * - Dynamic pattern learning and word frequency tracking.
 * - Bigram collocations (next-word prediction).
 * - Real-time spelling suggestions, typo detection & autocorrect candidates.
 * - Shortcuts expansion.
 */
public class SuggestionEngine {

    private static final String PREF_NAME = "wispr_suggestion_prefs";
    private static final String KEY_USER_WORDS_JSON = "user_words_frequency_json";
    private static final String KEY_COLLOCATIONS_JSON = "collocations_bigram_json";

    private final SharedPreferences prefs;
    private final ShortcutsManager shortcutsManager;

    private static class ScoredWord {
        final String word;
        final int score;

        ScoredWord(String word, int score) {
            this.word = word;
            this.score = score;
        }
    }

    // Word frequencies: word -> count
    private final Map<String, Integer> wordFrequency = new HashMap<>();

    // Bigram Collocations: prevWord -> (nextWord -> count)
    private final Map<String, Map<String, Integer>> collocations = new HashMap<>();

    // Fast Lookup Map for Common English Typos & Misspellings
    private static final Map<String, String> COMMON_TYPOS = new HashMap<>();

    static {
        // High frequency common misspellings
        COMMON_TYPOS.put("teh", "the");
        COMMON_TYPOS.put("taht", "that");
        COMMON_TYPOS.put("wiht", "with");
        COMMON_TYPOS.put("becuase", "because");
        COMMON_TYPOS.put("becasue", "because");
        COMMON_TYPOS.put("bcuz", "because");
        COMMON_TYPOS.put("recieved", "received");
        COMMON_TYPOS.put("receved", "received");
        COMMON_TYPOS.put("seperate", "separate");
        COMMON_TYPOS.put("separete", "separate");
        COMMON_TYPOS.put("definately", "definitely");
        COMMON_TYPOS.put("definitly", "definitely");
        COMMON_TYPOS.put("adress", "address");
        COMMON_TYPOS.put("addres", "address");
        COMMON_TYPOS.put("tommorow", "tomorrow");
        COMMON_TYPOS.put("tommorrow", "tomorrow");
        COMMON_TYPOS.put("tomorow", "tomorrow");
        COMMON_TYPOS.put("alot", "a lot");
        COMMON_TYPOS.put("beleive", "believe");
        COMMON_TYPOS.put("occured", "occurred");
        COMMON_TYPOS.put("untill", "until");
        COMMON_TYPOS.put("wierd", "weird");
        COMMON_TYPOS.put("thier", "their");
        COMMON_TYPOS.put("writting", "writing");
        COMMON_TYPOS.put("goverment", "government");
        COMMON_TYPOS.put("reccomend", "recommend");
        COMMON_TYPOS.put("accomodate", "accommodate");
        COMMON_TYPOS.put("calender", "calendar");
        COMMON_TYPOS.put("concious", "conscious");
        COMMON_TYPOS.put("neccessary", "necessary");
        COMMON_TYPOS.put("necesary", "necessary");
        COMMON_TYPOS.put("truely", "truly");
        COMMON_TYPOS.put("embarass", "embarrass");
        COMMON_TYPOS.put("oppurtunity", "opportunity");
        COMMON_TYPOS.put("schedul", "schedule");
        COMMON_TYPOS.put("mispell", "misspell");
        COMMON_TYPOS.put("suprise", "surprise");
        COMMON_TYPOS.put("succesful", "successful");
        COMMON_TYPOS.put("intrest", "interest");
        COMMON_TYPOS.put("bussiness", "business");
        COMMON_TYPOS.put("buisness", "business");
        COMMON_TYPOS.put("collegue", "colleague");
        COMMON_TYPOS.put("relevent", "relevant");
        COMMON_TYPOS.put("maintainance", "maintenance");
        COMMON_TYPOS.put("familar", "familiar");
        COMMON_TYPOS.put("priviledge", "privilege");
        COMMON_TYPOS.put("noticable", "noticeable");
        COMMON_TYPOS.put("guarentee", "guarantee");
        COMMON_TYPOS.put("experiance", "experience");
        COMMON_TYPOS.put("enviroment", "environment");
        COMMON_TYPOS.put("diferent", "different");
        COMMON_TYPOS.put("commited", "committed");
        COMMON_TYPOS.put("begining", "beginning");
        COMMON_TYPOS.put("basicly", "basically");
        COMMON_TYPOS.put("appologies", "apologies");
        COMMON_TYPOS.put("agian", "again");
        COMMON_TYPOS.put("acceptible", "acceptable");
        COMMON_TYPOS.put("acheive", "achieve");
        COMMON_TYPOS.put("aquire", "acquire");
        COMMON_TYPOS.put("comming", "coming");
        COMMON_TYPOS.put("doesnt", "doesn't");
        COMMON_TYPOS.put("dont", "don't");
        COMMON_TYPOS.put("cant", "can't");
        COMMON_TYPOS.put("wont", "won't");
        COMMON_TYPOS.put("didnt", "didn't");
        COMMON_TYPOS.put("couldnt", "couldn't");
        COMMON_TYPOS.put("shouldnt", "shouldn't");
        COMMON_TYPOS.put("wouldnt", "wouldn't");
        COMMON_TYPOS.put("whats", "what's");
        COMMON_TYPOS.put("its", "it's");
        COMMON_TYPOS.put("youre", "you're");
        COMMON_TYPOS.put("theyre", "they're");
        COMMON_TYPOS.put("weve", "we've");
    }

    // Default base vocabulary
    private static final String[] BASE_WORDS = {
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "I", "it", "for", "not", "on", "with",
        "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her",
        "she", "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up",
        "out", "if", "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time",
        "no", "just", "him", "know", "take", "people", "into", "year", "your", "good", "some", "could",
        "them", "see", "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
        "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way", "even",
        "new", "want", "because", "any", "these", "give", "day", "most", "us", "great", "thanks", "thank",
        "morning", "evening", "afternoon", "night", "tomorrow", "today", "yesterday", "meeting", "please",
        "help", "send", "call", "email", "check", "update", "project", "ready", "done", "fine", "sure",
        "happy", "welcome", "best", "regards", "wishes", "hello", "hi", "hey", "awesome", "perfect",
        "sounds", "looking", "forward", "urgent", "client", "sales", "status", "team", "review", "schedule",
        "business", "address", "company", "customer", "invoice", "payment", "received", "separate",
        "definitely", "believe", "opportunity", "contact", "phone", "number", "message", "service",
        "support", "contract", "proposal", "agreement", "account", "manager", "product", "price",
        "discount", "order", "delivery", "confirm", "available", "discuss", "connect", "question",
        "answer", "details", "information", "attachment", "feedback", "solution", "issue", "problem",
        "resolved", "experience", "different", "always", "coming", "again", "truly", "basically"
    };

    public SuggestionEngine(Context context, ShortcutsManager shortcutsManager) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.shortcutsManager = shortcutsManager;
        initializeBaseVocabulary();
        loadLearnedPatterns();
    }

    private void initializeBaseVocabulary() {
        for (String w : BASE_WORDS) {
            wordFrequency.put(w.toLowerCase(Locale.US), 5);
        }

        // Common English collocations
        seedCollocation("good", "morning", 20);
        seedCollocation("good", "afternoon", 18);
        seedCollocation("good", "evening", 18);
        seedCollocation("good", "night", 15);
        seedCollocation("good", "day", 14);
        seedCollocation("good", "luck", 12);
        seedCollocation("good", "job", 10);

        seedCollocation("thank", "you", 25);
        seedCollocation("thank", "you so much", 20);
        seedCollocation("thanks", "for", 18);
        seedCollocation("thanks", "a lot", 15);

        seedCollocation("how", "are you", 25);
        seedCollocation("how", "about", 18);
        seedCollocation("how", "is it going", 15);

        seedCollocation("see", "you", 22);
        seedCollocation("see", "you soon", 18);
        seedCollocation("see", "you tomorrow", 15);

        seedCollocation("let", "me know", 25);
        seedCollocation("let", "us know", 20);

        seedCollocation("please", "find attached", 22);
        seedCollocation("please", "let me know", 20);
        seedCollocation("please", "check", 18);

        seedCollocation("looking", "forward to", 25);
        seedCollocation("looking", "good", 15);

        seedCollocation("best", "regards", 25);
        seedCollocation("best", "wishes", 20);

        seedCollocation("nice", "to meet you", 25);
        seedCollocation("nice", "work", 15);

        seedCollocation("can", "we connect", 20);
        seedCollocation("can", "you please", 18);
        seedCollocation("contact", "card", 20);
        seedCollocation("business", "card", 20);
    }

    private void seedCollocation(String w1, String w2, int weight) {
        String key1 = w1.toLowerCase(Locale.US);
        Map<String, Integer> map = collocations.computeIfAbsent(key1, k -> new HashMap<>());
        map.put(w2, weight);
    }

    private void loadLearnedPatterns() {
        String wordsJson = prefs.getString(KEY_USER_WORDS_JSON, null);
        if (wordsJson != null) {
            try {
                JSONObject obj = new JSONObject(wordsJson);
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String w = it.next();
                    wordFrequency.put(w, obj.getInt(w));
                }
            } catch (JSONException ignored) {}
        }

        String collocationsJson = prefs.getString(KEY_COLLOCATIONS_JSON, null);
        if (collocationsJson != null) {
            try {
                JSONObject obj = new JSONObject(collocationsJson);
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String w1 = it.next();
                    JSONObject inner = obj.getJSONObject(w1);
                    Map<String, Integer> map = collocations.computeIfAbsent(w1, k -> new HashMap<>());
                    Iterator<String> innerIt = inner.keys();
                    while (innerIt.hasNext()) {
                        String w2 = innerIt.next();
                        map.put(w2, inner.getInt(w2));
                    }
                }
            } catch (JSONException ignored) {}
        }
    }

    public synchronized void saveLearnedPatterns() {
        try {
            JSONObject wordsObj = new JSONObject();
            for (Map.Entry<String, Integer> e : wordFrequency.entrySet()) {
                if (e.getValue() > 5) {
                    wordsObj.put(e.getKey(), e.getValue());
                }
            }
            prefs.edit().putString(KEY_USER_WORDS_JSON, wordsObj.toString()).apply();

            JSONObject collocationsObj = new JSONObject();
            for (Map.Entry<String, Map<String, Integer>> e : collocations.entrySet()) {
                JSONObject inner = new JSONObject();
                for (Map.Entry<String, Integer> innerE : e.getValue().entrySet()) {
                    inner.put(innerE.getKey(), innerE.getValue());
                }
                collocationsObj.put(e.getKey(), inner);
            }
            prefs.edit().putString(KEY_COLLOCATIONS_JSON, collocationsObj.toString()).apply();
        } catch (JSONException ignored) {}
    }

    public synchronized void recordWord(String word) {
        if (word == null || word.trim().length() < 2) return;
        String clean = word.trim().toLowerCase(Locale.US);
        int current = wordFrequency.getOrDefault(clean, 0);
        wordFrequency.put(clean, current + 2);
    }

    public synchronized void recordWordPair(String prevWord, String currentWord) {
        if (prevWord == null || currentWord == null) return;
        String w1 = prevWord.trim().toLowerCase(Locale.US);
        String w2 = currentWord.trim();
        if (w1.isEmpty() || w2.isEmpty()) return;

        recordWord(w2);

        Map<String, Integer> map = collocations.computeIfAbsent(w1, k -> new HashMap<>());
        int count = map.getOrDefault(w2, 0);
        map.put(w2, count + 3);
    }

    /**
     * Calculates Damerau-Levenshtein distance between two strings for spelling correction.
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) return 999;
        int len1 = s1.length();
        int len2 = s2.length();
        if (Math.abs(len1 - len2) > 2) return 999;

        int[] costs = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) costs[j] = j;

        for (int i = 1; i <= len1; i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= len2; j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[len2];
    }

    /**
     * Generates up to maxCount suggestions based on current typing context,
     * including SPELLING SUGGESTIONS, shortcuts expansion, prefix matching, and collocations.
     */
    public synchronized List<String> getSuggestions(String prevWord, String currentPrefix, int maxCount) {
        List<String> results = new ArrayList<>();
        Set<String> added = new HashSet<>();

        String cleanPrefix = currentPrefix != null ? currentPrefix.trim() : "";
        String cleanPrev = prevWord != null ? prevWord.trim().toLowerCase(Locale.US) : "";

        // 1. Check for exact or prefix shortcut matches first
        if (!cleanPrefix.isEmpty() && shortcutsManager != null) {
            String exactExpansion = shortcutsManager.getExpansion(cleanPrefix);
            if (exactExpansion != null) {
                results.add(exactExpansion);
                added.add(exactExpansion.toLowerCase(Locale.US));
            } else {
                List<ShortcutsManager.ShortcutItem> matches = shortcutsManager.findMatchingShortcuts(cleanPrefix);
                for (ShortcutsManager.ShortcutItem item : matches) {
                    if (results.size() < maxCount && !added.contains(item.expansion.toLowerCase(Locale.US))) {
                        results.add(item.expansion);
                        added.add(item.expansion.toLowerCase(Locale.US));
                    }
                }
            }
        }

        // 2. SPELLING SUGGESTIONS & Autocorrect lookup
        if (!cleanPrefix.isEmpty() && cleanPrefix.length() >= 3) {
            String lower = cleanPrefix.toLowerCase(Locale.US);
            // Check known typo map
            if (COMMON_TYPOS.containsKey(lower)) {
                String correction = COMMON_TYPOS.get(lower);
                if (Character.isUpperCase(cleanPrefix.charAt(0)) && correction.length() > 0) {
                    correction = Character.toUpperCase(correction.charAt(0)) + correction.substring(1);
                }
                if (!added.contains(correction.toLowerCase(Locale.US))) {
                    results.add(correction);
                    added.add(correction.toLowerCase(Locale.US));
                }
            }
        }

        // 3. If typing a prefix: filter words starting with prefix ordered by learned frequency
        if (!cleanPrefix.isEmpty()) {
            final String lowerPrefix = cleanPrefix.toLowerCase(Locale.US);
            boolean isCapitalized = Character.isUpperCase(cleanPrefix.charAt(0));

            // Candidate matching words
            List<ScoredWord> candidates = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : wordFrequency.entrySet()) {
                if (entry.getKey().startsWith(lowerPrefix)) {
                    candidates.add(new ScoredWord(entry.getKey(), entry.getValue()));
                }
            }

            // Also check if collocations from prevWord match the prefix
            if (!cleanPrev.isEmpty() && collocations.containsKey(cleanPrev)) {
                Map<String, Integer> colMap = collocations.get(cleanPrev);
                for (Map.Entry<String, Integer> ce : colMap.entrySet()) {
                    if (ce.getKey().toLowerCase(Locale.US).startsWith(lowerPrefix)) {
                        candidates.add(new ScoredWord(ce.getKey(), ce.getValue() * 2));
                    }
                }
            }

            // If few or no prefix matches found, check Levenshtein distance for spelling corrections
            if (candidates.size() < 2 && lowerPrefix.length() >= 3) {
                int maxDist = lowerPrefix.length() >= 5 ? 2 : 1;
                for (Map.Entry<String, Integer> entry : wordFrequency.entrySet()) {
                    String dictWord = entry.getKey();
                    if (Math.abs(dictWord.length() - lowerPrefix.length()) <= maxDist) {
                        int dist = levenshteinDistance(lowerPrefix, dictWord);
                        if (dist <= maxDist) {
                            candidates.add(new ScoredWord(dictWord, (10 - dist * 4) + entry.getValue()));
                        }
                    }
                }
            }

            // Sort candidates by frequency / match score descending
            Collections.sort(candidates, (a, b) -> Integer.compare(b.score, a.score));

            for (ScoredWord c : candidates) {
                if (results.size() >= maxCount) break;
                String word = c.word;
                if (isCapitalized && word.length() > 0) {
                    word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
                }
                if (!added.contains(word.toLowerCase(Locale.US))) {
                    results.add(word);
                    added.add(word.toLowerCase(Locale.US));
                }
            }
        } 
        // 4. If NOT typing a prefix (at a word boundary): Predict next collocated words from prevWord!
        else if (!cleanPrev.isEmpty() && collocations.containsKey(cleanPrev)) {
            Map<String, Integer> colMap = collocations.get(cleanPrev);
            List<Map.Entry<String, Integer>> sortedCollocations = new ArrayList<>(colMap.entrySet());
            Collections.sort(sortedCollocations, (a, b) -> b.getValue().compareTo(a.getValue()));

            for (Map.Entry<String, Integer> entry : sortedCollocations) {
                if (results.size() >= maxCount) break;
                String word = entry.getKey();
                if (!added.contains(word.toLowerCase(Locale.US))) {
                    results.add(word);
                    added.add(word.toLowerCase(Locale.US));
                }
            }
        }

        // 5. Fill remaining slots with top common words or helpful contextual phrases
        if (results.size() < maxCount) {
            String[] fallbacks = cleanPrev.isEmpty() ? 
                new String[]{"I", "Hello", "Thanks"} : 
                new String[]{"you", "to", "the", "and", "it"};

            for (String fb : fallbacks) {
                if (results.size() >= maxCount) break;
                if (!added.contains(fb.toLowerCase(Locale.US))) {
                    results.add(fb);
                    added.add(fb.toLowerCase(Locale.US));
                }
            }
        }

        return results;
    }

    public synchronized int getLearnedWordsCount() {
        int count = 0;
        for (int freq : wordFrequency.values()) {
            if (freq > 5) count++;
        }
        return count;
    }

    public synchronized int getLearnedCollocationsCount() {
        int count = 0;
        for (Map<String, Integer> map : collocations.values()) {
            count += map.size();
        }
        return count;
    }

    public synchronized void resetLearnedPatterns() {
        wordFrequency.clear();
        collocations.clear();
        prefs.edit().remove(KEY_USER_WORDS_JSON).remove(KEY_COLLOCATIONS_JSON).apply();
        initializeBaseVocabulary();
    }
}
