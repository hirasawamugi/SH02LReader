package com.sh02l.reader;

import android.view.*;

public final class AppearanceNavigationTest extends NativeTestBase {
    public void testSettingsReturnsToSameParentItem() {
        press(KeyEvent.KEYCODE_F3);
        select("settings");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertNotNull("Categorized settings are required", find("item:appearance"));
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        select("ui-theme");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_F4);
        assertEquals("item:appearance", selected());
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(
                ((android.widget.TextView) find("item:ui-theme"))
                        .getText()
                        .toString()
                        .contains("白色"));
    }

    public void testUiThemeLeavesReaderAnchorAndViewIntact() throws Exception {
        fixture();
        long before = offset();
        View reader = find("reading-content");
        int width = reader.getWidth(), height = reader.getHeight();
        press(KeyEvent.KEYCODE_STAR);
        assertSame(reader, find("reading-content"));
        select("appearance");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertSame(reader, find("reading-content"));
        assertEquals(width, reader.getWidth());
        assertEquals(height, reader.getHeight());
        assertEquals(before, offset());
        press(KeyEvent.KEYCODE_F4);
        press(KeyEvent.KEYCODE_F4);
        assertSame(reader, find("reading-content"));
    }

    public void testFontScaleKeepsFooterClear() {
        press(KeyEvent.KEYCODE_F3);
        select("settings");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        View footer = find("soft-keys");
        assertNotNull("Footer must have its own layout space", footer);
        ListBounds.assertClear(this, find("list:SETTINGS"), footer);
    }

    private static final class ListBounds {
        static void assertClear(AppearanceNavigationTest test, View list, View footer) {
            int[] top = {0, 0}, bottom = {0, 0};
            test.ui(
                    () -> {
                        list.getLocationOnScreen(top);
                        footer.getLocationOnScreen(bottom);
                    });
            test.assertTrue("List must end before footer", top[1] + list.getHeight() <= bottom[1]);
        }
    }
}
