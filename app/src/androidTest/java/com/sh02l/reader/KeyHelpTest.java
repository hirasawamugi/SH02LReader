package com.sh02l.reader;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;

import com.sh02l.reader.core.Action;

import java.io.*;

public final class KeyHelpTest extends NativeTestBase {
    public void testZeroTogglesWithoutCenterExit() {
        press(KeyEvent.KEYCODE_0);
        assertEquals("HELP", kind());
        assertNotNull(find("key-help"));
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("HELP", kind());
        press(KeyEvent.KEYCODE_0);
        assertEquals("LIBRARY", kind());
        press(KeyEvent.KEYCODE_0);
        press(KeyEvent.KEYCODE_F2);
        assertEquals("HELP", kind());
        press(KeyEvent.KEYCODE_F4);
        assertEquals("LIBRARY", kind());
    }

    public void testHeldZeroDoesNotToggleTwice() {
        long now = SystemClock.uptimeMillis();
        ui(
                () -> {
                    activity.dispatchKeyEvent(
                            new KeyEvent(now, now, KeyEvent.ACTION_DOWN, 7, 0, 0, 3, 11));
                    activity.dispatchKeyEvent(
                            new KeyEvent(now, now + 400, KeyEvent.ACTION_DOWN, 7, 1, 0, 3, 11));
                    activity.dispatchKeyEvent(
                            new KeyEvent(now, now + 600, KeyEvent.ACTION_DOWN, 7, 2, 0, 3, 11));
                    activity.dispatchKeyEvent(
                            new KeyEvent(now, now + 650, KeyEvent.ACTION_UP, 7, 0, 0, 3, 11));
                });
        assertEquals("HELP", kind());
        press(KeyEvent.KEYCODE_0);
        assertEquals("LIBRARY", kind());
    }

    public void testHelpRestoresMenuFocusAndReaderView() throws Exception {
        fixture();
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(this::ready, 5000);
        long before = offset();
        View reader = find("reading-content");
        int width = reader.getWidth(), height = reader.getHeight();
        press(KeyEvent.KEYCODE_F3);
        select("settings");
        String selected = selected();
        press(KeyEvent.KEYCODE_0);
        assertEquals("HELP", kind());
        press(KeyEvent.KEYCODE_5);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("HELP", kind());
        assertEquals(before, offset());
        assertSame(reader, find("reading-content"));
        press(KeyEvent.KEYCODE_0);
        assertEquals("MENU", kind());
        assertEquals(selected, selected());
        assertSame(reader, find("reading-content"));
        assertEquals(width, reader.getWidth());
        assertEquals(height, reader.getHeight());
        assertEquals(before, offset());
    }

    public void testHelpDuringImportDoesNotCancelWork() throws Exception {
        File source = new File(activity.getCacheDir(), "help-import.txt");
        try (OutputStream out = new FileOutputStream(source)) {
            byte[] bytes = new byte[1024 * 1024];
            java.util.Arrays.fill(bytes, (byte) 'a');
            out.write(bytes);
        }
        ui(
                () -> {
                    activity.importFile(source);
                    activity.perform(Action.HELP);
                });
        await(this::ready, 15000);
        assertEquals("HELP", kind());
        assertTrue(source.isFile());
        press(KeyEvent.KEYCODE_0);
        assertEquals("READER", kind());
        assertTrue(ready());
    }

    public void testHelpGeometryAlignsAtDeviceFontScale() {
        for (int width : new int[] {450, 380}) {
            HelpLayout layout = HelpLayout.measure(width, 1.5f, 1.95f);
            assertNotNull("Native keypad layout required", layout.key("2"));
            for (String id : new String[] {"2", "5", "8", "0", "II"})
                assertEquals(layout.centerX, layout.key(id).bounds.centerX(), 0.01f);
            assertEquals(
                    layout.key("F1").bounds.centerY(), layout.key("F2").bounds.centerY(), 0.01f);
            assertEquals(
                    layout.key("F3").bounds.centerY(), layout.key("F4").bounds.centerY(), 0.01f);
            String[][] rows = {
                {"1", "2", "3"},
                {"4", "5", "6"},
                {"7", "8", "9"},
                {"*", "0", "#"},
                {"I", "II", "III"}
            };
            for (String[] row : rows)
                for (int col = 0; col < 3; col++) {
                    HelpLayout.KeyBox key = layout.key(row[col]);
                    assertEquals(layout.key(rows[0][col]).bounds.left, key.bounds.left, 0.01f);
                    assertEquals(layout.key(rows[0][col]).bounds.right, key.bounds.right, 0.01f);
                    assertEquals(layout.key(row[0]).digitBaseline, key.digitBaseline, 0.01f);
                    assertEquals(layout.key(row[0]).labelBaseline, key.labelBaseline, 0.01f);
                    if (col > 0)
                        assertTrue(layout.key(row[col - 1]).bounds.right < key.bounds.left);
                    if (row != rows[4]) assertTrue(key.digitBaseline < key.labelBaseline);
                }
            for (int r = 1; r < rows.length; r++)
                assertTrue(
                        layout.key(rows[r - 1][0]).bounds.bottom
                                < layout.key(rows[r][0]).bounds.top);
            Paint labels = new Paint();
            labels.setTextSize(12 * 1.95f);
            for (String label : new String[] {"上一章", "下一章", "说明开关"})
                assertTrue(labels.measureText(label) < layout.key("1").bounds.width() - 12);
        }
    }
}
