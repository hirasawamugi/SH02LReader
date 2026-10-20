package com.sh02l.reader;

import android.text.TextUtils;
import android.view.*;
import android.widget.*;

import java.io.*;

public final class LibraryAppearanceTest extends NativeTestBase {
    private View selectedRow() {
        final View[] row = {null};
        ui(() -> row[0] = ((ListView) find("list:LIBRARY")).getSelectedView());
        return row[0];
    }

    private View awaitSelectedRow() {
        final View[] row = {null};
        await(
                () -> {
                    row[0] = selectedRow();
                    return row[0] != null;
                },
                10000);
        return row[0];
    }

    public void testContinueBookAppearsOnce() throws Exception {
        fixture();
        press(KeyEvent.KEYCODE_F4);
        await(() -> find("list:LIBRARY") != null && selected() != null, 10000);
        final int[] matches = {0};
        ui(
                () -> {
                    ListView list = (ListView) find("list:LIBRARY");
                    for (int i = 0; i < list.getCount(); i++) {
                        View row = list.getAdapter().getView(i, null, list);
                        CharSequence text = row.getContentDescription();
                        if (text != null && text.toString().contains("appearance-fixture"))
                            matches[0]++;
                    }
                });
        assertEquals("Continue-reading book must not be duplicated", 1, matches[0]);
    }

    public void testStyleChangeKeepsSelectedBook() throws Exception {
        fixture();
        press(KeyEvent.KEYCODE_F4);
        View initial = awaitSelectedRow();
        assertEquals("item:book", initial.getTag());
        String before = String.valueOf(initial.getContentDescription());
        press(KeyEvent.KEYCODE_F3);
        select("settings");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        select("appearance");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        select("shelf-style");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        for (int i = 0; i < 4 && !"LIBRARY".equals(kind()); i++) press(KeyEvent.KEYCODE_F4);
        View changed = awaitSelectedRow();
        assertEquals(before, String.valueOf(changed.getContentDescription()));
        assertNotNull("Cover mode must render a cover", changed.findViewWithTag("book-cover"));
    }

    public void testLongTitleAndEmptyShelfRemainUsable() throws Exception {
        ui(
                () -> {
                    BookRepository repo = BookRepository.obtain(activity);
                    repo.getWritableDatabase().delete("bookmarks", null, null);
                    repo.getWritableDatabase().delete("books", null, null);
                    activity.perform(com.sh02l.reader.core.Action.HOME);
                });
        await(() -> listCount() == 1, 10000);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("SOURCES", kind());
        press(KeyEvent.KEYCODE_F4);
        String title = "沿着山脊慢慢走直到看见远处的海以及更多没有显示完的书名";
        File file = new File(activity.getCacheDir(), title + ".txt");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            out.write("long-title unique fixture\n正文。\n");
        }
        ui(() -> activity.importFile(file));
        await(this::ready, 10000);
        press(KeyEvent.KEYCODE_F4);
        await(() -> selected() != null, 10000);
        TextView label = selectedRow().findViewWithTag("book-title");
        assertNotNull("Book name has a dedicated single-line view", label);
        assertEquals(1, label.getMaxLines());
        assertEquals(TextUtils.TruncateAt.MARQUEE, label.getEllipsize());
        assertTrue(label.isSelected());
        final java.util.List<Integer> heights = new java.util.ArrayList<>();
        ui(
                () -> {
                    ListView list = (ListView) find("list:LIBRARY");
                    for (int i = 0; i < list.getCount(); i++) {
                        View row = list.getAdapter().getView(i, null, list);
                        row.measure(
                                View.MeasureSpec.makeMeasureSpec(
                                        list.getWidth(), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        heights.add(row.getMeasuredHeight());
                    }
                });
        for (int height : heights)
            assertEquals(
                    "Same-level rows must keep equal height", heights.get(0).intValue(), height);
        press(KeyEvent.KEYCODE_F1);
        assertFalse("Covered labels must stop scrolling", label.isSelected());
        press(KeyEvent.KEYCODE_F4);
        await(() -> selected() != null, 10000);
        TextView resumed = selectedRow().findViewWithTag("book-title");
        assertTrue(resumed.isSelected());
    }
}
