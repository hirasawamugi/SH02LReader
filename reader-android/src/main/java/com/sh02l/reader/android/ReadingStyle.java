package com.sh02l.reader.android;

import android.graphics.Typeface;

import java.util.Objects;

/** Immutable render parameters, independent of application preference storage. */
public final class ReadingStyle {
    public final float textSize, lineExtra;
    public final int foreground, background;
    public final Typeface typeface;
    public final String fontFamily, fontPath;

    public ReadingStyle(
            float size,
            float extra,
            int foreground,
            int background,
            Typeface typeface,
            String family,
            String path) {
        this.textSize = size;
        lineExtra = extra;
        this.foreground = foreground;
        this.background = background;
        this.typeface = typeface;
        this.fontFamily = family;
        this.fontPath = path;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ReadingStyle)) return false;
        ReadingStyle s = (ReadingStyle) o;
        return textSize == s.textSize
                && lineExtra == s.lineExtra
                && foreground == s.foreground
                && background == s.background
                && Objects.equals(typeface, s.typeface)
                && Objects.equals(fontFamily, s.fontFamily)
                && Objects.equals(fontPath, s.fontPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                textSize, lineExtra, foreground, background, typeface, fontFamily, fontPath);
    }
}
