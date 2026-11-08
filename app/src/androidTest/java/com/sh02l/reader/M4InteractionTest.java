package com.sh02l.reader;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

import com.sh02l.reader.core.DocumentLocation;

import java.io.*;

public final class M4InteractionTest extends NativeTestBase {
    private void appearance() {
        press(KeyEvent.KEYCODE_F3);
        select("appearance");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("THEME_EDITOR", kind());
    }

    private DocumentLocation location() {
        final DocumentLocation[] value = {null};
        ui(() -> value[0] = activity.reading().position.location);
        return value[0];
    }

    private void screenshot(String name) throws Exception {
        java.util.concurrent.CountDownLatch drawn = new java.util.concurrent.CountDownLatch(1);
        ui(
                () ->
                        activity.getWindow()
                                .getDecorView()
                                .postOnAnimation(
                                        () ->
                                                activity.getWindow()
                                                        .getDecorView()
                                                        .postOnAnimation(drawn::countDown)));
        assertTrue(drawn.await(3, java.util.concurrent.TimeUnit.SECONDS));
        getInstrumentation().getUiAutomation().waitForIdle(150, 2000);
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out =
                new FileOutputStream(new File(activity.getExternalFilesDir(null), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    public void testCenteredShelfAndDraftCancelSave() throws Exception {
        ui(
                () -> {
                    ListView list = (ListView) find("list:LIBRARY");
                    View row = list.getSelectedView();
                    assertEquals(row.getLeft(), list.getWidth() - row.getRight());
                });
        ThemeSpec before = new BackgroundStore(activity).load(0, 0);
        appearance();
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        select("theme-preview");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("THEME_PREVIEW", kind());
        screenshot("m4-theme-preview.png");
        press(KeyEvent.KEYCODE_F4);
        press(KeyEvent.KEYCODE_F4);
        assertEquals(before.preset, new BackgroundStore(activity).load(0, 0).preset);
        press(KeyEvent.KEYCODE_F4);
        appearance();
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_F3);
        await(() -> "MENU".equals(kind()), 10000);
        assertEquals((before.preset + 1) % 5, new BackgroundStore(activity).load(0, 0).preset);
        press(KeyEvent.KEYCODE_F4);
        screenshot("m4-shelf.png");
        press(KeyEvent.KEYCODE_0);
        screenshot("m4-help.png");
        assertEquals("HELP", kind());
        press(KeyEvent.KEYCODE_0);
        assertEquals("LIBRARY", kind());
    }

    public void testBackgroundDraftImportPreviewAndSave() throws Exception {
        File file = new File(activity.getCacheDir(), "m4-background.png");
        Bitmap source = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(source);
        canvas.drawColor(0xff203050);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(0xff39c5bb);
        canvas.drawCircle(800, 350, 240, paint);
        try (FileOutputStream out = new FileOutputStream(file)) {
            source.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        source.recycle();
        appearance();
        select("theme-image");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("appearanceFeatures");
        field.setAccessible(true);
        AppearanceFeatures features = (AppearanceFeatures) field.get(activity);
        ui(() -> features.picked(android.net.Uri.fromFile(file)));
        java.lang.reflect.Field working = AppearanceFeatures.class.getDeclaredField("busy");
        working.setAccessible(true);
        await(
                () -> {
                    final boolean[] idle = {false};
                    ui(
                            () -> {
                                try {
                                    idle[0] = !working.getBoolean(features);
                                } catch (Exception e) {
                                    throw new AssertionError(e);
                                }
                            });
                    return idle[0];
                },
                15000);
        select("theme-preview");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("THEME_PREVIEW", kind());
        screenshot("m4-background-preview.png");
        press(KeyEvent.KEYCODE_F4);
        press(KeyEvent.KEYCODE_F3);
        await(() -> "MENU".equals(kind()), 10000);
        ThemeSpec saved = new BackgroundStore(activity).load(0, 0);
        assertFalse(saved.asset.isEmpty());
        assertTrue(new BackgroundStore(activity).file(saved.asset).isFile());
        assertTrue(file.isFile());
        press(KeyEvent.KEYCODE_F4);
        screenshot("m4-background-shelf.png");
    }

    public void testPureReadingKeepsTxtViewAndAnchor() throws Exception {
        fixture();
        samplePure("TXT");
    }

    public void testPureReadingKeepsEpubViewAndAnchor() throws Exception {
        File file = EpubFixtures.create(activity.getCacheDir());
        ui(() -> activity.importFile(file));
        await(this::ready, 20000);
        samplePure("EPUB");
    }

    private void samplePure(String format) throws Exception {
        for (int i = 0; i < 20; i++) {
            press(KeyEvent.KEYCODE_DPAD_DOWN);
            await(this::ready, 10000);
        }
        View surface = find("reading-content");
        DocumentLocation anchor = location();
        int height = surface.getHeight();
        long start = SystemClock.elapsedRealtime();
        for (int i = 0; i < 10; i++) {
            press(KeyEvent.KEYCODE_8);
            await(() -> ready() && surface.getHeight() != height, 10000);
            assertSame(surface, find("reading-content"));
            press(KeyEvent.KEYCODE_0);
            assertEquals("HELP", kind());
            int pureHeight = surface.getHeight();
            press(KeyEvent.KEYCODE_0);
            assertEquals(pureHeight, surface.getHeight());
            press(KeyEvent.KEYCODE_8);
            await(() -> ready() && surface.getHeight() == height, 10000);
            assertEquals(anchor, location());
        }
        report(
                format
                        + " 20 pure toggles / 20 help toggles: "
                        + (SystemClock.elapsedRealtime() - start)
                        + " ms; PSS KiB="
                        + android.os.Debug.getPss());
        DocumentLocation colors = location();
        press(KeyEvent.KEYCODE_5);
        await(this::ready, 10000);
        assertEquals(colors, location());
        assertSame(surface, find("reading-content"));
        screenshot("m4-" + format.toLowerCase() + ".png");
        press(KeyEvent.KEYCODE_8);
        await(this::ready, 10000);
        screenshot("m4-" + format.toLowerCase() + "-pure.png");
        if (format.equals("TXT")) {
            long cpu = android.os.Process.getElapsedCpuTime();
            SystemClock.sleep(30000);
            report(
                    "TXT foreground idle30s CPU ms="
                            + (android.os.Process.getElapsedCpuTime() - cpu)
                            + " PSS KiB="
                            + android.os.Debug.getPss());
            getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
            cpu = android.os.Process.getElapsedCpuTime();
            SystemClock.sleep(30000);
            report(
                    "TXT background idle30s CPU ms="
                            + (android.os.Process.getElapsedCpuTime() - cpu)
                            + " PSS KiB="
                            + android.os.Debug.getPss());
        } else {
            press(KeyEvent.KEYCODE_F4);
            awaitLibrary();
        }
    }

    private void report(String value) throws Exception {
        try (Writer out =
                new OutputStreamWriter(
                        new FileOutputStream(
                                new File(activity.getExternalFilesDir(null), "m4-samples.txt"),
                                true),
                        "UTF-8")) {
            out.write(value + "\n");
        }
    }

    public void testContentsOrdinalAndListPaging() throws Exception {
        File file = new File(activity.getCacheDir(), "m4-contents.txt");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            for (int i = 1; i <= 60; i++) out.write("第" + i + "章 测试\n正文内容。\n");
        }
        ui(() -> activity.importFile(file));
        await(this::ready, 15000);
        press(KeyEvent.KEYCODE_2);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        final int[] selection = {0};
        ui(() -> selection[0] = ((ListView) find("list:CONTENTS")).getSelectedItemPosition());
        assertTrue(selection[0] > 1);
        press(KeyEvent.KEYCODE_F1);
        assertEquals("INPUT", kind());
        ui(
                () ->
                        ((ReaderInputView) findInput(activity.getWindow().getDecorView()))
                                .editor.setText("40"));
        press(KeyEvent.KEYCODE_F3);
        assertEquals("CONTENTS", kind());
        ui(() -> selection[0] = ((ListView) find("list:CONTENTS")).getSelectedItemPosition());
        assertEquals(40, selection[0]);
    }

    private View findInput(View view) {
        if (view instanceof ReaderInputView) return view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View result = findInput(((ViewGroup) view).getChildAt(i));
                if (result != null) return result;
            }
        return null;
    }
}
