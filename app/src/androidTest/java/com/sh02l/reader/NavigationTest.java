package com.sh02l.reader;

import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.ListView;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.util.List;

/** Hardware injection tests use framework events; physical scan codes are recorded separately. */
@SuppressWarnings("deprecation")
public final class NavigationTest extends InstrumentationTestCase {
    private MainActivity activity;
    private interface Condition { boolean value(); }
    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().getTargetContext().getSharedPreferences("reader", 0).edit().clear().commit();
        getInstrumentation().getTargetContext().getSharedPreferences("prototype", 0).edit().clear().commit();
        Intent intent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().runOnMainSync(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        await(() -> find("item:import") != null, 15000);
    }
    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> activity.finish()); getInstrumentation().waitForIdleSync(); super.tearDown();
    }
    private void press(int code) { getInstrumentation().sendKeyDownUpSync(code); getInstrumentation().waitForIdleSync(); }
    private void await(Condition condition, long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        while (!condition.value() && SystemClock.uptimeMillis() < end) SystemClock.sleep(40);
        assertTrue("Timed out: " + getName(), condition.value());
    }
    private View find(String tag) {
        final View[] found = new View[1]; getInstrumentation().runOnMainSync(() -> found[0] = activity.getWindow().getDecorView().findViewWithTag(tag)); return found[0];
    }
    private String focused() {
        final String[] tag = new String[1]; getInstrumentation().runOnMainSync(() -> {
            View focus = activity.getCurrentFocus(); tag[0] = focus == null ? null : String.valueOf(focus.getTag());
        }); return tag[0];
    }
    private String selected(String listTag) {
        final String[] tag = new String[1]; getInstrumentation().runOnMainSync(() -> {
            ListView list = activity.getWindow().getDecorView().findViewWithTag(listTag);
            View selected = list == null ? null : list.getSelectedView(); tag[0] = selected == null ? null : String.valueOf(selected.getTag());
        }); return tag[0];
    }
    private long offset() { final long[] value = new long[1]; getInstrumentation().runOnMainSync(() -> value[0] = activity.readingOffset()); return value[0]; }
    private long endOffset() { final long[] value = new long[1]; getInstrumentation().runOnMainSync(() -> value[0] = activity.readingEnd()); return value[0]; }
    private boolean ready() { final boolean[] value = new boolean[1]; getInstrumentation().runOnMainSync(() -> value[0] = activity.readingReady()); return value[0]; }
    private boolean committed() { final boolean[] value = new boolean[1]; getInstrumentation().runOnMainSync(() -> value[0] = activity.positionCommitted()); return value[0]; }
    private void openFixture() throws Exception {
        File file = new File(activity.getCacheDir(), "导航验证.txt");
        try (Writer output = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            output.write("第一章 按键与位置\n");
            for (int i = 0; i < 1000; i++) output.write("第" + i + "段：中文、标点和 English 😀。这一段验证稳定分页与菜单焦点。\n");
            output.write("第二章 结束\n末尾没有换行");
        }
        getInstrumentation().runOnMainSync(() -> activity.importFile(file)); await(this::ready, 30000); await(this::committed, 10000);
    }
    public void testLibraryHasARealFocusedImportAction() {
        assertEquals("list:LIBRARY", focused()); assertEquals("item:import", selected("list:LIBRARY"));
        press(KeyEvent.KEYCODE_F3); assertEquals("list:SOURCES", focused()); press(KeyEvent.KEYCODE_F2);
        assertEquals("item:import", selected("list:LIBRARY"));
    }
    public void testFirstMenuRestoresLibrarySelection() {
        press(KeyEvent.KEYCODE_F1); assertNotNull(find("overlay")); assertEquals("item:import", selected("list:MENU"));
        press(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals("item:settings", selected("list:MENU"));
        press(KeyEvent.KEYCODE_F2); assertNull(find("overlay")); assertEquals("item:import", selected("list:LIBRARY"));
    }
    public void testUnknownF2ClosesOnlyTheTopConfirmation() {
        press(KeyEvent.KEYCODE_F1); for (int i = 0; i < 4; i++) press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER); assertNotNull(find("item:exit-now"));
        long now = SystemClock.uptimeMillis();
        getInstrumentation().runOnMainSync(() -> {
            activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, 0, 0, 0, 1, 753));
            activity.dispatchKeyEvent(new KeyEvent(now, now + 1, KeyEvent.ACTION_UP, 132, 0, 0, 1, 753));
        });
        assertNotNull(find("overlay")); assertNull(find("item:exit-now")); assertEquals("item:exit", selected("list:MENU"));
        press(KeyEvent.KEYCODE_F2); assertNull(find("overlay"));
    }
    public void testReaderMenuKeepsTheSameViewSizeAndLocator() throws Exception {
        openFixture(); long before = offset(); View reader = find("reading-content"); int width = reader.getWidth(), height = reader.getHeight();
        press(KeyEvent.KEYCODE_F1); assertSame(reader, find("reading-content"));
        assertEquals(width, reader.getWidth()); assertEquals(height, reader.getHeight());
        press(KeyEvent.KEYCODE_F2); assertEquals(before, offset()); assertEquals("reading-content", focused());
    }
    public void testNextPreviousFontChangeAndNormalReturnPreserveTheTextPosition() throws Exception {
        openFixture(); long start = offset(), end = endOffset(); press(KeyEvent.KEYCODE_DPAD_RIGHT);
        await(() -> ready() && offset() != start, 10000); assertEquals(end, offset());
        press(KeyEvent.KEYCODE_DPAD_LEFT); await(() -> ready() && offset() == start, 10000);
        for (int i = 0; i < 8; i++) { long old = offset(); press(KeyEvent.KEYCODE_DPAD_RIGHT); await(() -> ready() && offset() > old, 10000); }
        long anchor = offset(); press(KeyEvent.KEYCODE_6); await(this::ready, 10000);
        assertTrue(offset() <= anchor && endOffset() > anchor);
        await(this::committed, 10000); long saved = offset();
        press(KeyEvent.KEYCODE_F4); await(() -> find("list:LIBRARY") != null, 10000);
        press(KeyEvent.KEYCODE_DPAD_CENTER); await(this::ready, 10000); assertEquals(saved, offset());
    }
    public void testRepeatedMenuCyclesKeepTheSelection() {
        for (int i = 0; i < 20; i++) { press(KeyEvent.KEYCODE_F1); press(KeyEvent.KEYCODE_F2); }
        assertEquals("list:LIBRARY", focused()); assertEquals("item:import", selected("list:LIBRARY"));
    }
    public void testCancelledImportCannotOpenAStaleReaderAndTheNextImportWorks() throws Exception {
        File file = new File(activity.getCacheDir(), "取消验证.txt");
        try (FileOutputStream output = new FileOutputStream(file)) {
            byte[] block = new byte[16384]; java.util.Arrays.fill(block, (byte) 'a');
            for (int i = 0; i < 128; i++) output.write(block);
        }
        getInstrumentation().runOnMainSync(() -> { activity.importFile(file); activity.perform(com.sh02l.reader.core.Action.BACK); });
        await(() -> find("item:import") != null, 10000); SystemClock.sleep(200);
        final String[] screen = new String[1]; getInstrumentation().runOnMainSync(() -> screen[0] = activity.screenKind());
        assertEquals("LIBRARY", screen[0]); openFixture(); assertTrue(ready()); assertTrue(file.delete());
    }
    public void testRapidLayoutChangesAndBookSwitchDoNotPublishOldText() throws Exception {
        openFixture();
        for (int i = 0; i < 4; i++) press(KeyEvent.KEYCODE_6);
        press(KeyEvent.KEYCODE_F4);
        File file = new File(activity.getCacheDir(), "切书验证.txt");
        String marker = "新书唯一标识" + System.nanoTime();
        try (Writer output = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) { output.write(marker + "\n这本书应保持当前内容。\n结束。"); }
        getInstrumentation().runOnMainSync(() -> activity.importFile(file)); await(this::ready, 10000); SystemClock.sleep(200);
        final String[] text = new String[1]; getInstrumentation().runOnMainSync(() -> text[0] = activity.readingText());
        assertTrue(text[0], text[0].startsWith(marker)); assertTrue(file.delete());
    }
    public void testSavedBookmarkReturnsToItsTextAfterFontChange() throws Exception {
        openFixture(); long old = offset(); press(KeyEvent.KEYCODE_DPAD_RIGHT); await(() -> ready() && offset() > old, 10000);
        long bookmarked = offset(); press(KeyEvent.KEYCODE_POUND); press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> {
            final boolean[] saved = new boolean[1]; getInstrumentation().runOnMainSync(() -> {
                android.widget.TextView notice = activity.getWindow().getDecorView().findViewWithTag("notice");
                saved[0] = notice != null && "已添加书签".contentEquals(notice.getText());
            }); return saved[0];
        }, 10000);
        press(KeyEvent.KEYCODE_6); await(this::ready, 10000); press(KeyEvent.KEYCODE_POUND);
        await(() -> find("item:bookmark-" + bookmarked) != null, 10000);
        // Select the bookmark with this exact position; older fixtures may already have bookmarks.
        List<TextImporter.Chapter> stored = BookRepository.obtain(activity).bookmarks(
                BookRepository.obtain(activity).list().stream().filter(book -> "导航验证".equals(book.title)).findFirst().get().id);
        int index = 1; for (TextImporter.Chapter bookmark : stored) { if (bookmark.offset == bookmarked) break; index++; }
        for (int i = 0; i < index; i++) press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER); // M2 exposes jump / rename / delete for the selected bookmark.
        press(KeyEvent.KEYCODE_DPAD_CENTER); await(this::ready, 10000);
        assertTrue(offset() <= bookmarked && endOffset() > bookmarked);
    }
}
