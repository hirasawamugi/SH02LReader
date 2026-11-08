package com.sh02l.reader;

import com.sh02l.reader.core.AppearanceState;
import com.sh02l.reader.core.Palette;

import org.json.*;

import java.util.Arrays;

/** A single saved UI appearance. Reading colors remain independent. */
final class ThemeSpec {
    static final String[] NAMES = {"米白", "白色", "暖炭灰", "初音未来", "边缘行者"};
    private static final int[][] ORIGINAL_NIGHT_PRESETS = {
        {0xff122a2b, 0xff1b3638, 0xffe1fffa, 0xffa0cbc5, 0xff39c5bb, 0xff255b5b, 0xff416b69},
        {0xff161329, 0xff211d38, 0xfff3eeff, 0xffc0afd9, 0xfff574db, 0xff4a2f61, 0xff64517c}
    };
    static final String[] ROLES = {"页面底色", "面板底色", "主要文字", "次要文字", "强调色", "选中底色", "分隔色"};
    int preset, style, fit, position, mask = 2;
    int[] colors;
    String asset = "";

    ThemeSpec(int preset, int style) {
        this.style = style;
        preset(preset);
    }

    void preset(int value) {
        preset = value;
        Palette p =
                value == 3
                        ? new Palette(
                                0xfff0faf4,
                                0xfffcfffd,
                                0xff193b35,
                                0xff4f7068,
                                0xff168b7b,
                                0xffb8ebd8,
                                0xffc4ddd3)
                        : value == 4
                                ? new Palette(
                                        0xff131824,
                                        0xff20283a,
                                        0xffeff2f8,
                                        0xffa9bfc7,
                                        0xff49d9c8,
                                        0xfffcee0a,
                                        0xff34495b)
                                : Palette.forTheme(value);
        colors =
                new int[] {
                    p.background,
                    p.surface,
                    p.foreground,
                    p.secondary,
                    p.accent,
                    p.selected,
                    p.divider
                };
    }

    Palette palette() {
        return new Palette(
                colors[0], colors[1], colors[2], colors[3], colors[4], colors[5], colors[6]);
    }

    int legacyTheme() {
        return preset == 3 ? AppearanceState.LIGHT : Math.min(preset, AppearanceState.DARK);
    }

    ThemeSpec copy() {
        ThemeSpec n = new ThemeSpec(preset, style);
        n.colors = colors.clone();
        n.fit = fit;
        n.position = position;
        n.mask = mask;
        n.asset = asset;
        return n;
    }

    String json() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", 1);
        o.put("preset", preset);
        o.put("style", style);
        o.put("fit", fit);
        o.put("position", position);
        o.put("mask", mask);
        o.put("asset", asset);
        JSONArray a = new JSONArray();
        for (int color : colors) a.put(color);
        o.put("colors", a);
        return o.toString();
    }

    static ThemeSpec parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (o.getInt("version") != 1) throw new JSONException("Unsupported theme");
        ThemeSpec n = new ThemeSpec(range(o.getInt("preset"), 4), range(o.getInt("style"), 2));
        n.fit = range(o.getInt("fit"), 1);
        n.position = range(o.getInt("position"), 4);
        n.mask = range(o.getInt("mask"), 3);
        n.asset = o.getString("asset");
        if (!n.asset.isEmpty() && !n.asset.matches("bg-[a-f0-9-]{36}\\.png"))
            throw new JSONException("Invalid asset");
        JSONArray a = o.getJSONArray("colors");
        if (a.length() != 7) throw new JSONException("Invalid palette");
        for (int i = 0; i < 7; i++) n.colors[i] = a.getInt(i) | 0xff000000;
        // Replace only the two exact, untouched old presets. Keep every custom color and asset.
        if (n.preset >= 3 && Arrays.equals(n.colors, ORIGINAL_NIGHT_PRESETS[n.preset - 3]))
            n.preset(n.preset);
        return n;
    }

    private static int range(int n, int max) throws JSONException {
        if (n < 0 || n > max) throw new JSONException("Invalid theme value");
        return n;
    }
}
