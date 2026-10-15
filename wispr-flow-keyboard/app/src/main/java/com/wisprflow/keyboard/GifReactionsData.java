package com.wisprflow.keyboard;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * High-Performance GIF Engine & Curated Reactions Catalog.
 * - Supports rich keyword tagging for instant contextual text-to-GIF suggestions.
 * - Local zero-latency curated database covering all primary emotional expressions and reactions.
 * - Live online GIF search (powered by Giphy/Tenor public endpoints) for unlimited dynamic queries.
 * - Dispatches actual GIF image files via InputContentInfoCompat (NOT raw URL text).
 */
public class GifReactionsData {

    private static final String TAG = "GifReactionsData";
    private static final String GIPHY_API_KEY = "LIVDSRZULELA"; // Public Giphy Explorer Key

    public static class GifItem {
        public final String title;
        public final String emojiPreview;
        public final String gifUrl;
        public final String fallbackText;
        public final List<String> tags;
        public final String category;

        public GifItem(String title, String emojiPreview, String gifUrl, String fallbackText, String category, String... tags) {
            this.title = title;
            this.emojiPreview = emojiPreview;
            this.gifUrl = gifUrl;
            this.fallbackText = fallbackText;
            this.category = category;
            this.tags = tags != null ? Arrays.asList(tags) : Collections.emptyList();
        }
    }

    public static final Map<String, List<GifItem>> CATEGORIES = new LinkedHashMap<>();
    private static final List<GifItem> ALL_GIFS = new ArrayList<>();

