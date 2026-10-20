package com.sh02l.reader;

import android.graphics.Bitmap;
import android.view.*;
import android.widget.ListView;

import java.io.*;

/** Uses only generated documents; captured images are device evidence, not mockups. */
public final class VisualVerificationTest extends NativeTestBase {
    private void appearance(int theme, int style) {
        press(KeyEvent.KEYCODE_F3);
        select("settings");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        select("appearance");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        int current = activity.getSharedPreferences("reader", 0).getInt("ui_theme", 0);
        for (int i = 0; i < (theme - current + 3) % 3; i++) press(KeyEvent.KEYCODE_DPAD_RIGHT);
        select("shelf-style");
        current = activity.getSharedPreferences("reader", 0).getInt("shelf_style", 1);
        for (int i = 0; i < (style - current + 3) % 3; i++) press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_F4);
        press(KeyEvent.KEYCODE_F4);
        press(KeyEvent.KEYCODE_F4);
    }

    private void capture(String name) throws IOException {
        getInstrumentation().waitForIdleSync();
        Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = activity.getExternalFilesDir("qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (OutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally {
            image.recycle();
        }
    }

    public void testAllThemesAndShelfStylesAtDeviceFontScale() throws Exception {
        for (int i = 0; i < 3; i++) {
            File source =
                    new File(
                            activity.getCacheDir(),
                            "长名称对齐验证第" + i + "本_这是一本用于检查选中滚动和统一行高的原创文本.txt");
            try (Writer out = new OutputStreamWriter(new FileOutputStream(source), "UTF-8")) {
                for (int n = 0; n < 80; n++) out.write("第" + i + "本，段落" + n + "：正文与软键布局验证。\n");
            }
            ui(() -> activity.importFile(source));
            await(this::ready, 10000);
            press(KeyEvent.KEYCODE_F4);
        }
        for (int theme = 0; theme < 3; theme++) {
            for (int style = 0; style < 3; style++) {
                appearance(theme, style);
                await(() -> selected() != null, 3000);
                final int[] sizes = {0, 0};
                ui(
                        () -> {
                            ListView list = (ListView) find("list:LIBRARY");
                            for (int row = 0; row < list.getChildCount(); row++) {
                                View child = list.getChildAt(row);
                                if (sizes[0] == 0) sizes[0] = child.getHeight();
                                else
                                    assertEquals(
                                            "Equal rows in one style", sizes[0], child.getHeight());
                            }
                            View footer = find("soft-keys");
                            int[] l = {0, 0}, f = {0, 0};
                            list.getLocationOnScreen(l);
                            footer.getLocationOnScreen(f);
                            assertTrue(l[1] + list.getHeight() <= f[1]);
                            sizes[1] = list.getChildCount();
                        });
                assertTrue(sizes[1] > 0);
                capture("theme-" + theme + "-style-" + style);
            }
            press(KeyEvent.KEYCODE_0);
            assertNotNull(find("key-help"));
            capture("help-theme-" + theme);
            press(KeyEvent.KEYCODE_0);
        }
        appearance(0, 1);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(this::ready, 10000);
        capture("reader");
        press(KeyEvent.KEYCODE_STAR);
        capture("settings");
        press(KeyEvent.KEYCODE_F4);
    }
}
