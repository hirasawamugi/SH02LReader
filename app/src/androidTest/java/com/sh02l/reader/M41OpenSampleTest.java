package com.sh02l.reader;

import android.os.SystemClock;
import android.view.KeyEvent;

import com.sh02l.reader.epub.EpubDocumentSession;

import java.io.*;

/** A small opening sample separates original verification, metadata and renderer startup. */
public final class M41OpenSampleTest extends NativeTestBase {
    public void testReopeningStillChecksOriginalIntegrity() throws Exception {
        File source = EpubFixtures.illustrated(activity.getCacheDir(), false);
        BookRepository repository = BookRepository.obtain(activity);
        BookRepository.Book book =
                repository.importBook(new FileInputStream(source), source.getName(), null);
        DocumentEngineRegistry engines = new DocumentEngineRegistry();
        try {
            engines.open(repository, book).close();
            File original = repository.original(book.id);
            try (RandomAccessFile file = new RandomAccessFile(original, "rw")) {
                file.seek(file.length() - 1);
                int last = file.read();
                file.seek(file.length() - 1);
                file.write(last ^ 1);
            }
            try {
                engines.open(repository, book).close();
                fail("Retained metadata bypassed original verification");
            } catch (IOException expected) {
            }
        } finally {
            engines.clearRetainedMetadata();
            repository.deleteBook(book.id);
        }
    }

    public void testRepeatedEpubOpen() throws Exception {
        File source = new File(activity.getExternalFilesDir(null), "m41-user.epub");
        if (!source.isFile()) source = EpubFixtures.illustrated(activity.getCacheDir(), false);
        BookRepository repository = BookRepository.obtain(activity);
        long importing = SystemClock.elapsedRealtime();
        BookRepository.Book book =
                repository.importBook(new FileInputStream(source), source.getName(), null);
        long imported = SystemClock.elapsedRealtime();
        File report = new File(activity.getExternalFilesDir(null), "m41-open-sample.txt");
        try (Writer output = new OutputStreamWriter(new FileOutputStream(report), "UTF-8")) {
            output.write("bytes=" + source.length() + " importMs=" + (imported - importing) + "\n");
            output.flush();
            for (int i = 0; i < 3; i++) {
                long start = SystemClock.elapsedRealtime();
                File original = repository.original(book.id);
                long verified = SystemClock.elapsedRealtime();
                EpubDocumentSession metadata = new EpubDocumentSession(original);
                long parsed = SystemClock.elapsedRealtime();
                metadata.close();
                long opening = SystemClock.elapsedRealtime();
                ui(() -> activity.open(book));
                await(
                        () -> {
                            final boolean[] exists = {false};
                            ui(() -> exists[0] = activity.reading() != null);
                            return exists[0];
                        },
                        120000);
                long surface = SystemClock.elapsedRealtime();
                await(this::ready, 30000);
                long displayed = SystemClock.elapsedRealtime();
                output.write(
                        "run="
                                + i
                                + " verifyMs="
                                + (verified - start)
                                + " metadataMs="
                                + (parsed - verified)
                                + " sessionMs="
                                + (surface - opening)
                                + " renderMs="
                                + (displayed - surface)
                                + " totalMs="
                                + (displayed - opening)
                                + "\n");
                output.flush();
                press(KeyEvent.KEYCODE_F4);
                awaitLibrary();
            }
        }
    }
}