    static {
        // 1. 🔥 Trending & Most Popular
        List<GifItem> trending = new ArrayList<>();
        trending.add(new GifItem("Thumbs Up", "👍", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "👍 [Thumbs Up]", "🔥 Trending", "thumbs up", "ok", "cool", "approved", "agree", "nice", "good"));
        trending.add(new GifItem("Standing Ovation", "👏", "https://media.giphy.com/media/fnK0jeA8vIh2QLq3IZ/giphy.gif", "👏 [Applause]", "🔥 Trending", "applause", "clapping", "good job", "bravo", "congrats", "well done"));
        trending.add(new GifItem("DiCaprio Cheers", "🥂", "https://media.giphy.com/media/GCLlQnV7dXZ2E/giphy.gif", "🥂 [Cheers!]", "🔥 Trending", "cheers", "toast", "gatsby", "congrats", "celebrate", "awesome"));
        trending.add(new GifItem("Party Celebrate", "🎉", "https://media.giphy.com/media/artj92V8o75VPL7AeQ/giphy.gif", "🎉 [Celebrate!]", "🔥 Trending", "celebrate", "party", "yay", "happy birthday", "woohoo"));
        trending.add(new GifItem("Mind Blown", "🤯", "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif", "🤯 [Mind Blown]", "🔥 Trending", "mind blown", "mindblown", "wow", "unbelievable", "omg", "insane"));
        trending.add(new GifItem("Laugh Out Loud", "😂", "https://media.giphy.com/media/3oEjHAUOqG3lSS0f1C/giphy.gif", "😂 [LOL]", "🔥 Trending", "lol", "lmao", "haha", "funny", "laugh", "rofl", "hilarious"));
        trending.add(new GifItem("Love & Heart", "❤️", "https://media.giphy.com/media/26BRv0ThflsDTqUXa/giphy.gif", "❤️ [Love]", "🔥 Trending", "love", "heart", "romantic", "adoring", "thanks", "sweet"));
        trending.add(new GifItem("Yes Absolutely", "✅", "https://media.giphy.com/media/n4oKYFlAcv2AU/giphy.gif", "✅ [Yes!]", "🔥 Trending", "yes", "nod", "absolutely", "agree", "correct", "yep", "sure"));
        trending.add(new GifItem("No Way Facepalm", "🤦", "https://media.giphy.com/media/3og0INyCmHlNylks9O/giphy.gif", "🤦 [Facepalm]", "🔥 Trending", "facepalm", "no", "smh", "why", "fail", "oops"));
        trending.add(new GifItem("High Five", "🙌", "https://media.giphy.com/media/3oEjHV0z8S7WM4MwnK/giphy.gif", "🙌 [High Five]", "🔥 Trending", "high five", "teamwork", "partner", "boom", "success"));
        CATEGORIES.put("🔥 Trending", trending);
        ALL_GIFS.addAll(trending);

        // 2. 👏 Applause & Cheers
        List<GifItem> applause = new ArrayList<>();
        applause.add(new GifItem("Wild Applause", "👏", "https://media.giphy.com/media/fnK0jeA8vIh2QLq3IZ/giphy.gif", "👏 [Applause]", "👏 Applause", "applause", "clap", "cheer", "bravo", "good job"));
        applause.add(new GifItem("Leonardo Toast", "🥂", "https://media.giphy.com/media/GCLlQnV7dXZ2E/giphy.gif", "🥂 [Cheers!]", "👏 Applause", "cheers", "toast", "awesome", "legend", "celebrate"));
        applause.add(new GifItem("Proud Clap", "🙌", "https://media.giphy.com/media/nbvFVPiEiJH6JOGIok/giphy.gif", "🙌 [Proud]", "👏 Applause", "clap", "proud", "well done", "awesome", "congratulations"));
        applause.add(new GifItem("Mic Drop", "🎤", "https://media.giphy.com/media/3o7qDSOvfaCO9b3MlO/giphy.gif", "🎤 [Mic Drop]", "👏 Applause", "mic drop", "done", "finished", "boom", "savage", "won"));
        applause.add(new GifItem("High Five Team", "✋", "https://media.giphy.com/media/3oEjHV0z8S7WM4MwnK/giphy.gif", "✋ [High Five]", "👏 Applause", "high five", "congrats", "team", "celebrate"));
        CATEGORIES.put("👏 Applause", applause);
        ALL_GIFS.addAll(applause);

        // 3. 😂 Laugh & Fun
        List<GifItem> laugh = new ArrayList<>();
        laugh.add(new GifItem("Can't Stop Laughing", "😂", "https://media.giphy.com/media/3oEjHAUOqG3lSS0f1C/giphy.gif", "😂 [LOL]", "😂 Laugh & Fun", "lol", "laugh", "haha", "rofl", "hilarious"));
        laugh.add(new GifItem("Chuckling Baby", "👶", "https://media.giphy.com/media/10JhviFuU2gWD6/giphy.gif", "👶 [Haha]", "😂 Laugh & Fun", "laugh", "baby", "cute", "haha", "funny"));
        laugh.add(new GifItem("Popcorn Drama", "🍿", "https://media.giphy.com/media/gl0mkIZOW6Nwc/giphy.gif", "🍿 [Popcorn]", "😂 Laugh & Fun", "popcorn", "drama", "watching", "listening", "tell me"));
        laugh.add(new GifItem("Snoopy Happy Dance", "🕺", "https://media.giphy.com/media/blSTtZehjAZ8I/giphy.gif", "🕺 [Dance]", "😂 Laugh & Fun", "dance", "dancing", "happy", "groove", "vibe"));
        laugh.add(new GifItem("Snicker Smile", "😏", "https://media.giphy.com/media/NdKVEei95yvIY/giphy.gif", "😏 [Hehe]", "😂 Laugh & Fun", "smile", "smirk", "hehe", "gotcha"));
        CATEGORIES.put("😂 Laugh & Fun", laugh);
        ALL_GIFS.addAll(laugh);

        // 4. 🎉 Celebrate & Victory
        List<GifItem> celebrate = new ArrayList<>();
        celebrate.add(new GifItem("Confetti Celebration", "🎉", "https://media.giphy.com/media/artj92V8o75VPL7AeQ/giphy.gif", "🎉 [Celebrate]", "🎉 Celebrate", "celebrate", "party", "confetti", "woohoo", "birthday"));
        celebrate.add(new GifItem("Victory Dance", "💃", "https://media.giphy.com/media/l41lI4bYmcsPJX9Go/giphy.gif", "💃 [Victory]", "🎉 Celebrate", "victory", "win", "dance", "success", "yes"));
        celebrate.add(new GifItem("Fire & Lit", "🔥", "https://media.giphy.com/media/l46CqETdipTOTi55m/giphy.gif", "🔥 [Lit!]", "🎉 Celebrate", "fire", "lit", "hot", "epic", "amazing"));
        celebrate.add(new GifItem("Fireworks Show", "🎆", "https://media.giphy.com/media/26tOZ42Mg6pbTUPHW/giphy.gif", "🎆 [Fireworks]", "🎉 Celebrate", "fireworks", "new year", "sparkle", "festive"));
        CATEGORIES.put("🎉 Celebrate", celebrate);
        ALL_GIFS.addAll(celebrate);

        // 5. 👍 Agree & Thumbs Up
        List<GifItem> agree = new ArrayList<>();
        agree.add(new GifItem("Classic Thumbs Up", "👍", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "👍 [Thumbs Up]", "👍 Agree", "thumbs up", "ok", "great", "cool", "approved"));
        agree.add(new GifItem("Nodding in Agreement", "🙆‍♂️", "https://media.giphy.com/media/n4oKYFlAcv2AU/giphy.gif", "🙆‍♂️ [Agree]", "👍 Agree", "nod", "yes", "agree", "sure", "correct"));
        agree.add(new GifItem("No Problem / Smooth", "👌", "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif", "👌 [No Problem]", "👍 Agree", "no problem", "smooth", "easy", "perfect"));
        agree.add(new GifItem("Rock On Legend", "🤘", "https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/giphy.gif", "🤘 [Rock On]", "👍 Agree", "rock on", "cool", "fire", "legend"));
        CATEGORIES.put("👍 Agree", agree);
        ALL_GIFS.addAll(agree);

        // 6. 🤔 Thinking & Confused
        List<GifItem> thinking = new ArrayList<>();
        thinking.add(new GifItem("Deep Pondering", "🤔", "https://media.giphy.com/media/a5viI92PAF89q/giphy.gif", "🤔 [Thinking]", "🤔 Thinking", "thinking", "ponder", "hmm", "curious", "question"));
        thinking.add(new GifItem("Confused Travolta", "🤷‍♂️", "https://media.giphy.com/media/g01ZnwAUvutuK8GIQn/giphy.gif", "🤷‍♂️ [Where?]", "🤔 Thinking", "confused", "what", "where", "lost", "idk", "huh"));
        thinking.add(new GifItem("Mind Blown Explosion", "🤯", "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif", "🤯 [Mind Blown]", "🤔 Thinking", "mind blown", "wow", "shocked", "omg"));
        thinking.add(new GifItem("Searching / Looking", "🧐", "https://media.giphy.com/media/l2Je0oOcT4cioSIfu/giphy.gif", "🧐 [Inspecting]", "🤔 Thinking", "looking", "inspect", "search", "detective"));
        CATEGORIES.put("🤔 Thinking", thinking);
        ALL_GIFS.addAll(thinking);

        // 7. ❤️ Love & Gratitude
        List<GifItem> love = new ArrayList<>();
        love.add(new GifItem("Heart Love", "❤️", "https://media.giphy.com/media/26BRv0ThflsDTqUXa/giphy.gif", "❤️ [Love]", "❤️ Love & Thanks", "love", "heart", "sweet", "sweetheart", "romantic"));
        love.add(new GifItem("Thank You So Much", "🙏", "https://media.giphy.com/media/osjgQPWRx3cac/giphy.gif", "🙏 [Thank You]", "❤️ Love & Thanks", "thank you", "thanks", "grateful", "appreciate", "blessed"));
        love.add(new GifItem("Warm Hug", "🤗", "https://media.giphy.com/media/3bqtLDeiDtwhq/giphy.gif", "🤗 [Hug]", "❤️ Love & Thanks", "hug", "warm", "cuddle", "comfort", "friend"));
        love.add(new GifItem("Sending Kisses", "😘", "https://media.giphy.com/media/l4pTdcifPZLpDjL1e/giphy.gif", "😘 [Kiss]", "❤️ Love & Thanks", "kiss", "kisses", "love", "goodnight"));
        CATEGORIES.put("❤️ Love & Thanks", love);
        ALL_GIFS.addAll(love);

        // 8. 💼 Work & Productivity
        List<GifItem> work = new ArrayList<>();
        work.add(new GifItem("Furious Typing", "💻", "https://media.giphy.com/media/3oKIPnAiaMCws8nOsE/giphy.gif", "💻 [Working]", "💼 Work & Hustle", "work", "typing", "coding", "busy", "hustle", "computer"));
        work.add(new GifItem("Coffee Fuel", "☕", "https://media.giphy.com/media/oZEBLugoTNRxS/giphy.gif", "☕ [Coffee]", "💼 Work & Hustle", "coffee", "morning", "good morning", "caffeine", "wake up"));
        work.add(new GifItem("Deal Signed / Handshake", "🤝", "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif", "🤝 [Deal]", "💼 Work & Hustle", "deal", "handshake", "partner", "business", "approved"));
        work.add(new GifItem("Let's Go / Ready", "🚀", "https://media.giphy.com/media/3ohnEqJ1XOfvWaSk7e/giphy.gif", "🚀 [Launch]", "💼 Work & Hustle", "launch", "rocket", "ready", "lets go", "speed"));
        CATEGORIES.put("💼 Work & Hustle", work);
        ALL_GIFS.addAll(work);

        // 9. 🐱 Cute Pets
        List<GifItem> pets = new ArrayList<>();
        pets.add(new GifItem("Curious Kitten", "🐱", "https://media.giphy.com/media/JIX9t2j0ZTN9S/giphy.gif", "🐱 [Meow]", "🐱 Cute Pets", "cat", "kitten", "cute", "meow", "pet", "animal"));
        pets.add(new GifItem("Excited Golden Dog", "🐶", "https://media.giphy.com/media/4Zo41lhzKt6iZ8xff9/giphy.gif", "🐶 [Woof]", "🐱 Cute Pets", "dog", "puppy", "good boy", "pet", "play"));
        pets.add(new GifItem("Panda Roll", "🐼", "https://media.giphy.com/media/EatwJZRUIv41G/giphy.gif", "🐼 [Panda]", "🐱 Cute Pets", "panda", "cute", "funny", "lazy", "roll"));
        CATEGORIES.put("🐱 Cute Pets", pets);
        ALL_GIFS.addAll(pets);

        // 10. ✨ Express Kaomoji & Text Art Stickers
        List<GifItem> kaomoji = new ArrayList<>();
        kaomoji.add(new GifItem("Shrug", "¯\\_(ツ)_/¯", "", "¯\\_(ツ)_/¯", "✨ Kaomoji", "shrug", "idk", "whatever"));
        kaomoji.add(new GifItem("Table Flip", "(╯°□°)╯︵ ┻━┻", "", "(╯°□°)╯︵ ┻━┻", "✨ Kaomoji", "rage", "flip", "angry", "table"));
        kaomoji.add(new GifItem("Put Table Back", "┬─┬ノ( º _ ºノ)", "", "┬─┬ノ( º _ ºノ)", "✨ Kaomoji", "calm", "table", "peace"));
        kaomoji.add(new GifItem("Big Hug", "(づ｡◕‿‿◕｡)づ", "", "(づ｡◕‿‿◕｡)づ", "✨ Kaomoji", "hug", "love", "cute"));
        kaomoji.add(new GifItem("Warm Smile", "(✿◠‿◠)", "", "(✿◠‿◠)", "✨ Kaomoji", "smile", "happy", "flower"));
        kaomoji.add(new GifItem("In Love", "(♥_♥)", "", "(♥_♥)", "✨ Kaomoji", "love", "heart", "eyes"));
        kaomoji.add(new GifItem("Let's Fight", "(ง'̀-'́)ง", "", "(ง'̀-'́)ง", "✨ Kaomoji", "fight", "box", "lets go"));
        kaomoji.add(new GifItem("Happy Dance", "ᕕ( ᐛ )ᕗ", "", "ᕕ( ᐛ )ᕗ", "✨ Kaomoji", "dance", "happy", "walk"));
        kaomoji.add(new GifItem("Crying", "(╥﹏╥)", "", "(╥﹏╥)", "✨ Kaomoji", "cry", "sad", "tears"));
        kaomoji.add(new GifItem("Cheers Drink", "( ^_^)／旦", "", "( ^_^)／旦", "✨ Kaomoji", "cheers", "tea", "drink"));
        kaomoji.add(new GifItem("Success Joy", "٩(^‿^)۶", "", "٩(^‿^)۶", "✨ Kaomoji", "success", "yay", "cheer"));
        CATEGORIES.put("✨ Kaomoji", kaomoji);
        ALL_GIFS.addAll(kaomoji);
    }

