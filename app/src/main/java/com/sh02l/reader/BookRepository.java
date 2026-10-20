package com.sh02l.reader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.sh02l.reader.core.*;
import com.sh02l.reader.core.text.TextImporter;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Private originals outlive cache changes; catalogue commits decide recovery. */
final class BookRepository {
    private static final String ORDER = "opened DESC, title COLLATE NOCASE, id";
    private static final Pattern OPERATION =
            Pattern.compile("\\.(delete|rebuild|previous)-([0-9a-f]{64})-([0-9a-f-]{36})");
    static final String[] CACHE_NAMES = {
        "text.u16", "index.bin", "cache.sha256", ".cache.sha256.tmp"
    };
    private static final long SPACE_MARGIN = 8L * 1024 * 1024;
    private static BookRepository instance;

    static synchronized BookRepository obtain(Context context) {
        if (instance == null) instance = new BookRepository(context.getApplicationContext());
        return instance;
    }

    static final class Book {
        final String id, title, charset, format;
        final DocumentLocation location;
        final double progression;
        final long length, sourceBytes, offset;

        Book(String id, String title, String charset, long length, long sourceBytes, long offset) {
            this.id = id;
            this.title = title;
            this.charset = charset;
            this.length = length;
            this.sourceBytes = sourceBytes;
            this.offset = offset;
            this.format = "txt";
            this.location = DocumentLocation.txt(offset);
            this.progression = length == 0 ? 0 : (double) offset / length;
        }

        Book(
                String id,
                String title,
                String charset,
                long length,
                long sourceBytes,
                long offset,
                String format,
                String payload,
                int version,
                double progression) {
            this.id = id;
            this.title = title;
            this.charset = charset;
            this.length = length;
            this.sourceBytes = sourceBytes;
            this.offset = offset;
            this.format = format;
            this.location =
                    "txt".equals(format)
                            ? DocumentLocation.txt(offset)
                            : payload.isEmpty()
                                    ? null
                                    : new DocumentLocation(format, version, payload);
            this.progression =
                    "txt".equals(format) ? length == 0 ? 0 : (double) offset / length : progression;
        }
    }

    private final LibraryDatabase database;
    private final BookFileStore files;
    private final File books;
    private final CoverStore covers;

    private BookRepository(Context context) {
        database = new LibraryDatabase(context);
        files = new BookFileStore(context);
        books = files.root;
        covers = new CoverStore(context);
    }

    SQLiteDatabase getReadableDatabase() {
        return database.getReadableDatabase();
    }

    SQLiteDatabase getWritableDatabase() {
        return database.getWritableDatabase();
    }

    void close() {
        database.close();
    }

    File directory(String id) {
        return files.directory(id);
    }

    List<Book> list() {
        return queryBooks(null);
    }

    List<Book> listPage(int offset, int limit) {
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("library page");
        return queryBooks(offset + "," + limit);
    }

    private List<Book> queryBooks(String limit) {
        List<Book> list = new ArrayList<>();
        try (Cursor cursor =
                getReadableDatabase().query("books", null, null, null, null, null, ORDER, limit)) {
            while (cursor.moveToNext()) list.add(book(cursor));
        }
        return list;
    }

    int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM books", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    /** Zero-based page, with the first page as the fallback for a removed book. */
    int pageOf(String id, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("page size");
        SQLiteDatabase db = getReadableDatabase();
        String opened, title;
        try (Cursor cursor =
                db.query(
                        "books",
                        new String[] {"opened", "title"},
                        "id=?",
                        new String[] {id},
                        null,
                        null,
                        null)) {
            if (!cursor.moveToFirst()) return 0;
            opened = cursor.getString(0);
            title = cursor.getString(1);
        }
        String sql =
                "SELECT COUNT(*) FROM books WHERE opened>? OR (opened=? AND (title COLLATE NOCASE<?"
                    + " COLLATE NOCASE OR (title COLLATE NOCASE=? COLLATE NOCASE AND id<?)))";
        try (Cursor cursor = db.rawQuery(sql, new String[] {opened, opened, title, title, id})) {
            cursor.moveToFirst();
            return cursor.getInt(0) / pageSize;
        }
    }

