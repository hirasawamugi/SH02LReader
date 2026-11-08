package com.sh02l.reader;

import android.test.InstrumentationTestCase;

import java.util.Arrays;

@SuppressWarnings("deprecation")
public final class ThemePresetMigrationTest extends InstrumentationTestCase {
    private static final int[][] OLD = {
        {0xff122a2b, 0xff1b3638, 0xffe1fffa, 0xffa0cbc5, 0xff39c5bb, 0xff255b5b, 0xff416b69},
        {0xff161329, 0xff211d38, 0xfff3eeff, 0xffc0afd9, 0xfff574db, 0xff4a2f61, 0xff64517c}
    };

    private ThemeSpec old(int preset) {
        ThemeSpec spec = new ThemeSpec(preset, 2);
        spec.colors = OLD[preset - 3].clone();
        spec.asset = "bg-12345678-1234-1234-1234-123456789abc.png";
        spec.fit = 1;
        spec.position = 4;
        spec.mask = 3;
        return spec;
    }

    private void retained(ThemeSpec before, ThemeSpec after) {
        assertEquals(before.preset, after.preset);
        assertEquals(before.style, after.style);
        assertEquals(before.asset, after.asset);
        assertEquals(before.fit, after.fit);
        assertEquals(before.position, after.position);
        assertEquals(before.mask, after.mask);
    }

    public void testUntouchedOldPresetsMigrateWithoutLosingBackground() throws Exception {
        for (int preset = 3; preset <= 4; preset++) {
            ThemeSpec before = old(preset);
            ThemeSpec after = ThemeSpec.parse(before.json());
            retained(before, after);
            assertTrue(Arrays.equals(new ThemeSpec(preset, 2).colors, after.colors));
            ThemeSpec again = ThemeSpec.parse(after.json());
            retained(after, again);
            assertTrue(Arrays.equals(after.colors, again.colors));
        }
    }

    public void testAnyEditedColorIsPreserved() throws Exception {
        for (int preset = 3; preset <= 4; preset++) {
            for (int role = 0; role < 7; role++) {
                ThemeSpec before = old(preset);
                before.colors[role] ^= 1;
                ThemeSpec after = ThemeSpec.parse(before.json());
                retained(before, after);
                assertTrue(
                        "Custom role " + role + " was overwritten",
                        Arrays.equals(before.colors, after.colors));
            }
        }
    }
}