    public static String[] getCategoryNames() {
        return CATEGORIES.keySet().toArray(new String[0]);
    }

    public static List<GifItem> getItemsForCategory(String category) {
        return CATEGORIES.getOrDefault(category, ALL_GIFS);
    }

    /**
     * Fast, zero-latency local keyword & tag search across the curated library.
     */
    public static List<GifItem> searchLocalGifs(String query) {
        if (query == null || query.trim().isEmpty()) {
            return CATEGORIES.get("🔥 Trending");
        }

        String q = query.toLowerCase(Locale.US).trim();
        List<GifItem> results = new ArrayList<>();

        for (GifItem item : ALL_GIFS) {
            boolean matched = false;
            if (item.title.toLowerCase(Locale.US).contains(q) || item.category.toLowerCase(Locale.US).contains(q)) {
                matched = true;
            } else {
                for (String tag : item.tags) {
                    if (tag.contains(q) || q.contains(tag)) {
                        matched = true;
                        break;
                    }
                }
            }

            if (matched && !results.contains(item)) {
                results.add(item);
            }
        }

        if (results.isEmpty()) {
            // Return top trending items if no match
            return CATEGORIES.get("🔥 Trending");
        }
        return results;
    }

    /**
     * Finds contextual GIF suggestions based on typed words / sentences.
     * E.g. "congratulations", "applause", "haha", "celebrate", "awesome", "lol", "love", "thanks", "wow".
     */
    public static GifItem findContextualGif(String lastWord, String currentWord) {
        String combined = ((lastWord != null ? lastWord + " " : "") + (currentWord != null ? currentWord : "")).toLowerCase(Locale.US).trim();
        if (combined.isEmpty()) return null;

        for (GifItem item : ALL_GIFS) {
            if (item.gifUrl.isEmpty()) continue; // Skip kaomoji for top GIF chip
            for (String tag : item.tags) {
                if (combined.contains(tag) || tag.equalsIgnoreCase(currentWord) || tag.equalsIgnoreCase(lastWord)) {
                    return item;
                }
            }
        }
        return null;
    }

