package com.sh02l.reader;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

import com.sh02l.reader.core.DocumentLocation;
import com.sh02l.reader.core.ReadingPosition;
import com.sh02l.reader.core.text.TextImporter;

import java.io.*;
import java.util.UUID;

/** Small durable crash states, isolated from both the release and debug application libraries. */
@SuppressWarnings("deprecation")
public final class M3WriteBoundaryTest extends InstrumentationTestCase {
    private LibraryManagementTest.TestContext context;
    private BookRepository repository;

    protected void setUp() throws Exception {
        super.setUp();
        context = new LibraryManagementTest.TestContext(getInstrumentation().getTargetContext());
        repository = LibraryManagementTest.open(context);
    }

    protected void tearDown() throws Exception {
        if (repository != null) repository.close();
        LibraryManagementTest.remove(context.root);
        super.tearDown();
    }

    private void reopen() throws Exception {
        repository.close();
        repository = LibraryManagementTest.open(context);
        repository.cleanupStaging();
    }

    public void testProgressWriteKeepsWholeLocator() throws Exception {
        File source = EpubFixtures.create(context.root);
        BookRepository.Book book =
                repository.importBook(new FileInputStream(source), "fixture.epub", null);
        ReadingPosition old =
                new ReadingPosition(
                        new DocumentLocation("epub", 1, "OPS/one.xhtml\u001f7\u001f"), .1);
        ReadingPosition next =
                new ReadingPosition(
                        new DocumentLocation("epub", 1, "OPS/two.xhtml\u001f42\u001f"), .7);
        repository.saveProgress(book.id, old);
        BookRepository.Book committed = repository.get(book.id);
        repository
                .getWritableDatabase()
                .execSQL(
                        "CREATE TRIGGER reject_m3 BEFORE UPDATE ON books BEGIN SELECT"
                            + " RAISE(ABORT,'m3 write interrupted'); END");
        try {
            repository.saveProgress(book.id, next);
            fail("Write veto was ignored");
        } catch (android.database.sqlite.SQLiteException expected) {
        }
        reopen();
        assertEquals(committed.location, repository.get(book.id).location);
        assertEquals(committed.offset, repository.get(book.id).offset);
        assertEquals(committed.progression, repository.get(book.id).progression, 0);
        repository.getWritableDatabase().execSQL("DROP TRIGGER reject_m3");
        repository.saveProgress(book.id, next);
        reopen();
        assertEquals(next.location, repository.get(book.id).location);
        assertEquals(next.progression, repository.get(book.id).progression, 0);
        assertEquals(Math.round(next.progression * book.length), repository.get(book.id).offset);
    }

    public void testUncommittedImportCanBeRetriedSafely() throws Exception {
        File source = new File(context.root, "keep-source.txt");
        byte[] bytes = "第一章 导入提交边界\n私有原件发布后，书目提交前中断。".getBytes("UTF-8");
        try (FileOutputStream out = new FileOutputStream(source)) {
            out.write(bytes);
        }
        String hash = CoverStore.hash(source, null);
        TextImporter.Observer interrupted =
                new TextImporter.Observer() {
                    public void progress(String phase, long count) {}

                    public boolean cancelled() {
                        return false;
                    }

                    public void beforeWrite(long count) throws IOException {
                        if (count == 65536)
                            throw new IOException("interrupted before catalogue commit");
                    }
                };
        try {
            repository.importBook(new FileInputStream(source), "source.txt", interrupted);
            fail("Commit boundary was not reached");
        } catch (IOException expected) {
        }
        assertNull(repository.get(hash));
        assertTrue(new File(repository.directory(hash), "original.bin").isFile());
        reopen();
        assertEquals(0, repository.count());
        BookRepository.Book imported =
                repository.importBook(new FileInputStream(source), "source.txt", null);
        assertEquals(hash, imported.id);
        assertEquals(1, repository.count());
        assertEquals(hash, CoverStore.hash(repository.original(hash), null));
        assertTrue(source.isFile());
        assertEquals(hash, CoverStore.hash(source, null));
    }

    public void testInterruptedRebuildChoosesCommittedGeneration() throws Exception {
        BookRepository.Book book = LibraryManagementTest.importText(repository, "ABCDEFGH");
        repository.saveProgress(book.id, 6);
        repository.addBookmark(book.id, 4, "keep");
        for (boolean commit : new boolean[] {false, true}) {
            String token = UUID.randomUUID().toString();
            File root = repository.directory(book.id).getParentFile();
            File destination = repository.directory(book.id);
            File previous = new File(root, ".previous-" + book.id + "-" + token);
            File staging = new File(root, ".rebuild-" + book.id + "-" + token);
            TextImporter.Result prepared =
                    TextImporter.prepare(
                            new FileInputStream(repository.original(book.id)),
                            staging,
                            "UTF-16LE",
                            null);
            TxtCacheFingerprint.write(staging, null);
            // This is the persisted state at a process exit: no production finally/catch ran.
            assertTrue(destination.renameTo(previous));
            assertTrue(staging.renameTo(destination));
            if (commit) {
                SQLiteDatabase db = repository.getWritableDatabase();
                db.beginTransaction();
                try {
                    ContentValues values = new ContentValues();
                    values.put("charset", prepared.charset);
                    values.put("length", prepared.length);
                    values.put("offset", 3);
                    values.put("locator_payload", "3");
                    db.update("books", values, "id=?", new String[] {book.id});
                    ContentValues mark = new ContentValues();
                    mark.put("offset", 2);
                    mark.put("locator_payload", "2");
                    db.update("bookmarks", mark, "book_id=?", new String[] {book.id});
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            reopen();
            BookRepository.Book recovered =
                    repository.ensureCache(book.id, null) != null ? repository.get(book.id) : null;
            assertNotNull(recovered);
            assertEquals(commit ? "UTF-16LE" : "UTF-8", recovered.charset);
            assertEquals(commit ? 4L : 8L, recovered.length);
            assertEquals(commit ? 3L : 6L, recovered.offset);
            assertEquals(commit ? 2L : 4L, repository.bookmarks(book.id).get(0).offset);
            assertEquals(book.id, CoverStore.hash(repository.original(book.id), null));
            assertFalse(previous.exists());
            assertFalse(staging.exists());
        }
    }
}
