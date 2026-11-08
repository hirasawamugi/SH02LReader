package com.sh02l.reader;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;

import com.sh02l.reader.core.DocumentLocation;

import java.io.*;
import java.lang.reflect.Field;

/** Focused review of interruption and failure paths that ordinary happy-path tests miss. */
public final class M41ReviewTest extends NativeTestBase {
    private AppearanceFeatures appearance() throws Exception {
        press(KeyEvent.KEYCODE_F3);
        select("appearance");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Field field = MainActivity.class.getDeclaredField("appearanceFeatures");
        field.setAccessible(true);
        return (AppearanceFeatures) field.get(activity);
    }

    private boolean working(AppearanceFeatures features, String name) {
        final boolean[] value = {false};
        ui(
                () -> {
                    try {
                        Field field = AppearanceFeatures.class.getDeclaredField(name);
                        field.setAccessible(true);
                        value[0] = field.getBoolean(features);
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                });
        return value[0];
    }

    public void testHelpCannotDivertAppearanceSaveCompletion() throws Exception {
        AppearanceFeatures features = appearance();
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        Field field = BackgroundStore.class.getDeclaredField("TRANSACTION");
        field.setAccessible(true);
        synchronized (field.get(null)) {
            press(KeyEvent.KEYCODE_F3);
            assertTrue(working(features, "committing"));
            // Deterministically press help during slow storage without actually filling storage.
            press(KeyEvent.KEYCODE_0);
            assertEquals("THEME_EDITOR", kind());
        }
        await(() -> !working(features, "committing"), 10000);
        assertEquals("A completed save must return from its own editor", "MENU", kind());
        assertEquals(1, new BackgroundStore(activity).load(0, 0).preset);
        press(KeyEvent.KEYCODE_0);
        assertEquals("HELP", kind());
        press(KeyEvent.KEYCODE_F4);
        assertEquals("MENU", kind());
    }

    public void testFailedBackgroundCanRetryAndCancelWithoutChangingSavedTheme() throws Exception {
        AppearanceFeatures features = appearance();
        File source = new File(activity.getCacheDir(), "review-background.png");
        try {
            try (OutputStream out = new FileOutputStream(source)) {
                out.write(new byte[] {1, 2, 3});
            }
            select("theme-image");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            ui(() -> features.picked(Uri.fromFile(source)));
            await(() -> !working(features, "busy"), 10000);
            assertEquals("THEME_EDITOR", kind());
            assertEquals("", new BackgroundStore(activity).load(0, 0).asset);
            Bitmap bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.BLUE);
            try (OutputStream out = new FileOutputStream(source)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } finally {
                bitmap.recycle();
            }
            select("theme-image");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            ui(() -> features.picked(Uri.fromFile(source)));
            press(KeyEvent.KEYCODE_F4);
            assertEquals("MENU", kind());
            assertEquals("", new BackgroundStore(activity).load(0, 0).asset);
            press(KeyEvent.KEYCODE_F4);
            awaitLibrary();
        } finally {
            source.delete();
        }
    }

    public void testAppearanceSaveUnderTxtRetainsPositionAndView() throws Exception {
        fixture();
        checkReaderAppearance();
    }

    public void testAppearanceSaveUnderEpubRetainsPositionAndView() throws Exception {
        File source = EpubFixtures.illustrated(activity.getCacheDir(), false);
        BookRepository.Book book =
                BookRepository.obtain(activity)
                        .importBook(new FileInputStream(source), source.getName(), null);
        ui(() -> activity.open(book));
        await(this::ready, 15000);
        checkReaderAppearance();
    }

    private void checkReaderAppearance() throws Exception {
        final View[] view = {null};
        final DocumentLocation[] location = {null};
        ui(
                () -> {
                    view[0] = activity.reading().surface.view();
                    location[0] = activity.reading().position.location;
                });
        appearance();
        select("theme-preset");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_F3);
        await(() -> "MENU".equals(kind()), 10000);
        press(KeyEvent.KEYCODE_F4);
        await(this::ready, 10000);
        ui(
                () -> {
                    assertSame(view[0], activity.reading().surface.view());
                    assertEquals(location[0], activity.reading().position.location);
                });
    }
}
