package com.sh02l.reader;

import android.graphics.*;
import android.test.InstrumentationTestCase;

import com.sh02l.reader.core.text.TextImporter;

import java.io.*;
import java.util.*;

public final class M4StorageTest extends InstrumentationTestCase {
    private LibraryManagementTest.TestContext context;
    private BookRepository repository;

    protected void setUp() throws Exception {
        super.setUp();
        context = new LibraryManagementTest.TestContext(getInstrumentation().getTargetContext());
        repository = LibraryManagementTest.open(context);
    }

    protected void tearDown() throws Exception {
        repository.close();
        LibraryManagementTest.remove(context.root);
        super.tearDown();
    }

    public void testDuplicateSkipsDecodeAndKeepsProgress() throws Exception {
        byte[] bytes = "第一章 重复导入\n保留名字、进度和书签。".getBytes("UTF-8");
        BookRepository.Book first =
                repository.importBook(new ByteArrayInputStream(bytes), "first.txt", null);
        repository.renameBook(first.id, "我的名字");
        repository.saveProgress(first.id, 8);
        repository.addBookmark(first.id, 5, "记号");
        Set<String> phases = new HashSet<>();
        BookRepository.Book again =
                repository.importBook(
                        new ByteArrayInputStream(bytes),
                        "renamed.txt",
                        new TextImporter.Observer() {
                            public boolean cancelled() {
                                return false;
                            }

                            public void progress(String phase, long bytes) {
                                phases.add(phase);
                            }
                        });
        assertEquals(first.id, again.id);
        assertEquals("我的名字", again.title);
        assertEquals(8, again.offset);
        assertFalse(phases.contains("decode"));
        assertFalse(phases.contains("encoding"));
        assertEquals(1, repository.bookmarkEntries(first.id).size());
        for (File f : repository.directory(first.id).getParentFile().listFiles())
            assertFalse(f.getName().startsWith(".import-"));
    }

    public void testSelectedCacheCleanupProtectsActiveAndDamagedOriginal() throws Exception {
        BookRepository.Book active = LibraryManagementTest.importText(repository, "active book");
        BookRepository.Book good = LibraryManagementTest.importText(repository, "good book");
        BookRepository.Book broken = LibraryManagementTest.importText(repository, "broken book");
        try (FileOutputStream out =
                new FileOutputStream(new File(repository.directory(broken.id), "original.bin"))) {
            out.write(0);
        }
        StorageManager.CleanupResult r =
                new StorageManager(context, repository)
                        .clearCaches(Arrays.asList(active.id, good.id, broken.id), active.id);
        assertEquals(1, r.cleared);
        assertEquals(1, r.skipped);
        assertEquals(1, r.failed);
        assertTrue(r.bytes > 0);
        assertTrue(new File(repository.directory(active.id), "text.u16").exists());
        assertTrue(new File(repository.directory(broken.id), "text.u16").exists());
        assertFalse(new File(repository.directory(good.id), "text.u16").exists());
    }

    public void testBackgroundCommitCleanupAndCancelledReplacement() throws Exception {
        BackgroundStore store = new BackgroundStore(context);
        ThemeSpec original = store.load(2, 1);
        assertEquals(2, original.preset);
        Bitmap bitmap = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.CYAN);
        ThemeSpec saved = store.save(original, bitmap);
        assertTrue(store.file(saved.asset).isFile());
        assertEquals(saved.asset, store.load(0, 0).asset);
        File source = store.file(".source-cancelled");
        try (FileOutputStream out = new FileOutputStream(source)) {
            out.write(1);
        }
        ThemeSpec draft = saved.copy();
        draft.preset(4);
        draft.asset = "";
        store.cleanup(saved.asset);
        assertFalse(source.exists());
        assertEquals(2, store.load(0, 0).preset);
        assertTrue(store.file(saved.asset).isFile());
        ThemeSpec plain = store.save(draft, null);
        store.cleanup(plain.asset);
        assertFalse(store.file(saved.asset).exists());
        assertEquals(4, store.load(0, 0).preset);
        bitmap.recycle();
    }

    public void testLargeImageDecodeBudgetAndFit() throws Exception {
        File source = new File(context.files, "source.png");
        Bitmap wide = Bitmap.createBitmap(2400, 200, Bitmap.Config.ARGB_8888);
        wide.eraseColor(Color.RED);
        try (FileOutputStream out = new FileOutputStream(source)) {
            wide.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        wide.recycle();
        Bitmap fit = CoverDecoder.decode(source, 540, 960, true, 0);
        assertTrue(fit.getAllocationByteCount() <= 2 * 1024 * 1024);
        assertEquals(0, Color.alpha(fit.getPixel(10, 10)));
        assertEquals(Color.RED, fit.getPixel(270, 480));
        fit.recycle();
        Bitmap fill = CoverDecoder.decode(source, 540, 960, false, 0);
        assertEquals(Color.RED, fill.getPixel(10, 10));
        fill.recycle();
    }
}
