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

    // Fast Lookup Map for Common English Typos, Contractions & Misspellings
    private static final Map<String, String> COMMON_TYPOS = new HashMap<>();

    static {
        // High frequency pronouns & contractions
        COMMON_TYPOS.put("i", "I");
        COMMON_TYPOS.put("im", "I'm");
        COMMON_TYPOS.put("ive", "I've");
        COMMON_TYPOS.put("id", "I'd");
        COMMON_TYPOS.put("ill", "I'll");
        COMMON_TYPOS.put("i'm", "I'm");
        COMMON_TYPOS.put("i've", "I've");
        COMMON_TYPOS.put("i'd", "I'd");
        COMMON_TYPOS.put("i'll", "I'll");

        COMMON_TYPOS.put("dont", "don't");
        COMMON_TYPOS.put("cant", "can't");
        COMMON_TYPOS.put("wont", "won't");
        COMMON_TYPOS.put("didnt", "didn't");
        COMMON_TYPOS.put("isnt", "isn't");
        COMMON_TYPOS.put("arent", "aren't");
        COMMON_TYPOS.put("wasnt", "wasn't");
        COMMON_TYPOS.put("werent", "weren't");
        COMMON_TYPOS.put("hasnt", "hasn't");
        COMMON_TYPOS.put("havent", "haven't");
        COMMON_TYPOS.put("hadnt", "hadn't");
        COMMON_TYPOS.put("doesnt", "doesn't");
        COMMON_TYPOS.put("couldnt", "couldn't");
        COMMON_TYPOS.put("shouldnt", "shouldn't");
        COMMON_TYPOS.put("wouldnt", "wouldn't");

        COMMON_TYPOS.put("youre", "you're");
        COMMON_TYPOS.put("theyre", "they're");
        COMMON_TYPOS.put("weve", "we've");
        COMMON_TYPOS.put("youve", "you've");
        COMMON_TYPOS.put("theyve", "they've");
        COMMON_TYPOS.put("youll", "you'll");
        COMMON_TYPOS.put("theyll", "they'll");
        COMMON_TYPOS.put("well", "we'll"); // common contraction intent

        COMMON_TYPOS.put("whats", "what's");
        COMMON_TYPOS.put("thats", "that's");
        COMMON_TYPOS.put("theres", "there's");
        COMMON_TYPOS.put("heres", "here's");
        COMMON_TYPOS.put("wheres", "where's");
        COMMON_TYPOS.put("whos", "who's");
        COMMON_TYPOS.put("hows", "how's");
        COMMON_TYPOS.put("its", "it's");
        COMMON_TYPOS.put("lets", "let's");

        // Tech acronyms
        COMMON_TYPOS.put("gpu", "GPU");
        COMMON_TYPOS.put("cpu", "CPU");
        COMMON_TYPOS.put("ai", "AI");
        COMMON_TYPOS.put("api", "API");
        COMMON_TYPOS.put("ui", "UI");
        COMMON_TYPOS.put("ux", "UX");
        COMMON_TYPOS.put("fyi", "FYI");
        COMMON_TYPOS.put("asap", "ASAP");
        COMMON_TYPOS.put("idk", "I don't know");
        COMMON_TYPOS.put("omw", "on my way");
        COMMON_TYPOS.put("thx", "thanks");
        COMMON_TYPOS.put("pls", "please");
        COMMON_TYPOS.put("plz", "please");

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
        COMMON_TYPOS.put("captial", "capital");
        COMMON_TYPOS.put("correctoon", "correction");
        COMMON_TYPOS.put("correctoons", "corrections");
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
        "resolved", "experience", "different", "always", "coming", "again", "truly", "basically",
        "capital", "correction", "capitalization", "keyboard", "smart", "quick", "voice", "scan",
        "appreciate", "assistance", "follow", "notes", "action", "items", "summary", "convenient",
        "presentation", "document", "contract", "partnership", "collaboration", "reminder", "agenda"
    };

    /**
     * Contextual Emoji predictions matching high-frequency keywords
     */
    public static String getEmojiForWord(String word) {
        if (word == null) return null;
        String lower = word.toLowerCase(Locale.US).trim();
        switch (lower) {
            case "happy": case "smile": case "smiling": case "glad": case "yay": return "😊";
            case "love": case "heart": case "loved": case "loving": return "❤️";
            case "fire": case "lit": case "hot": return "🔥";
            case "good": case "great": case "nice": case "cool": case "thumbs": return "👍";
            case "thanks": case "thank": case "thankyou": case "grateful": case "appreciate": return "🙏";
            case "ok": case "okay": case "perfect": case "sure": return "👌";
            case "meeting": case "schedule": case "calendar": case "appointment": return "📅";
            case "call": case "phone": return "📞";
            case "coffee": case "tea": case "cafe": return "☕";
            case "congrats": case "congratulations": case "celebrate": case "party": return "🎉";
            case "done": case "checked": case "completed": case "yes": return "✅";
            case "idea": case "smart": case "thinking": return "💡";
            case "rocket": case "launch": case "grow": return "🚀";
            case "money": case "cash": case "paid": case "invoice": case "price": return "💰";
            case "deal": case "handshake": case "partner": return "🤝";
            case "car": case "drive": return "🚗";
            case "plane": case "flight": case "travel": return "✈️";
            case "laugh": case "funny": case "lol": case "haha": return "😂";
            case "sad": case "cry": case "sorry": return "🥺";
            case "star": case "win": case "winner": case "best": return "⭐";
            case "work": case "office": case "business": return "💼";
            case "target": case "goal": return "🎯";
            case "food": case "lunch": case "dinner": case "pizza": return "🍕";
            default: return null;
        }
    }

    /**
     * Resolves automatic error corrections (e.g. standalone "i" -> "I", "dont" -> "don't", "teh" -> "the").
     * Preserves capitalization if original input was capitalized or uppercase.
     */
    public String getAutocorrect(String rawWord) {
        if (rawWord == null || rawWord.trim().isEmpty()) return null;
        String trimmed = rawWord.trim();

        // Standalone 'i' must always be capitalized to 'I'
        if (trimmed.equals("i")) {
            return "I";
        }

        String lower = trimmed.toLowerCase(Locale.US);
        if (COMMON_TYPOS.containsKey(lower)) {
            String replacement = COMMON_TYPOS.get(lower);
            if (replacement == null) return null;

            // Preserve all-caps (e.g. TEH -> THE, DONT -> DON'T)
            if (trimmed.length() > 1 && trimmed.equals(trimmed.toUpperCase(Locale.US))) {
                return replacement.toUpperCase(Locale.US);
            }
            // Preserve TitleCase (e.g. Teh -> The, Dont -> Don't, Im -> I'm)
            if (Character.isUpperCase(trimmed.charAt(0)) && replacement.length() > 0) {
                return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
            }
            return replacement;
        }

        return null;
    }

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

        // Common English collocations & next-word predictions
        seedCollocation("i", "am", 30);
        seedCollocation("i", "will", 28);
        seedCollocation("i", "have", 28);
        seedCollocation("i", "would", 25);
        seedCollocation("i", "think", 24);
        seedCollocation("i", "can", 22);
        seedCollocation("i", "appreciate", 20);
        seedCollocation("i", "hope", 18);

        seedCollocation("good", "morning", 25);
        seedCollocation("good", "afternoon", 20);
        seedCollocation("good", "evening", 20);
        seedCollocation("good", "night", 18);
        seedCollocation("good", "day", 15);
        seedCollocation("good", "luck", 14);
        seedCollocation("good", "job", 14);

        seedCollocation("thank", "you", 30);
        seedCollocation("thank", "you so much", 26);
        seedCollocation("thank", "you very much", 22);
        seedCollocation("thanks", "for", 24);
        seedCollocation("thanks", "for your help", 22);
        seedCollocation("thanks", "a lot", 18);

        seedCollocation("how", "are you", 30);
        seedCollocation("how", "about", 22);
        seedCollocation("how", "is it going", 20);
        seedCollocation("how", "was your day", 18);

        seedCollocation("see", "you", 25);
        seedCollocation("see", "you soon", 20);
        seedCollocation("see", "you tomorrow", 18);

        seedCollocation("let", "me know", 30);
        seedCollocation("let", "us know", 24);
        seedCollocation("let", "me check", 20);

        seedCollocation("please", "let me know", 28);
        seedCollocation("please", "find attached", 26);
        seedCollocation("please", "check", 22);
        seedCollocation("please", "confirm", 20);
        seedCollocation("please", "feel free to", 18);

        seedCollocation("looking", "forward to", 30);
        seedCollocation("looking", "forward to hearing from you", 25);
        seedCollocation("looking", "good", 16);

        seedCollocation("best", "regards", 30);
        seedCollocation("best", "wishes", 24);

        seedCollocation("nice", "to meet you", 26);
        seedCollocation("nice", "work", 16);

        seedCollocation("can", "we connect", 22);
        seedCollocation("can", "you please", 20);
        seedCollocation("can", "we schedule a call", 20);

        seedCollocation("sounds", "great", 25);
        seedCollocation("sounds", "good", 24);
        seedCollocation("sounds", "like a plan", 20);

        seedCollocation("contact", "card", 22);
        seedCollocation("business", "card", 22);
        seedCollocation("action", "items", 24);
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

        // 1. Standalone 'i' priority (I, I'm, I'll, I've, I'd)
        if (cleanPrefix.equalsIgnoreCase("i")) {
            String[] iVariants = {"I", "I'm", "I'll", "I've", "I'd"};
            for (String iv : iVariants) {
                if (results.size() < maxCount && !added.contains(iv.toLowerCase(Locale.US))) {
                    results.add(iv);
                    added.add(iv.toLowerCase(Locale.US));
                }
            }
        }

        // 2. Direct Autocorrect / Typo resolution (placed FIRST as primary center candidate!)
        String directCorrection = getAutocorrect(cleanPrefix);
        if (directCorrection != null && !directCorrection.equalsIgnoreCase(cleanPrefix)) {
            if (!added.contains(directCorrection.toLowerCase(Locale.US))) {
                results.add(directCorrection);
                added.add(directCorrection.toLowerCase(Locale.US));
            }
        }

        // 3. Check for exact or prefix shortcut matches next
        if (!cleanPrefix.isEmpty() && shortcutsManager != null) {
            String exactExpansion = shortcutsManager.getExpansion(cleanPrefix);
            if (exactExpansion != null) {
                if (!added.contains(exactExpansion.toLowerCase(Locale.US))) {
                    results.add(exactExpansion);
                    added.add(exactExpansion.toLowerCase(Locale.US));
                }
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

        // 4. If typing a prefix: filter words starting with prefix ordered by learned frequency
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
        // 5. If NOT typing a prefix (at a word boundary): Predict next collocated words from prevWord!
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
