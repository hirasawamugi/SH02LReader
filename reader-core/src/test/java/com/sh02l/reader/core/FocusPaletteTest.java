package com.sh02l.reader.core;

import static org.junit.Assert.*;

import org.junit.Test;

public final class FocusPaletteTest {
    @Test
    public void brightFocusUsesDarkInkForTextAndIcon() {
        Palette p =
                new Palette(
                        0xff131824,
                        0xff20283a,
                        0xffeff2f8,
                        0xffa9bfc7,
                        0xff49d9c8,
                        0xfffcee0a,
                        0xff34495b);
        assertEquals(p.background, p.focusForeground);
        assertTrue(ColorContrast.ratio(p.focusForeground, p.focusBackground) >= 4.5);
        assertTrue(ColorContrast.ratio(p.focusSecondary, p.focusBackground) >= 4.5);
        assertTrue(ColorContrast.ratio(p.focusAccent, p.focusBackground) >= 3);
        assertEquals("Stored text color remains independent", 0xffeff2f8, p.foreground);
    }

    @Test
    public void readablePreferredColorsAreRetained() {
        Palette p = Palette.forTheme(AppearanceState.DARK);
        assertEquals(p.foreground, p.focusForeground);
        assertEquals(p.secondary, p.focusSecondary);
        assertEquals(p.accent, p.focusAccent);
    }

    @Test
    public void customLowContrastColorsStillHaveReadableFocus() {
        for (int gray = 0; gray <= 255; gray++) {
            int color = 0xff000000 | gray << 16 | gray << 8 | gray;
            int ink = ColorContrast.readable(color, color, color, 4.5);
            assertTrue(ColorContrast.ratio(ink, color) >= 4.5);
        }
        assertEquals(21, ColorContrast.ratio(0xff000000, 0xffffffff), .00001);
    }
}
