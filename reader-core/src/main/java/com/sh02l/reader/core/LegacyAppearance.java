package com.sh02l.reader.core;

/** Read-only migration of M0 appearance, without shipping its sample navigation model. */
public final class LegacyAppearance {
    public final int fontSize, theme, brightness;
    public final boolean infoVisible;

    private LegacyAppearance(int fontSize, int theme, int brightness, boolean infoVisible) {
        this.fontSize = fontSize;
        this.theme = theme;
        this.brightness = brightness;
        this.infoVisible = infoVisible;
    }

    public static LegacyAppearance restore(String value) {
        LegacyAppearance defaults = new LegacyAppearance(20, 0, 8, true);
        if (value == null || value.length() > 2048) return defaults;
        try {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 8 || !parts[0].equals("1")) return defaults;
            number(parts[1], 0, 8);
            int font = number(parts[2], 14, 32),
                    theme = number(parts[3], 0, 2),
                    brightness = number(parts[4], 1, 10),
                    info = number(parts[5], 0, 1);
            String[] frames = parts[6].split(";");
            if (frames.length < 1 || frames.length > 8) return defaults;
            String known =
                    "|HOME|READER|DIAGNOSTICS|SETTINGS|HELP|MENU|CONTENTS|BOOKMARKS|EXIT_CONFIRM|";
            for (int i = 0; i < frames.length; i++) {
                String[] frame = frames[i].split(",", -1);
                if (frame.length != 2
                        || !known.contains("|" + frame[0] + "|")
                        || i == 0 && !frame[0].equals("HOME")) return defaults;
                Integer.parseInt(frame[1]);
            }
            if (!parts[7].isEmpty()) for (String mark : parts[7].split(",")) number(mark, 0, 8);
            return new LegacyAppearance(font, theme, brightness, info == 1);
        } catch (IllegalArgumentException malformed) {
            return defaults;
        }
    }

    private static int number(String value, int min, int max) {
        int n = Integer.parseInt(value);
        if (n < min || n > max) throw new IllegalArgumentException("range");
        return n;
    }
}
