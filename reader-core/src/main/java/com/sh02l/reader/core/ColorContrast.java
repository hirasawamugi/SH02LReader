package com.sh02l.reader.core;

/** Opaque sRGB contrast calculations shared by native focus colors and appearance previews. */
public final class ColorContrast {
    private ColorContrast() {}

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }

    private static double luminance(int color) {
        return .2126 * channel((color >> 16) & 255)
                + .7152 * channel((color >> 8) & 255)
                + .0722 * channel(color & 255);
    }

    public static double ratio(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    static int readable(int preferred, int background, int fallback, double minimum) {
        if (ratio(preferred, background) >= minimum) return preferred;
        if (ratio(fallback, background) >= minimum) return fallback;
        return ratio(0xff000000, background) >= ratio(0xffffffff, background)
                ? 0xff000000
                : 0xffffffff;
    }

    static int blend(int base, int accent, int percent) {
        int r = (((base >> 16) & 255) * (100 - percent) + ((accent >> 16) & 255) * percent) / 100;
        int g = (((base >> 8) & 255) * (100 - percent) + ((accent >> 8) & 255) * percent) / 100;
        int b = ((base & 255) * (100 - percent) + (accent & 255) * percent) / 100;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }
}
