package com.sh02l.reader;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real private files and SQLite; fixtures never share the application's library. */
@SuppressWarnings("deprecation")
public final class LibraryManagementTest extends InstrumentationTestCase {
    private TestContext context;
    private BookRepository repository;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = new TestContext(getInstrumentation().getTargetContext());
        repository = open(context);
    }
    @Override protected void tearDown() throws Exception {
        if (repository != null) repository.close();
        remove(context.root);
        super.tearDown();
    }
    static BookRepository open(Context context) throws Exception {
        Constructor<BookRepository> constructor = BookRepository.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        return constructor.newInstance(context);
    }
    static BookRepository.Book importText(BookRepository repository, String text) throws Exception {
        return repository.importBook(new ByteArrayInputStream(text.getBytes("UTF-8")), "fixture.txt", null);
    }

    public void testIdenticalOriginalCanBeImportedAfterEncodingCorrectionAndCacheCleanup() throws Exception {
        BookRepository.Book book = importText(repository, "ABCDEFGH");
        repository.rebuild(book.id, "UTF-16LE", null);
        BookRepository.Book duplicate = importText(repository, "ABCDEFGH");
        assertEquals(book.id, duplicate.id); assertEquals("UTF-16LE", duplicate.charset); assertEquals(1, repository.count());
        new StorageManager(context, repository).clearCaches("");
        duplicate = importText(repository, "ABCDEFGH");
        assertEquals(book.id, duplicate.id); assertEquals("UTF-16LE", duplicate.charset); assertEquals(1, repository.count());
        assertEquals("UTF-16LE", repository.ensureCache(book.id, null).charset);
    }

    public void testDequeuedProgressCannotUpdateADeletedAndReimportedBook() throws Exception {
        BookRepository.Book book = importText(repository, "discard old pending progress");
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
        ProgressWriter writer = new ProgressWriter(repository, (id, offset, error) -> callbacks.incrementAndGet());
        java.lang.reflect.Field pendingField = ProgressWriter.class.getDeclaredField("pending"); pendingField.setAccessible(true);
        try {
            synchronized (repository) {
                writer.save(book.id, 10);
                // Hold the repository so the worker is deterministically parked after dequeue, before its write.
                long deadline = android.os.SystemClock.uptimeMillis() + 5000;
                boolean dequeued = false;
                while (android.os.SystemClock.uptimeMillis() < deadline) {
                    synchronized (writer) { dequeued = ((java.util.Map<?, ?>)pendingField.get(writer)).isEmpty(); }
                    if (dequeued) break; android.os.SystemClock.sleep(5);
                }
                assertTrue("writer did not dequeue", dequeued);
                writer.forget(book.id); repository.deleteBook(book.id);
                assertEquals(book.id, importText(repository, "discard old pending progress").id);
            }
            assertTrue(writer.awaitIdle(5000));
            assertEquals(0L, repository.get(book.id).offset); assertEquals(0, callbacks.get());
        } finally { writer.close(); }
    }

    public void testDeleteRemovesOnlySelectedPrivateBookAndCover() throws Exception {
        File source = new File(context.root, "keep-source.txt");
        try (FileOutputStream out = new FileOutputStream(source)) { out.write("source stays".getBytes("UTF-8")); }
        BookRepository.Book selected;
        try (InputStream in = new FileInputStream(source)) { selected = repository.importBook(in, "selected.txt", null); }
        BookRepository.Book other = importText(repository, "other book");
        repository.addBookmark(selected.id, 2, "remove me");
        repository.addBookmark(other.id, 3, "keep me");
        CoverStore covers = new CoverStore(context);
        covers.importCover(selected.id, new ByteArrayInputStream(CoverFixtures.png(20, 30)), "cover.png", null);
        repository.getWritableDatabase().execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON books BEGIN SELECT RAISE(FAIL,'delete veto'); END");
        try { repository.deleteBook(selected.id); fail("Catalogue veto ignored"); } catch (IOException expected) { }
        assertNotNull(repository.get(selected.id));
        assertTrue(repository.original(selected.id).isFile());
        assertNotNull(covers.get(selected.id));
        assertEquals(1, repository.bookmarks(selected.id).size());
        repository.getWritableDatabase().execSQL("DROP TRIGGER reject_delete");
        repository.deleteBook(selected.id);
        assertNull(repository.get(selected.id));
        assertTrue(repository.bookmarks(selected.id).isEmpty());
        assertFalse(repository.directory(selected.id).exists());
        assertNull(covers.get(selected.id));
        assertTrue(source.isFile());
        assertTrue(repository.original(other.id).isFile());
        assertEquals("keep me", repository.bookmarks(other.id).get(0).title);
    }

    public void testMissingCacheRebuildKeepsOriginalProgressAndBookmarks() throws Exception {
        BookRepository.Book book = importText(repository, "第一章\nABC\nXYZ");
        repository.saveProgress(book.id, 7);
        repository.addBookmark(book.id, 4, "chapter");
        File original = repository.original(book.id);
        String hash = CoverStore.hash(original);
        assertTrue(new File(repository.directory(book.id), "text.u16").delete());
        assertTrue(new File(repository.directory(book.id), "index.bin").delete());
        TextImporter.Result rebuilt = repository.ensureCache(book.id, null);
        assertEquals(book.id, rebuilt.id);
        assertEquals(hash, CoverStore.hash(repository.original(book.id)));
        assertEquals(7L, repository.get(book.id).offset);
        assertEquals(4L, repository.bookmarks(book.id).get(0).offset);
        assertEquals("chapter", repository.bookmarks(book.id).get(0).title);
        File text = new File(repository.directory(book.id), "text.u16");
        String cacheHash = CoverStore.hash(text);
        try (RandomAccessFile file = new RandomAccessFile(text, "rw")) { file.write(0); file.getFD().sync(); }
        repository.ensureCache(book.id, null);
        assertEquals("Same-length cache corruption must be repaired", cacheHash, CoverStore.hash(text));
        assertTrue(new File(repository.directory(book.id), "cache.sha256").delete());
        TextImporter.Observer noSpace = new TextImporter.Observer() {
            public void progress(String phase, long bytes) { }
            public boolean cancelled() { return false; }
            public void beforeWrite(long bytes) throws IOException { throw new IOException("no optional fingerprint space"); }
        };
        assertEquals("A legacy cache stays readable when its fingerprint cannot be written", book.id, repository.ensureCache(book.id, noSpace).id);
        assertFalse(new File(repository.directory(book.id), "cache.sha256").exists());
    }

    public void testVersionOneMigrationPreservesRowsAndProgress() throws Exception {
        repository.close();
        context.deleteDatabase("library.db");
        String id = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        try (SQLiteDatabase db = context.openOrCreateDatabase("library.db", 0, null)) {
            db.execSQL("CREATE TABLE books(id TEXT PRIMARY KEY,title TEXT NOT NULL,charset TEXT NOT NULL,length INTEGER NOT NULL,source_bytes INTEGER NOT NULL,offset INTEGER NOT NULL DEFAULT 0,locator_version INTEGER NOT NULL DEFAULT 1,opened INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE bookmarks(book_id TEXT NOT NULL,offset INTEGER NOT NULL,label TEXT NOT NULL,PRIMARY KEY(book_id,offset))");
            db.execSQL("INSERT INTO books(id,title,charset,length,source_bytes,offset,opened) VALUES(?, 'old title','UTF-8',100,100,42,123)", new Object[]{id});
            db.execSQL("INSERT INTO bookmarks VALUES(?,7,'old bookmark')", new Object[]{id});
            db.setVersion(1);
        }
        repository = open(context);
        assertEquals(2, repository.getReadableDatabase().getVersion());
        assertEquals(1, repository.count());
        assertEquals(42L, repository.get(id).offset);
        assertEquals("old bookmark", repository.bookmarks(id).get(0).title);
        repository.renameBook(id, "renamed");
        repository.renameBookmark(id, 7, "renamed bookmark");
        assertEquals("renamed", repository.listPage(0, 50).get(0).title);
        assertEquals(0, repository.pageOf(id, 50));
        repository.removeBookmark(id, 7);
        assertTrue(repository.bookmarks(id).isEmpty());
    }

    public void testEncodingChangeMapsAnchorsAndFailedCommitRestoresOldCache() throws Exception {
        BookRepository.Book book = importText(repository, "ABCDEFGH"); // Eight UTF-8 units become four UTF-16LE units.
        repository.saveProgress(book.id, 6);
        repository.addBookmark(book.id, 2, "keep");
        File text = new File(repository.directory(book.id), "text.u16");
        String oldCache = CoverStore.hash(text);
        repository.getWritableDatabase().execSQL("CREATE TRIGGER reject_rebuild BEFORE UPDATE OF charset ON books BEGIN SELECT RAISE(FAIL,'rebuild veto'); END");
        try { repository.rebuild(book.id, "UTF-16LE", null); fail("Rebuild catalogue veto ignored"); } catch (IOException expected) { }
        assertEquals("UTF-8", repository.get(book.id).charset);
        assertEquals(oldCache, CoverStore.hash(text));
        repository.getWritableDatabase().execSQL("DROP TRIGGER reject_rebuild");
        BookRepository.Book changed = repository.rebuild(book.id, "UTF-16LE", null);
        assertEquals("UTF-16LE", changed.charset);
        assertEquals(4L, changed.length);
        assertEquals(3L, changed.offset);
        assertEquals(1L, repository.bookmarks(book.id).get(0).offset);
        assertEquals(book.id, CoverStore.hash(repository.original(book.id)));
    }

    public void testFailedProgressRemainsPendingUntilExplicitRetry() throws Exception {
        BookRepository.Book book = importText(repository, "progress fixture");
        StorageManager storage = new StorageManager(context, repository);
        storage.prepareReserve();
        File reserve = new File(context.files, "progress-reserve.bin");
        assertEquals(262144L, reserve.length());
        assertTrue("The emergency reserve must allocate real blocks", android.system.Os.stat(reserve.getPath()).st_blocks * 512 >= 262144);
        repository.getWritableDatabase().execSQL("CREATE TRIGGER reject_progress BEFORE UPDATE OF offset ON books BEGIN SELECT RAISE(FAIL,'write veto'); END");
        CountDownLatch failed = new CountDownLatch(1), saved = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<ProgressWriter> current = new AtomicReference<>();
        ProgressWriter writer = new ProgressWriter(repository, storage, (id, offset, error) -> {
            if (error == null) saved.countDown();
            else if (failures.incrementAndGet() == 1) current.get().save(id, 12);
            else failed.countDown();
        });
        current.set(writer);
        try {
            writer.save(book.id, 10);
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertFalse(writer.awaitIdle(5000));
            assertEquals("Failed positions must wait instead of busy-looping", 2, failures.get());
            assertFalse(reserve.exists());
            assertEquals(0L, repository.get(book.id).offset);
            repository.getWritableDatabase().execSQL("DROP TRIGGER reject_progress");
            writer.retry();
            assertTrue(saved.await(5, TimeUnit.SECONDS));
            assertTrue(writer.awaitIdle(5000));
            assertEquals("The newer failed position must win", 12L, repository.get(book.id).offset);
        } finally { writer.close(); }
    }

    static final class TestContext extends ContextWrapper {
        final File root, files, databases, imports;
        TestContext(Context base) {
            super(base);
            root = new File(base.getCacheDir(), "library-m2-" + UUID.randomUUID());
            files = new File(root, "files"); databases = new File(root, "databases"); imports = new File(root, "import");
            files.mkdirs(); databases.mkdirs(); imports.mkdirs();
        }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { return files; }
        @Override public File getDatabasePath(String name) { return new File(databases, name); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(), factory, handler);
        }
        @Override public boolean deleteDatabase(String name) { return SQLiteDatabase.deleteDatabase(getDatabasePath(name)); }
        @Override public File getExternalFilesDir(String type) { return imports; }
        @Override public File[] getExternalFilesDirs(String type) { return new File[]{imports}; }
    }
    static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }
}
