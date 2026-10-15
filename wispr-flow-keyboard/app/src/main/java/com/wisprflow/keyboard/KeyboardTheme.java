package com.wisprflow.keyboard;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Rich Theme definitions for Wispr Flow Keyboard.
 * Supports dynamic switching between dark, light, OLED, cyberpunk, forest, and sunset themes.
 */
public class KeyboardTheme {

    public final String id;
    public final String name;
    public final String icon;
    public final int backgroundColor;
    public final int toolbarColor;
    public final int keyColor;
    public final int keyPressedColor;
    public final int actionKeyColor;
    public final int accentColor;
    public final int textColor;
    public final int subTextColor;
    public final int dividerColor;
    public final boolean isLight;

    public KeyboardTheme(String id, String name, String icon,
                         int backgroundColor, int toolbarColor, int keyColor,
                         int keyPressedColor, int actionKeyColor, int accentColor,
                         int textColor, int subTextColor, int dividerColor,
                         boolean isLight) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.backgroundColor = backgroundColor;
        this.toolbarColor = toolbarColor;
        this.keyColor = keyColor;
        this.keyPressedColor = keyPressedColor;
        this.actionKeyColor = actionKeyColor;
        this.accentColor = accentColor;
        this.textColor = textColor;
        this.subTextColor = subTextColor;
        this.dividerColor = dividerColor;
        this.isLight = isLight;
    }

    public static final String DEFAULT_THEME_ID = "clean_light";

    public static List<KeyboardTheme> getAllThemes() {
        List<KeyboardTheme> list = new ArrayList<>();

        // 1. Clean Light / Silver (Default)
        list.add(new KeyboardTheme(
                "clean_light", "Clean Light", "☀️",
                Color.parseColor("#f1f5f9"), Color.parseColor("#f8fafc"),
                Color.parseColor("#ffffff"), Color.parseColor("#cbd5e1"),
                Color.parseColor("#f1f5f9"), Color.parseColor("#4f46e5"),
                Color.parseColor("#0f172a"), Color.parseColor("#64748b"),
                Color.parseColor("#e2e8f0"), true
        ));

        // 2. System Auto (Adapts to OS Light/Dark Mode)
        list.add(new KeyboardTheme(
                "system_auto", "System Auto (OS)", "📱",
                Color.parseColor("#f1f5f9"), Color.parseColor("#f8fafc"),
                Color.parseColor("#ffffff"), Color.parseColor("#cbd5e1"),
                Color.parseColor("#f1f5f9"), Color.parseColor("#4f46e5"),
                Color.parseColor("#0f172a"), Color.parseColor("#64748b"),
                Color.parseColor("#e2e8f0"), true
        ));

        // 3. Midnight Slate
        list.add(new KeyboardTheme(
                "midnight_slate", "Midnight Slate", "🌌",
                Color.parseColor("#080b11"), Color.parseColor("#0c121e"),
                Color.parseColor("#161e2e"), Color.parseColor("#243048"),
                Color.parseColor("#1e293b"), Color.parseColor("#6366f1"),
                Color.WHITE, Color.parseColor("#94a3b8"),
                Color.parseColor("#1a2638"), false
        ));

        // 4. OLED Pure Black
        list.add(new KeyboardTheme(
                "oled_black", "OLED Black", "🖤",
                Color.parseColor("#000000"), Color.parseColor("#0a0a0a"),
                Color.parseColor("#141414"), Color.parseColor("#262626"),
                Color.parseColor("#1c1c1c"), Color.parseColor("#10b981"),
                Color.WHITE, Color.parseColor("#737373"),
                Color.parseColor("#222222"), false
        ));

        // 5. Cyberpunk Neon
        list.add(new KeyboardTheme(
                "cyberpunk_neon", "Cyberpunk Neon", "🔮",
                Color.parseColor("#0d0517"), Color.parseColor("#150926"),
                Color.parseColor("#23103d"), Color.parseColor("#3b1c66"),
                Color.parseColor("#2c144d"), Color.parseColor("#ec4899"),
                Color.WHITE, Color.parseColor("#a78bfa"),
                Color.parseColor("#381c5f"), false
        ));

        // 6. Forest Emerald
        list.add(new KeyboardTheme(
                "forest_emerald", "Forest Emerald", "🌲",
                Color.parseColor("#04130d"), Color.parseColor("#071e15"),
                Color.parseColor("#0d2f21"), Color.parseColor("#174d37"),
                Color.parseColor("#123c2a"), Color.parseColor("#059669"),
                Color.WHITE, Color.parseColor("#6ee7b7"),
                Color.parseColor("#134430"), false
        ));

        // 7. Sunset Amber
        list.add(new KeyboardTheme(
                "sunset_amber", "Sunset Amber", "🌅",
                Color.parseColor("#140a04"), Color.parseColor("#1e1107"),
                Color.parseColor("#301a0a"), Color.parseColor("#4d2c12"),
                Color.parseColor("#3c210d"), Color.parseColor("#f59e0b"),
                Color.WHITE, Color.parseColor("#fcd34d"),
                Color.parseColor("#3d230e"), false
        ));

        return list;
    }

    public static KeyboardTheme getTheme(String id) {
        if (id == null) return getDefaultTheme();
        for (KeyboardTheme t : getAllThemes()) {
            if (t.id.equalsIgnoreCase(id.trim())) {
                return t;
            }
        }
        return getDefaultTheme();
    }

    public static KeyboardTheme resolveTheme(android.content.Context context, String id) {
        if (id != null && id.equalsIgnoreCase("system_auto")) {
            if (context != null) {
                int nightMode = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    return getTheme("midnight_slate");
                } else {
                    return getTheme("clean_light");
                }
            }
            return getTheme("clean_light");
        }
        return getTheme(id);
    }

    public static KeyboardTheme getDefaultTheme() {
        return getAllThemes().get(0);
    }
}
