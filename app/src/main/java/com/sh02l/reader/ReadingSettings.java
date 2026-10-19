package com.sh02l.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

/** Reading layout preferences; a bad imported font never prevents opening a book. */
final class ReadingSettings {
    private final Context context;
    private final SharedPreferences preferences;
    private Typeface customTypeface;
    int lineExtra, horizontal, vertical, font;
    ReadingSettings(Context context, SharedPreferences preferences) {
        this.context = context.getApplicationContext(); this.preferences = preferences;
        lineExtra = bound(AppearanceStore.integer(preferences, "line_extra", 4), 0, 20);
        horizontal = bound(AppearanceStore.integer(preferences, "margin_horizontal", 12), 0, 40);
        vertical = bound(AppearanceStore.integer(preferences, "margin_vertical", 10), 0, 40);
        font = bound(AppearanceStore.integer(preferences, "font_family", 0), 0, 4);
    }
    void save() {
        preferences.edit().putInt("line_extra", lineExtra).putInt("margin_horizontal", horizontal)
                .putInt("margin_vertical", vertical).putInt("font_family", font).apply();
    }
    Typeface typeface() {
        switch (font) {
            case 1: return Typeface.SERIF;
            case 2: return Typeface.SANS_SERIF;
            case 3: return Typeface.MONOSPACE;
            case 4: if (customTypeface == null) customTypeface = new FontStore(context).load(); return customTypeface;
            default: return Typeface.DEFAULT;
        }
    }
    String fontName() { return new String[]{"系统", "衬线", "无衬线", "等宽", "自定义"}[font]; }
    void invalidateFont() { customTypeface = null; }
    String signature() { return lineExtra + ":" + horizontal + ":" + vertical + ":" + font; }
    static int bound(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