    Book get(String id) {
        try (Cursor cursor =
                getReadableDatabase()
                        .query("books", null, "id=?", new String[] {id}, null, null, null)) {
            return cursor.moveToFirst() ? book(cursor) : null;
        }
    }

    private static Book book(Cursor cursor) {
        return new Book(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("charset")),
                cursor.getLong(cursor.getColumnIndexOrThrow("length")),
                cursor.getLong(cursor.getColumnIndexOrThrow("source_bytes")),
                cursor.getLong(cursor.getColumnIndexOrThrow("offset")),
                cursor.getString(cursor.getColumnIndexOrThrow("format")),
                cursor.getString(cursor.getColumnIndexOrThrow("locator_payload")),
                cursor.getInt(cursor.getColumnIndexOrThrow("locator_version")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("progression")));
    }

    synchronized void renameBook(String id, String title) {
        ContentValues values = new ContentValues();
        values.put("title", label(title, 160));
        getWritableDatabase().update("books", values, "id=?", new String[] {id});
    }

    synchronized void cleanupStaging() throws IOException {
        ensureLibrary();
        File[] entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect library");
        // A retained delete directory is durable evidence of an explicit user deletion.
        for (File entry : entries) {
            Matcher operation = OPERATION.matcher(entry.getName());
            if (operation.matches() && operation.group(1).equals("delete"))
                recoverDeletion(entry, operation.group(2));
        }
        entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect library");
        for (File entry : entries) {
            Matcher operation = OPERATION.matcher(entry.getName());
            if (operation.matches() && operation.group(1).equals("previous"))
                recoverPrevious(entry, operation.group(2), operation.group(3));
        }
        entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect library");
        for (File entry : entries) {
            if (entry.getName().startsWith(".import-")) removeOwned(entry);
            Matcher operation = OPERATION.matcher(entry.getName());
            if (operation.matches() && operation.group(1).equals("rebuild")) {
                String id = operation.group(2);
                // Never discard a possible sole surviving original.
                if (verifiedOriginal(directory(id), id)
                        || !new File(entry, "original.bin").exists()) removeOwned(entry);
            }
        }
    }

    private void ensureLibrary() throws IOException {
        files.ensure();
    }

    synchronized Book importBook(InputStream source, String name, TextImporter.Observer observer)
            throws IOException {
        try {
            ensureLibrary();
        } catch (IOException error) {
            source.close();
            throw error;
        }
        File staging = new File(books, ".import-" + UUID.randomUUID());
        try {
            DocumentImportRegistry.Prepared result =
                    DocumentImportRegistry.prepare(source, name, staging, observer);
            TextImporter.check(observer);
            Book existing = get(result.id);
            if (existing != null) {
                original(result.id, observer);
                return existing;
            }
            File destination = directory(result.id);
            if (destination.exists()) {
                if (!verifiedOriginal(destination, result.id))
                    throw new IOException("Conflicting library directory");
            } else {
                DurableFiles.syncDirectory(staging);
                if (!staging.renameTo(destination))
                    throw new IOException("Cannot publish imported book");
                DurableFiles.syncDirectory(books);
            }
            ContentValues values = new ContentValues();
            values.put("id", result.id);
            values.put("title", result.title);
            values.put("charset", result.charset);
            values.put("format", result.format);
            values.put("length", result.length);
            values.put("source_bytes", result.sourceBytes);
            values.put("locator_payload", result.location.payload);
            values.put("locator_version", result.location.version);
            if (observer != null) observer.beforeWrite(65536);
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                db.insertOrThrow("books", null, values);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            if (result.cover != null)
                try {
                    covers.importCover(
                            result.id,
                            new ByteArrayInputStream(result.cover),
                            "EPUB cover",
                            observer);
                } catch (IOException ignored) {
                }
            return get(result.id);
        } finally {
            removeOwned(staging);
        }
    }

    File original(String id) throws IOException {
        return original(id, null);
    }

    private File original(String id, TextImporter.Observer observer) throws IOException {
        if (get(id) == null) throw new IOException("Book unavailable");
        File original = originalIn(directory(id));
        if (!id.equals(CoverStore.hash(original, observer)))
            throw new IOException("Damaged private original");
        return original;
    }

    private File originalIn(File folder) throws IOException {
        return files.originalIn(folder);
    }

    private boolean verifiedOriginal(File folder, String id) throws IOException {
        if (!folder.exists()) return false;
        try {
            return id.equals(CoverStore.hash(originalIn(folder)));
        } catch (InterruptedIOException error) {
            throw error;
        } catch (IOException error) {
            return false;
        }
    }

    synchronized TextImporter.Result ensureCache(String id, TextImporter.Observer observer)
            throws IOException {
        Book book = get(id);
        if (book == null || !book.format.equals("txt"))
            throw new IOException("TXT cache unavailable");
        try {
            return validCache(directory(id), book, observer);
        } catch (InterruptedIOException error) {
            throw error;
        } catch (IOException damaged) {
            rebuildInternal(book, book.charset, observer, false);
            return validCache(directory(id), get(id), observer);
        }
    }

    synchronized Book rebuild(String id, String charset, TextImporter.Observer observer)
            throws IOException {
        Book book = get(id);
        if (book == null) throw new IOException("Book unavailable");
        return rebuildInternal(book, charset, observer, true);
    }

    private Book rebuildInternal(
            Book old, String charset, TextImporter.Observer observer, boolean remap)
            throws IOException {
        File original = original(old.id, observer);
        String requested;
        try {
            requested = charset == null ? null : Charset.forName(charset).name();
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Unsupported text encoding", invalid);
        }
        long bytes = original.length();
        if (bytes > (Long.MAX_VALUE - SPACE_MARGIN - 6L * 1024 * 1024) / 3
                || books.getUsableSpace() < bytes * 3 + SPACE_MARGIN + 6L * 1024 * 1024)
            throw new IOException("Not enough space to rebuild text");
        String token = UUID.randomUUID().toString();
        File staging = new File(books, ".rebuild-" + old.id + "-" + token);
        File previous = new File(books, ".previous-" + old.id + "-" + token);
        File destination = directory(old.id);
        boolean swapped = false;
        TextImporter.Observer guarded = guarded(observer);
        try {
            TextImporter.Result result =
                    TextImporter.prepare(
                            new FileInputStream(original), staging, requested, guarded);
            if (!old.id.equals(result.id))
                throw new IOException("Private original changed during rebuild");
            writeCacheHash(staging, guarded);
            TextImporter.check(guarded);
            DurableFiles.syncDirectory(staging);
            if (!destination.renameTo(previous))
                throw new IOException("Cannot preserve previous text");
            swapped = true;
            DurableFiles.syncDirectory(books);
            if (!staging.renameTo(destination))
                throw new IOException("Cannot publish rebuilt text");
            DurableFiles.syncDirectory(books);
            boolean changed = remap && !Charset.forName(old.charset).name().equals(result.charset);
            guarded.beforeWrite(65536);
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put("charset", result.charset);
                values.put("length", result.length);
                values.put("source_bytes", result.sourceBytes);
                if (changed) {
                    values.put("offset", remap(old.offset, old.length, result.length));
                    values.put(
                            "locator_payload",
                            Long.toString(remap(old.offset, old.length, result.length)));
                }
                if (db.update("books", values, "id=?", new String[] {old.id}) != 1)
                    throw new IOException("Book removed during rebuild");
                if (changed) {
                    List<TextImporter.Chapter> bookmarks = bookmarks(old.id);
                    db.delete("bookmarks", "book_id=?", new String[] {old.id});
                    for (TextImporter.Chapter bookmark : bookmarks) {
                        long offset = remap(bookmark.offset, old.length, result.length);
                        // An encoding can collapse adjacent positions; retain both labels at that
                        // anchor.
                        String label = bookmark.title;
                        try (Cursor cursor =
                                db.query(
                                        "bookmarks",
                                        new String[] {"label"},
                                        "book_id=? AND offset=?",
                                        new String[] {old.id, Long.toString(offset)},
                                        null,
                                        null,
                                        null)) {
                            if (cursor.moveToFirst()) label = cursor.getString(0) + " / " + label;
                        }
                        ContentValues mark = new ContentValues();
                        mark.put("book_id", old.id);
                        mark.put("offset", offset);
                        mark.put("label", label);
                        mark.put("locator_payload", Long.toString(offset));
                        if (db.insertWithOnConflict(
                                        "bookmarks", null, mark, SQLiteDatabase.CONFLICT_REPLACE)
                                < 0) throw new IOException("Cannot remap bookmark");
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            removeOwned(previous);
            DurableFiles.syncDirectory(books);
            return get(old.id);
        } catch (IOException | RuntimeException error) {
            if (swapped)
                try {
                    recoverPrevious(previous, old.id, token);
                } catch (IOException | RuntimeException recovery) {
                    error.addSuppressed(recovery);
                }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot rebuild private text", error);
        } finally {
            if (staging.exists() && !previous.exists() && verifiedOriginal(destination, old.id))
                removeOwned(staging);
        }
    }

    private static long remap(long offset, long oldLength, long newLength) {
        if (oldLength <= 0 || newLength <= 0) return 0;
        return Math.max(
                0,
                Math.min(
                        newLength,
                        Math.round(
                                (double) Math.max(0, Math.min(oldLength, offset))
                                        / oldLength
                                        * newLength)));
    }

    private TextImporter.Observer guarded(TextImporter.Observer observer) {
        return new TextImporter.Observer() {
            public void progress(String phase, long bytes) {
                if (observer != null) observer.progress(phase, bytes);
            }

            public boolean cancelled() {
                return observer != null && observer.cancelled();
            }

            public void beforeWrite(long bytes) throws IOException {
                TextImporter.check(observer);
                if (bytes < 0 || books.getUsableSpace() < SPACE_MARGIN + bytes)
                    throw new IOException("Not enough private storage");
                if (observer != null) observer.beforeWrite(bytes);
            }
        };
    }

    private static boolean matches(TextImporter.Result result, Book book) {
        return book != null
                && result.id.equals(book.id)
                && result.charset.equalsIgnoreCase(book.charset)
                && result.length == book.length
                && result.sourceBytes == book.sourceBytes;
    }

    private TextImporter.Result validCache(File folder, Book book) throws IOException {
        return validCache(folder, book, null);
    }

    private TextImporter.Result validCache(File folder, Book book, TextImporter.Observer observer)
            throws IOException {
        checkOwnedDirectory(folder);
        for (String name : new String[] {"text.u16", "index.bin"})
            if (!regularChild(folder, new File(folder, name)))
                throw new IOException("Text cache unavailable");
        TextImporter.Result result = TextImporter.readIndex(folder);
        if (!matches(result, book)) throw new IOException("Text cache metadata mismatch");
        File fingerprint = new File(folder, "cache.sha256");
        if (!Files.exists(fingerprint.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            // M1.5 caches predate fingerprints. Keep them readable even when storage is full.
            if (!result.id.equals(CoverStore.hash(originalIn(folder), observer)))
                throw new IOException("Damaged private original");
            if (folder.getUsableSpace() >= SPACE_MARGIN + 4096) {
                try {
                    writeCacheHash(folder, observer);
                } catch (InterruptedIOException cancelled) {
                    throw cancelled;
                } catch (IOException unavailable) {
                    /* Fingerprinting is optional for a structurally valid legacy cache. */
                }
            }
            return result;
        }
        if (!regularChild(folder, fingerprint)) throw new IOException("Unsafe cache fingerprint");
        if (fingerprint.length() != 64) throw new IOException("Invalid cache fingerprint");
        byte[] bytes = new byte[64];
        try (DataInputStream in = new DataInputStream(new FileInputStream(fingerprint))) {
            in.readFully(bytes);
        }
        String expected = new String(bytes, "US-ASCII");
        if (!expected.matches("[0-9a-f]{64}")
                || !expected.equals(CoverStore.hash(new File(folder, "text.u16"), observer)))
            throw new IOException("Damaged text cache");
        return result;
    }

    private static void writeCacheHash(File folder, TextImporter.Observer observer)
            throws IOException {
        TxtCacheFingerprint.write(folder, observer);
    }

    private void recoverPrevious(File previous, String id, String token) throws IOException {
        if (!previous.exists()) return;
        checkOwnedDirectory(previous);
        Book book = get(id);
        if (book == null)
            return; // No deletion journal: ownership of a surviving original is uncertain.
        File destination = directory(id), staging = new File(books, ".rebuild-" + id + "-" + token);
        try {
            validCache(destination, book);
            if (verifiedOriginal(destination, id)) {
                removeOwned(previous);
                DurableFiles.syncDirectory(books);
                return;
            }
        } catch (InterruptedIOException error) {
            throw error;
        } catch (IOException unavailable) {
            /* The database still describes the previous generation. */
        }
        if (!verifiedOriginal(previous, id)) {
            if (verifiedOriginal(destination, id))
                return; // A partial old-cache cleanup must not block repair of the surviving
                        // original.
            throw new IOException("Cannot verify previous private original");
        }
        if (destination.exists()) {
            if (staging.exists()) throw new IOException("Conflicting rebuild recovery");
            if (!destination.renameTo(staging))
                throw new IOException("Cannot retain uncommitted cache");
            DurableFiles.syncDirectory(books);
        }
        if (!previous.renameTo(destination))
            throw new IOException("Cannot restore private original");
        DurableFiles.syncDirectory(books);
        removeOwned(staging);
    }

    synchronized void deleteBook(String id) throws IOException {
        if (get(id) == null) return;
        ensureLibrary();
        File source = directory(id),
                journal = new File(books, ".delete-" + id + "-" + UUID.randomUUID());
        if (source.exists()) {
            checkOwnedDirectory(source);
            if (!source.renameTo(journal)) throw new IOException("Cannot stage book deletion");
        } else if (!journal.mkdir()) throw new IOException("Cannot stage book deletion");
        try {
            DurableFiles.syncDirectory(books);
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete("bookmarks", "book_id=?", new String[] {id});
                db.delete("books", "id=?", new String[] {id});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (IOException | RuntimeException error) {
            try {
                recoverDeletion(journal, id);
            } catch (IOException | RuntimeException recovery) {
                error.addSuppressed(recovery);
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot commit book deletion", error);
        }
        recoverDeletion(journal, id);
    }

    private void recoverDeletion(File journal, String id) throws IOException {
        if (!journal.exists()) return;
        checkOwnedDirectory(journal);
        File destination = directory(id);
        if (get(id) != null) {
            if (!destination.exists()) {
                if (!journal.renameTo(destination))
                    throw new IOException("Cannot restore deleted book");
                DurableFiles.syncDirectory(books);
            }
            return;
        }
        covers.remove(id);
        File[] entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect deleted book files");
        for (File entry : entries) {
            Matcher operation = OPERATION.matcher(entry.getName());
            if (!entry.equals(journal)
                    && (entry.getName().equals(id)
                            || (operation.matches() && operation.group(2).equals(id))))
                removeOwned(entry);
        }
        removeOwned(journal);
        DurableFiles.syncDirectory(books);
    }

    private void checkOwnedDirectory(File folder) throws IOException {
        files.check(folder);
    }

    static boolean regularChild(File folder, File file) throws IOException {
        return BookFileStore.regularChild(folder, file);
    }

    private void removeOwned(File folder) throws IOException {
        files.remove(folder);
    }

    private static String title(String name) {
        if (name == null || name.trim().isEmpty()) return "未命名 TXT";
        String value = name.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".txt"))
            value = value.substring(0, value.length() - 4);
        return value.isEmpty() ? "未命名 TXT" : value.substring(0, Math.min(160, value.length()));
    }

    private static String label(String text, int limit) {
        String value = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Name is empty");
        return value.substring(0, Math.min(limit, value.length()));
    }

    synchronized void saveProgress(String id, long offset) {
        Book b = get(id);
        if (b != null)
            saveProgress(
                    id,
                    new ReadingPosition(
                            DocumentLocation.txt(offset),
                            b.length == 0 ? 0 : (double) offset / b.length));
    }

    synchronized void saveProgress(String id, ReadingPosition position) {
        Book b = get(id);
        if (b == null || !b.format.equals(position.location.format)) return;
        ContentValues values = new ContentValues();
        values.put(
                "offset",
                b.format.equals("txt")
                        ? position.location.textOffset()
                        : Math.round(position.progression * b.length));
        values.put("locator_payload", position.location.payload);
        values.put("locator_version", position.location.version);
        values.put("progression", position.progression);
        values.put("opened", System.currentTimeMillis());
        getWritableDatabase().update("books", values, "id=?", new String[] {id});
    }

    synchronized boolean saveProgressIf(
            String id, long offset, java.util.function.BooleanSupplier current) {
        if (!current.getAsBoolean()) return false;
        saveProgress(id, offset);
        return true;
    }

    synchronized boolean saveProgressIf(
            String id, ReadingPosition position, java.util.function.BooleanSupplier current) {
        if (!current.getAsBoolean()) return false;
        saveProgress(id, position);
        return true;
    }

    synchronized void addBookmark(String id, long offset, String label) {
        addBookmark(id, DocumentLocation.txt(offset), label);
    }

    synchronized void addBookmark(String id, DocumentLocation location, String label) {
        ContentValues values = new ContentValues();
        values.put("book_id", id);
        values.put("offset", location.format.equals("txt") ? location.textOffset() : 0);
        values.put("label", label);
        values.put("format", location.format);
        values.put("locator_version", location.version);
        values.put("locator_payload", location.payload);
        getWritableDatabase()
                .insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String[] markerArgs(String id, DocumentLocation location) {
        return new String[] {
            id, location.format, Integer.toString(location.version), location.payload
        };
    }

    private static final String MARKER_WHERE =
            "book_id=? AND format=? AND locator_version=? AND locator_payload=?";

    synchronized void removeBookmark(String id, long offset) {
        removeBookmark(id, DocumentLocation.txt(offset));
    }

    synchronized void removeBookmark(String id, DocumentLocation location) {
        getWritableDatabase().delete("bookmarks", MARKER_WHERE, markerArgs(id, location));
    }

    synchronized void renameBookmark(String id, long offset, String label) {
        renameBookmark(id, DocumentLocation.txt(offset), label);
    }

    synchronized void renameBookmark(String id, DocumentLocation location, String label) {
        ContentValues values = new ContentValues();
        values.put("label", label(label, 160));
        getWritableDatabase().update("bookmarks", values, MARKER_WHERE, markerArgs(id, location));
    }

    List<ContentEntry> bookmarkEntries(String id) {
        List<ContentEntry> result = new ArrayList<>();
        try (Cursor cursor =
                getReadableDatabase()
                        .query(
                                "bookmarks",
                                null,
                                "book_id=?",
                                new String[] {id},
                                null,
                                null,
                                "offset")) {
            while (cursor.moveToNext())
                result.add(
                        new ContentEntry(
                                cursor.getString(cursor.getColumnIndexOrThrow("label")),
                                new DocumentLocation(
                                        cursor.getString(cursor.getColumnIndexOrThrow("format")),
                                        cursor.getInt(
                                                cursor.getColumnIndexOrThrow("locator_version")),
                                        cursor.getString(
                                                cursor.getColumnIndexOrThrow("locator_payload")))));
        }
        return result;
    }

    List<TextImporter.Chapter> bookmarks(String id) {
        List<TextImporter.Chapter> result = new ArrayList<>();
        for (ContentEntry entry : bookmarkEntries(id))
            if (entry.location.format.equals("txt"))
                result.add(new TextImporter.Chapter(entry.title, entry.location.textOffset()));
        return result;
    }
}
