package com.sh02l.reader;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

import com.sh02l.reader.core.DocumentLocation;

import java.io.File;
import java.lang.reflect.Constructor;

@SuppressWarnings("deprecation")
public class M25DataMigrationTest extends InstrumentationTestCase {
    public void testV2LibraryPreservesTxtLocations() throws Exception {
        final File root =
                new File(
                        getInstrumentation().getTargetContext().getCacheDir(),
                        "m25-migration-" + System.nanoTime());
        root.mkdirs();
        Context context =
                new ContextWrapper(getInstrumentation().getTargetContext()) {
                    public Context getApplicationContext() {
                        return this;
                    }

                    public File getFilesDir() {
                        File f = new File(root, "files");
                        f.mkdirs();
                        return f;
                    }

                    public File getDatabasePath(String name) {
                        return new File(root, name);
                    }

                    public SQLiteDatabase openOrCreateDatabase(
                            String name, int mode, SQLiteDatabase.CursorFactory factory) {
                        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
                    }

                    public SQLiteDatabase openOrCreateDatabase(
                            String name,
                            int mode,
                            SQLiteDatabase.CursorFactory factory,
                            android.database.DatabaseErrorHandler handler) {
                        return SQLiteDatabase.openOrCreateDatabase(
                                getDatabasePath(name).getPath(), factory, handler);
                    }
                };
        String id = new String(new char[64]).replace('\0', 'a');
        SQLiteDatabase old = context.openOrCreateDatabase("library.db", 0, null);
        old.execSQL(
                "CREATE TABLE books(id TEXT PRIMARY KEY,title TEXT NOT NULL,charset TEXT NOT"
                    + " NULL,length INTEGER NOT NULL,source_bytes INTEGER NOT NULL,offset INTEGER"
                    + " NOT NULL DEFAULT 0,locator_version INTEGER NOT NULL DEFAULT 1,opened"
                    + " INTEGER NOT NULL DEFAULT 0)");
        old.execSQL(
                "CREATE TABLE bookmarks(book_id TEXT NOT NULL,offset INTEGER NOT NULL,label TEXT"
                        + " NOT NULL,PRIMARY KEY(book_id,offset))");
        old.execSQL(
                "INSERT INTO books VALUES(?, 'kept','UTF-8',1000,3000,42,1,123)",
                new Object[] {id});
        old.execSQL("INSERT INTO bookmarks VALUES(?,7,'kept bookmark')", new Object[] {id});
        old.setVersion(2);
        old.close();
        Constructor<BookRepository> constructor =
                BookRepository.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        BookRepository repository = constructor.newInstance(context);
        try {
            assertEquals(3, repository.getReadableDatabase().getVersion());
            assertEquals(DocumentLocation.txt(42), repository.get(id).location);
            assertEquals("kept bookmark", repository.bookmarkEntries(id).get(0).title);
            assertEquals(DocumentLocation.txt(7), repository.bookmarkEntries(id).get(0).location);
            assertEquals(id, repository.get(id).id);
        } finally {
            repository.close();
        }
    }

    public void testShortProviderReadsStillImportEpubAndAlwaysClose() throws Exception {
        LibraryManagementTest.TestContext isolated =
                new LibraryManagementTest.TestContext(getInstrumentation().getTargetContext());
        BookRepository repository = LibraryManagementTest.open(isolated);
        final boolean[] closed = {false};
        try {
            File book = EpubFixtures.create(isolated.root);
            java.io.InputStream shortReads =
                    new java.io.FilterInputStream(new java.io.FileInputStream(book)) {
                        public int available() {
                            return 0;
                        }

                        public int read(byte[] bytes, int off, int count)
                                throws java.io.IOException {
                            return super.read(bytes, off, Math.min(1, count));
                        }

                        public void close() throws java.io.IOException {
                            closed[0] = true;
                            super.close();
                        }
                    };
            assertEquals("epub", repository.importBook(shortReads, "partial.epub", null).format);
            assertTrue(closed[0]);
            closed[0] = false;
            java.io.InputStream failed =
                    new java.io.InputStream() {
                        public int read() throws java.io.IOException {
                            throw new java.io.IOException("provider read failed");
                        }

                        public void close() {
                            closed[0] = true;
                        }
                    };
            try {
                repository.importBook(failed, "failed.epub", null);
                fail("Read failure ignored");
            } catch (java.io.IOException expected) {
            }
            assertTrue(closed[0]);
        } finally {
            repository.close();
            LibraryManagementTest.remove(isolated.root);
        }
    }
}