    public interface OnlineSearchCallback {
        void onResults(List<GifItem> items);
    }

    /**
     * Fetches live GIFs from public Giphy endpoint and merges with local database.
     */
    public static void searchOnlineGifs(final String query, final OnlineSearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onResults(CATEGORIES.get("🔥 Trending"));
            return;
        }

        final List<GifItem> localMatches = searchLocalGifs(query);

        new Thread(() -> {
            List<GifItem> onlineMatches = new ArrayList<>();
            try {
                String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                String urlStr = "https://api.giphy.com/v1/gifs/search?api_key=" + GIPHY_API_KEY + "&q=" + encoded + "&limit=16&rating=g";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray data = root.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject obj = data.getJSONObject(i);
                            String title = obj.optString("title", "GIF");
                            if (title.endsWith(" GIF")) {
                                title = title.substring(0, title.length() - 4);
                            }
                            JSONObject images = obj.optJSONObject("images");
                            if (images != null) {
                                JSONObject downsized = images.optJSONObject("downsized");
                                JSONObject fixedHeight = images.optJSONObject("fixed_height");
                                String gifUrl = "";
                                if (downsized != null && downsized.has("url")) {
                                    gifUrl = downsized.optString("url");
                                } else if (fixedHeight != null && fixedHeight.has("url")) {
                                    gifUrl = fixedHeight.optString("url");
                                }

                                if (!gifUrl.isEmpty()) {
                                    onlineMatches.add(new GifItem(title, "🎞️", gifUrl, "🎞️ [" + title + "]", "🔍 Online", query));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Online GIF search fallback to local: " + e.getMessage());
            }

            // Merge online and local results without duplicates
            final List<GifItem> combined = new ArrayList<>();
            if (!onlineMatches.isEmpty()) {
                combined.addAll(onlineMatches);
            }
            for (GifItem local : localMatches) {
                if (!combined.contains(local)) {
                    combined.add(local);
                }
            }

            if (callback != null) {
                callback.onResults(combined.isEmpty() ? localMatches : combined);
            }
        }).start();
    }
}
