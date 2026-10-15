package com.wisprflow.keyboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dataset for GIF reactions, memes, and express text kaomoji stickers.
 */
public class GifReactionsData {

    public static class GifItem {
        public final String title;
        public final String emojiPreview;
        public final String gifUrl;
        public final String fallbackText;

        public GifItem(String title, String emojiPreview, String gifUrl, String fallbackText) {
            this.title = title;
            this.emojiPreview = emojiPreview;
            this.gifUrl = gifUrl;
            this.fallbackText = fallbackText;
        }
    }

    public static final Map<String, List<GifItem>> CATEGORIES = new LinkedHashMap<>();

    static {
        // 1. Reactions & Kaomoji Stickers
        List<GifItem> reactions = new ArrayList<>();
        reactions.add(new GifItem("Thumbs Up", "👍", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "👍 [GIF: Thumbs Up]"));
        reactions.add(new GifItem("Applaud", "👏", "https://media.giphy.com/media/fnK0jeA8vIh2QLq3IZ/giphy.gif", "👏 [GIF: Applause]"));
        reactions.add(new GifItem("Celebrate", "🎉", "https://media.giphy.com/media/artj92V8o75VPL7AeQ/giphy.gif", "🎉 [GIF: Celebrate!]"));
        reactions.add(new GifItem("Mind Blown", "🤯", "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif", "🤯 [GIF: Mind Blown]"));
        reactions.add(new GifItem("Laugh Out Loud", "😂", "https://media.giphy.com/media/3oEjHAUOqG3lSS0f1C/giphy.gif", "😂 [GIF: LOL]"));
        reactions.add(new GifItem("Love Heart", "❤️", "https://media.giphy.com/media/26BRv0ThflsDTqUXa/giphy.gif", "❤️ [GIF: Love]"));
        reactions.add(new GifItem("Thinking", "🤔", "https://media.giphy.com/media/a5viI92PAF89q/giphy.gif", "🤔 [GIF: Thinking]"));
        reactions.add(new GifItem("No Problem", "👌", "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif", "👌 [GIF: No Problem]"));
        reactions.add(new GifItem("Rock On", "🤘", "https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/giphy.gif", "🤘 [GIF: Rock On]"));
        reactions.add(new GifItem("Hustle", "💼", "https://media.giphy.com/media/3oKIPnAiaMCws8nOsE/giphy.gif", "💼 [GIF: Let's Work]"));
        CATEGORIES.put("🔥 Trending GIFs", reactions);

        // 2. Express Stickers / Kaomoji
        List<GifItem> kaomoji = new ArrayList<>();
        kaomoji.add(new GifItem("Shrug", "¯\\_(ツ)_/¯", "", "¯\\_(ツ)_/¯"));
        kaomoji.add(new GifItem("Table Flip", "(╯°□°)╯︵ ┻━┻", "", "(╯°□°)╯︵ ┻━┻"));
        kaomoji.add(new GifItem("Put Table Back", "┬─┬ノ( º _ ºノ)", "", "┬─┬ノ( º _ ºノ)"));
        kaomoji.add(new GifItem("Big Hug", "(づ｡◕‿‿◕｡)づ", "", "(づ｡◕‿‿◕｡)づ"));
        kaomoji.add(new GifItem("Warm Smile", "(✿◠‿◠)", "", "(✿◠‿◠)"));
        kaomoji.add(new GifItem("In Love", "(♥_♥)", "", "(♥_♥)"));
        kaomoji.add(new GifItem("Let's Fight / Go", "(ง'̀-'́)ง", "", "(ง'̀-'́)ง"));
        kaomoji.add(new GifItem("Happy Dance", "ᕕ( ᐛ )ᕗ", "", "ᕕ( ᐛ )ᕗ"));
        kaomoji.add(new GifItem("Crying", "(╥﹏╥)", "", "(╥﹏╥)"));
        kaomoji.add(new GifItem("Suspicious", "(¬_¬)", "", "(¬_¬)"));
        kaomoji.add(new GifItem("Cheers", "( ^_^)／旦", "", "( ^_^)／旦"));
        kaomoji.add(new GifItem("Success", "٩(^‿^)۶", "", "٩(^‿^)۶"));
        CATEGORIES.put("✨ Kaomoji Stickers", kaomoji);
    }

    public static String[] getCategoryNames() {
        return CATEGORIES.keySet().toArray(new String[0]);
    }

    public static List<GifItem> getItemsForCategory(String category) {
        return CATEGORIES.getOrDefault(category, CATEGORIES.values().iterator().next());
    }
}
