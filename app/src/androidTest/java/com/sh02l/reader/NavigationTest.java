package com.sh02l.reader;

import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;

/** Native focus/window tests on API 27; hardware scan-code cases are synthetic. */
@SuppressWarnings("deprecation")
public final class NavigationTest extends InstrumentationTestCase {
    private MainActivity activity;

    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().getTargetContext().getSharedPreferences("prototype", 0).edit().clear().commit();
        Intent intent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> activity.finish());
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }
    private void press(int code) { getInstrumentation().sendKeyDownUpSync(code); getInstrumentation().waitForIdleSync(); }
    private View find(String tag) {
        final View[] found = new View[1];
        getInstrumentation().runOnMainSync(() -> found[0] = activity.getWindow().getDecorView().findViewWithTag(tag));
        return found[0];
    }
    private String focused() {
        final String[] value = new String[1];
        getInstrumentation().runOnMainSync(() -> {
            View focus = activity.getCurrentFocus(); value[0] = focus == null ? null : String.valueOf(focus.getTag());
        });
        return value[0];
    }
    public void testHomeStartsWithARealFocusedAction() {
        assertEquals("item:sample", focused());
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("item:diagnostics", focused());
    }
    public void testFirstMenuAndReturnRestoreTheActualFocus() {
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_F1);
        assertNotNull(find("overlay")); assertEquals("item:settings", focused());
        press(KeyEvent.KEYCODE_F2);
        assertNull(find("overlay")); assertEquals("item:diagnostics", focused());
    }
    public void testUnknownF2ScanClosesOnlyTheConfirmation() {
        press(KeyEvent.KEYCODE_F1);
        for (int i = 0; i < 3; i++) press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertNotNull(find("item:exit-now"));
        final long now = SystemClock.uptimeMillis();
        getInstrumentation().runOnMainSync(() -> {
            activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, 0, 0, 0, 1, 753));
            activity.dispatchKeyEvent(new KeyEvent(now, now + 1, KeyEvent.ACTION_UP, 132, 0, 0, 1, 753));
        });
        assertNotNull(find("overlay")); assertNull(find("item:exit-now"));
        assertEquals("item:exit", focused());
        press(KeyEvent.KEYCODE_F2);
        assertNull(find("overlay"));
    }
    public void testMenuDoesNotResizeTheReadingArea() {
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        final int[] before = new int[2], after = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            View reader = activity.getWindow().getDecorView().findViewWithTag("reading-scroll");
            before[0] = reader.getWidth(); before[1] = reader.getHeight();
        });
        press(KeyEvent.KEYCODE_F1);
        getInstrumentation().runOnMainSync(() -> {
            View reader = activity.getWindow().getDecorView().findViewWithTag("reading-scroll");
            after[0] = reader.getWidth(); after[1] = reader.getHeight();
            assertTrue((activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0);
        });
        assertEquals(before[0], after[0]); assertEquals(before[1], after[1]);
        press(KeyEvent.KEYCODE_F2); assertEquals("reading-content", focused());
    }
    public void testLargeFontSampleKeepsItsScrollPositionAcrossAMenu() {
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        for (int i = 0; i < 12; i++) press(KeyEvent.KEYCODE_6);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        final int[] before = new int[1], after = new int[1];
        getInstrumentation().runOnMainSync(() -> before[0] = activity.getWindow().getDecorView().findViewWithTag("reading-scroll").getScrollY());
        assertTrue("Large text should have scrolled", before[0] > 0);
        press(KeyEvent.KEYCODE_F1); press(KeyEvent.KEYCODE_F2);
        getInstrumentation().runOnMainSync(() -> after[0] = activity.getWindow().getDecorView().findViewWithTag("reading-scroll").getScrollY());
        assertEquals(before[0], after[0]);
    }
    public void testRepeatedMenuCyclesKeepTheSelectedHomeAction() {
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN);
        for (int i = 0; i < 20; i++) { press(KeyEvent.KEYCODE_F1); press(KeyEvent.KEYCODE_F2); }
        assertEquals("item:settings", focused()); assertNull(find("overlay"));
    }
}
