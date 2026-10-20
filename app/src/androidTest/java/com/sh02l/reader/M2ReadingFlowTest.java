package com.sh02l.reader;

import android.content.ContentValues;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ListView;

/** A small M2 slice through real activity routing, input, search and relayout. */
@SuppressWarnings("deprecation")
public final class M2ReadingFlowTest extends NativeTestBase {
    public void testInputDigitsDoNotOpenHelpAndCancelPreservesPosition() throws Exception {
        fixture();
        long before = offset();
        ui(() -> activity.input("数字输入", "", true, value -> null));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_2);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_0);
        getInstrumentation().waitForIdleSync();
        assertEquals("INPUT", kind());
        final String[] text = {""};
        ui(() -> text[0] = ((EditText) find("reader-input")).getText().toString());
        assertEquals("20", text[0]);
        press(KeyEvent.KEYCODE_F4);
        assertEquals("READER", kind());
        assertEquals(before, offset());
    }

    public void testJumpSearchAndMarginChangeKeepUsableLocators() throws Exception {
        fixture();
        press(KeyEvent.KEYCODE_F1);
        select("jump");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        ui(() -> ((EditText) find("reader-input")).setText("50"));
        press(KeyEvent.KEYCODE_F3);
        await(this::ready, 15000);
        assertTrue(offset() > 0);
        long anchor = offset();
        ui(
                () -> {
                    activity.readingSettings().horizontal = 24;
                    activity.readingSettings().lineExtra = 8;
                    activity.layoutChanged();
                });
        await(this::ready, 15000);
        assertTrue(offset() <= anchor);
        assertTrue(activity.readingEnd() > anchor);
        press(KeyEvent.KEYCODE_F1);
        select("search");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        ui(() -> ((EditText) find("reader-input")).setText("English"));
        press(KeyEvent.KEYCODE_F3);
        await(
                () -> {
                    final boolean[] found = {false};
                    ui(
                            () -> {
                                ListView list = (ListView) find("list:SEARCH_RESULTS");
                                found[0] = list != null && list.getCount() > 1;
                            });
                    return found[0];
                },
                15000);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(this::ready, 15000);
        assertEquals("READER", kind());
        assertTrue(activity.readingText().contains("English"));
    }

    public void testConfirmedRenameRefreshesShelfAfterInputCloses() throws Exception {
        fixture();
        final String[] id = {""};
        ui(() -> id[0] = activity.reading().book.id);
        ui(
                () ->
                        activity.input(
                                "重命名图书",
                                "",
                                false,
                                value -> {
                                    activity.task(
                                            "保存书名",
                                            () -> {
                                                activity.repository().renameBook(id[0], value);
                                                return id[0];
                                            },
                                            activity::refreshShelf);
                                    return null;
                                }));
        ui(() -> ((EditText) find("reader-input")).setText("M2 新书名"));
        press(KeyEvent.KEYCODE_F3);
        await(() -> "LIBRARY".equals(kind()) && activity.libraryReady(), 15000);
        assertEquals("M2 新书名", activity.repository().get(id[0]).title);
    }

    public void testShelfBatchActionsHaveTheirOwnLabelAndReachTheNextBatch() throws Exception {
        BookRepository repository = activity.repository();
        try {
            for (int i = 0; i < 51; i++) {
                ContentValues values = new ContentValues();
                values.put("id", String.format(java.util.Locale.ROOT, "%064x", 4096 + i));
                values.put("title", "M2-page-fixture-" + i);
                values.put("charset", "UTF-8");
                values.put("length", 0);
                values.put("source_bytes", 0);
                values.put("opened", -1);
                repository.getWritableDatabase().insertOrThrow("books", null, values);
            }
            ui(() -> activity.refreshShelf(""));
            awaitLibrary();
            // A batch contains 50 books plus import, so the paging action can be past the old
            // 50-step helper limit.
            for (int i = 0; i < 60 && !"item:next-batch".equals(selected()); i++)
                press(KeyEvent.KEYCODE_DPAD_DOWN);
            assertEquals("item:next-batch", selected());
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            awaitLibrary();
            final String[] title = {""};
            ui(
                    () ->
                            title[0] =
                                    ((android.widget.TextView) find("screen-title"))
                                            .getText()
                                            .toString());
            assertTrue(title[0].contains("2/"));
        } finally {
            repository
                    .getWritableDatabase()
                    .delete("books", "title LIKE ?", new String[] {"M2-page-fixture-%"});
        }
    }

    public void testDeleteThenReimportDoesNotRestoreDeletedInMemoryProgress() throws Exception {
        fixture();
        final String[] id = {""};
        final long[] length = {0};
        ui(
                () -> {
                    id[0] = activity.reading().book.id;
                    length[0] = activity.reading().book.length;
                    activity.seekTo(length[0] / 2);
                });
        await(() -> ready() && offset() > 0, 15000);
        ui(() -> activity.refreshShelf(id[0]));
        awaitLibrary();
        press(KeyEvent.KEYCODE_F1);
        select("delete-book");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> find("item:confirm-operation") != null, 10000);
        select("confirm-operation");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> activity.repository().get(id[0]) == null && activity.libraryReady(), 15000);
        fixture();
        assertEquals(0L, offset());
    }
}
