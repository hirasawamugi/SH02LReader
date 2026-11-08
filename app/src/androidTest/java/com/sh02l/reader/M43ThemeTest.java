package com.sh02l.reader;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

import com.sh02l.reader.core.*;

import java.io.*;
import java.util.concurrent.*;

public final class M43ThemeTest extends NativeTestBase {
    private void addBook(String name) throws Exception {
        File file = new File(activity.getCacheDir(), name + ".txt");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            out.write("第一章 " + name + "\n");
            for (int i = 0; i < 80; i++) out.write("用于验证主题的本地测试正文。" + i + "\n");
        }
        ui(() -> activity.importFile(file));
        await(this::ready, 15000);
        press(KeyEvent.KEYCODE_F4);
        awaitLibrary();
        assertTrue(file.delete());
    }

    private void screenshot(String name) throws Exception {
        CountDownLatch drawn = new CountDownLatch(1);
        ui(
                () ->
                        activity.getWindow()
                                .getDecorView()
                                .postOnAnimation(
                                        () ->
                                                activity.getWindow()
                                                        .getDecorView()
                                                        .postOnAnimation(drawn::countDown)));
        assertTrue(drawn.await(3, TimeUnit.SECONDS));
        getInstrumentation().getUiAutomation().waitForIdle(150, 2000);
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out =
                new FileOutputStream(
                        new File(activity.getExternalFilesDir(null), "m43-" + name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    private void checkSelectedContrast() {
        ui(
                () -> {
                    ListView list = (ListView) find("list:" + kind());
                    View row = list.getSelectedView();
                    int background =
                            ((GradientDrawable) row.getBackground()).getColor().getDefaultColor();
                    checkText(row, background);
                    ImageView icon = row.findViewWithTag("book-cover");
                    if (icon != null && icon.getVisibility() == View.VISIBLE) {
                        assertTrue(
                                ColorContrast.ratio(
                                                icon.getImageTintList().getDefaultColor(),
                                                background)
                                        >= 3);
                    }
                });
    }

    private void checkText(View view, int background) {
        if (view instanceof TextView) {
            assertTrue(
                    "Focus text must be readable",
                    ColorContrast.ratio(((TextView) view).getCurrentTextColor(), background)
                            >= 4.5);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                checkText(group.getChildAt(i), background);
        }
    }

    public void testPresetSwitchWithKeysAndReadableShelfMenu() throws Exception {
        addBook("第一卷 · 风起之时");
        addBook("第二卷 · 夜城来信");
        // Let the platform import toast expire before capturing the theme itself.
        android.os.SystemClock.sleep(3000);
        for (int preset = 3; preset <= 4; preset++) {
            press(KeyEvent.KEYCODE_F3);
            select("appearance");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            select("theme-preset");
            int before = new BackgroundStore(activity).load(0, 0).preset;
            for (int n = before; n != preset; n = (n + 1) % 5) press(KeyEvent.KEYCODE_DPAD_RIGHT);
            press(KeyEvent.KEYCODE_F3);
            await(() -> "MENU".equals(kind()), 10000);
            assertEquals(preset, new BackgroundStore(activity).load(0, 0).preset);
            checkSelectedContrast();
            String name = preset == 3 ? "miku" : "edgerunners";
            screenshot(name + "-menu");
            press(KeyEvent.KEYCODE_F4);
            select("book");
            checkSelectedContrast();
            screenshot(name + "-shelf");
            press(KeyEvent.KEYCODE_DPAD_DOWN);
            checkSelectedContrast();
            press(KeyEvent.KEYCODE_DPAD_UP);
            checkSelectedContrast();
        }
    }

    public void testFocusRebindPreservesCoverAndRetintsOnlyDefaultIcons() {
        ui(
                () -> {
                    Palette p = new ThemeSpec(4, 2).palette();
                    BookRowView row = new BookRowView(activity);
                    LibraryAdapter.Item book =
                            new LibraryAdapter.Item(
                                    new BookRepository.Book(
                                            "test-book", "测试封面", "UTF-8", 100, 100, 0),
                                    false,
                                    0);
                    Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
                    try {
                        row.bind(book, AppearanceState.COVER, p, false);
                        row.setCover(bitmap);
                        row.bind(book, AppearanceState.COVER, p, true);
                        ImageView image = row.findViewWithTag("book-cover");
                        assertNull("User cover must not be tinted", image.getImageTintList());
                        row.setCover(null);
                        assertEquals(p.accent, image.getImageTintList().getDefaultColor());
                        row.bind(book, AppearanceState.ICON, p, true);
                        assertEquals(p.focusAccent, image.getImageTintList().getDefaultColor());
                        row.bind(book, AppearanceState.ICON, p, false);
                        assertEquals(p.accent, image.getImageTintList().getDefaultColor());
                    } finally {
                        bitmap.recycle();
                    }
                });
    }
}
