package com.sh02l.reader;

import android.test.InstrumentationTestCase;

import com.sh02l.reader.core.text.TextImporter;

import java.io.*;

@SuppressWarnings("deprecation")
public final class ImportCommitSafetyTest extends InstrumentationTestCase {
    public void testDuplicatePreservesOriginalAndRepairsCacheWhenOpened() throws Exception {
        byte[] source = ("duplicate fixture " + System.nanoTime()).getBytes("UTF-8");
        BookRepository repo = BookRepository.obtain(getInstrumentation().getTargetContext());
        BookRepository.Book book =
                repo.importBook(new ByteArrayInputStream(source), "duplicate.txt", null);
        File text = new File(repo.directory(book.id), "text.u16");
        String expected = CoverFixtures.hash(CoverFixtures.bytes(text));
        try (RandomAccessFile file = new RandomAccessFile(text, "rw")) {
            int original = file.read();
            file.seek(0);
            file.write(original ^ 1);
            file.getFD().sync();
        }
        // Duplicate recognition checks the immutable original. Cache validation/rebuild belongs to
        // opening.
        assertEquals(
                book.id,
                repo.importBook(new ByteArrayInputStream(source), "duplicate.txt", null).id);
        repo.ensureCache(book.id, null);
        assertEquals(expected, CoverFixtures.hash(CoverFixtures.bytes(text)));
        assertEquals(
                CoverFixtures.hash(source),
                CoverFixtures.hash(
                        CoverFixtures.bytes(new File(repo.directory(book.id), "original.bin"))));
    }

    public void testCoverWriteVetoKeepsPreviousRevision() throws Exception {
        CoverStore store = new CoverStore(getInstrumentation().getTargetContext());
        String id = CoverFixtures.id();
        byte[] image = CoverFixtures.png(80, 120);
        CoverStore.Record first =
                store.importCover(id, new ByteArrayInputStream(image), "old.png", null);
        TextImporter.Observer observer =
                new TextImporter.Observer() {
                    public void progress(String phase, long bytes) {}

                    public boolean cancelled() {
                        return false;
                    }

                    public void beforeWrite(long bytes) throws IOException {
                        throw new IOException("storage exhausted");
                    }
                };
        try {
            store.importCover(id, new ByteArrayInputStream(image), "new.png", observer);
            fail("Cover write veto ignored");
        } catch (IOException expected) {
        }
        assertEquals(first.revision, store.get(id).revision);
    }
}
