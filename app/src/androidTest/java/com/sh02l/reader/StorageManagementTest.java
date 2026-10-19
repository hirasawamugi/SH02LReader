package com.sh02l.reader;

import android.test.InstrumentationTestCase;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public final class StorageManagementTest extends InstrumentationTestCase {
    private LibraryManagementTest.TestContext context;
    private BookRepository repository;
    private StorageManager storage;
    @Override protected void setUp() throws Exception {
        super.setUp();
        context = new LibraryManagementTest.TestContext(getInstrumentation().getTargetContext());
        repository = LibraryManagementTest.open(context);
        storage = new StorageManager(context, repository);
    }
    @Override protected void tearDown() throws Exception {
        repository.close();
        LibraryManagementTest.remove(context.root);
        super.tearDown();
    }
    private static void write(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(value.getBytes("UTF-8")); }
    }
    public void testImportCleanupRejectsEscapeBeforeDeletingSelectedFiles() throws Exception {
        File owned = new File(context.imports, "chosen.txt"), outside = new File(context.root, "keep.txt");
        write(owned, "owned"); write(outside, "outside");
        File nested = new File(context.imports, "folder"); assertTrue(nested.mkdir());
        write(new File(nested, "nested.txt"), "nested");
        File link = new File(context.imports, "outside-link.txt");
        android.system.Os.symlink(outside.getPath(), link.getPath());
        List<File> listed = storage.listImportFiles();
        assertEquals(1, listed.size());
        assertEquals(owned.getCanonicalFile(), listed.get(0).getCanonicalFile());
        try { storage.deleteImportFiles(Collections.singletonList(link)); fail("Symbolic link cleanup accepted"); }
        catch (IOException expected) { }
        try { storage.deleteImportFiles(Arrays.asList(owned, outside)); fail("Escaping cleanup accepted"); }
        catch (IOException expected) { }
        assertTrue(owned.isFile()); assertTrue(outside.isFile());
        assertEquals(5L, storage.deleteImportFiles(Collections.singletonList(owned)));
        assertFalse(owned.exists()); assertTrue(outside.isFile());
    }
    public void testCacheCleanupPreservesActiveBookAndCanRebuildOthers() throws Exception {
        BookRepository.Book active = LibraryManagementTest.importText(repository, "active book");
        BookRepository.Book other = LibraryManagementTest.importText(repository, "other book content");
        repository.saveProgress(other.id, 5);
        repository.addBookmark(other.id, 3, "keep");
        StorageManager.Snapshot before = storage.scan();
        assertTrue(before.originals > 0); assertTrue(before.caches > 0);
        assertTrue(storage.clearCaches(active.id) > 0);
        assertTrue(new File(repository.directory(active.id), "text.u16").isFile());
        assertFalse(new File(repository.directory(other.id), "text.u16").exists());
        assertTrue(repository.original(other.id).isFile());
        repository.ensureCache(other.id, null);
        assertEquals(5L, repository.get(other.id).offset);
        assertEquals(3L, repository.bookmarks(other.id).get(0).offset);
        try (FileOutputStream out = new FileOutputStream(repository.original(other.id), true)) { out.write(1); }
        try { storage.clearCaches(active.id); fail("Cleanup accepted a damaged original"); }
        catch (IOException expected) { }
        assertTrue("A damaged original must keep its last usable text", new File(repository.directory(other.id), "text.u16").isFile());
    }
}
