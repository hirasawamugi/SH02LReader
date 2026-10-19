package com.sh02l.reader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Private originals outlive cache changes; catalogue commits decide recovery. */
final class BookRepository extends SQLiteOpenHelper {
    private static final String ORDER = "opened DESC, title COLLATE NOCASE, id";
    private static final Pattern OPERATION = Pattern.compile("\\.(delete|rebuild|previous)-([0-9a-f]{64})-([0-9a-f-]{36})");
    static final String[] CACHE_NAMES = {"text.u16", "index.bin", "cache.sha256", ".cache.sha256.tmp"};
    private static final long SPACE_MARGIN = 8L * 1024 * 1024;
    private static BookRepository instance;
    static synchronized BookRepository obtain(Context context) {
        if (instance == null) instance = new BookRepository(context.getApplicationContext());
        return instance;
    }
    static final class Book {
        final String id, title, charset;
        final long length, sourceBytes, offset;
        Book(String id, String title, String charset, long length, long sourceBytes, long offset) {
            this.id = id; this.title = title; this.charset = charset; this.length = length;
            this.sourceBytes = sourceBytes; this.offset = offset;
        }
    }
    private final File books;
    private final CoverStore covers;
    private BookRepository(Context context) {
        super(context, "library.db", null, 2);
        books = new File(context.getFilesDir(), "books");
        covers = new CoverStore(context);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE books(id TEXT PRIMARY KEY,title TEXT NOT NULL,charset TEXT NOT NULL,"
                + "length INTEGER NOT NULL,source_bytes INTEGER NOT NULL,offset INTEGER NOT NULL DEFAULT 0,"
                + "locator_version INTEGER NOT NULL DEFAULT 1,opened INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE bookmarks(book_id TEXT NOT NULL,offset INTEGER NOT NULL,label TEXT NOT NULL,"
                + "PRIMARY KEY(book_id,offset))");
        createIndexes(db);
    }
    private static void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS books_library_order ON books(opened DESC,title COLLATE NOCASE,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) { createIndexes(db); return; }
        throw new IllegalStateException("No migration from " + oldVersion + " to " + newVersion);
    }
    File directory(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("book id");
        return new File(books, id);
    }
    List<Book> list() { return queryBooks(null); }
    List<Book> listPage(int offset, int limit) {
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("library page");
        return queryBooks(offset + "," + limit);
    }
    private List<Book> queryBooks(String limit) {
        List<Book> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("books", null, null, null, null, null, ORDER, limit)) {
            while (cursor.moveToNext()) list.add(book(cursor));
        }
        return list;
    }
    int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM books", null)) {
            cursor.moveToFirst(); return cursor.getInt(0);
        }
    }
    /** Zero-based page, with the first page as the fallback for a removed book. */
    int pageOf(String id, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("page size");
        SQLiteDatabase db = getReadableDatabase();
        String opened, title;
        try (Cursor cursor = db.query("books", new String[]{"opened", "title"}, "id=?", new String[]{id}, null, null, null)) {
            if (!cursor.moveToFirst()) return 0;
            opened = cursor.getString(0); title = cursor.getString(1);
        }
        String sql = "SELECT COUNT(*) FROM books WHERE opened>? OR (opened=? AND "
                + "(title COLLATE NOCASE<? COLLATE NOCASE OR (title COLLATE NOCASE=? COLLATE NOCASE AND id<?)))";
        try (Cursor cursor = db.rawQuery(sql, new String[]{opened, opened, title, title, id})) {
            cursor.moveToFirst(); return cursor.getInt(0) / pageSize;
        }
    }
    Book get(String id) {
        try (Cursor cursor = getReadableDatabase().query("books", null, "id=?", new String[]{id}, null, null, null)) {
            return cursor.moveToFirst() ? book(cursor) : null;
        }
    }
    private static Book book(Cursor cursor) {
        return new Book(cursor.getString(cursor.getColumnIndexOrThrow("id")), cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("charset")), cursor.getLong(cursor.getColumnIndexOrThrow("length")),
                cursor.getLong(cursor.getColumnIndexOrThrow("source_bytes")), cursor.getLong(cursor.getColumnIndexOrThrow("offset")));
    }
    synchronized void renameBook(String id, String title) {
        ContentValues values = new ContentValues(); values.put("title", label(title, 160));
        getWritableDatabase().update("books", values, "id=?", new String[]{id});
    }

    synchronized void cleanupStaging() throws IOException {
        ensureLibrary();
        File[] entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect library");
        // A retained delete directory is durable evidence of an explicit user deletion.
        for (File entry : entries) {
            Matcher operation = OPERATION.matcher(entry.getName());
            if (operation.matches() && operation.group(1).equals("delete")) recoverDeletion(entry, operation.group(2));
        }
        entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect library");
        for (File entry : entries) {
            Matcher operation = OPERATION.matcher(entry.getName());
            if (operation.matches() && operation.group(1).equals("previous")) recoverPrevious(entry, operation.group(2), operation.group(3));
        }
        entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect library");
        for (File entry : entries) {
            if (entry.getName().startsWith(".import-")) removeOwned(entry);
            Matcher operation = OPERATION.matcher(entry.getName());
            if (operation.matches() && operation.group(1).equals("rebuild")) {
                String id = operation.group(2);
                // Never discard a possible sole surviving original.
                if (verifiedOriginal(directory(id), id) || !new File(entry, "original.bin").exists()) removeOwned(entry);
            }
        }
    }
    private void ensureLibrary() throws IOException {
        if (!books.isDirectory() && !books.mkdirs()) throw new IOException("Cannot create library");
        if (Files.isSymbolicLink(books.toPath())) throw new IOException("Unsafe library path");
    }
    synchronized Book importBook(InputStream source, String name, TextImporter.Observer observer) throws IOException {
        try { ensureLibrary(); } catch (IOException error) { source.close(); throw error; }
        File staging = new File(books, ".import-" + UUID.randomUUID());
        try {
            TextImporter.Result result = TextImporter.prepare(source, staging, null, observer);
            writeCacheHash(staging, observer);
            TextImporter.check(observer);
            Book existing = get(result.id);
            File destination = directory(result.id);
            if (existing != null) {
                // Encoding correction and deliberate cache eviction do not change original identity.
                original(result.id, observer);
                return existing;
            }
            if (destination.exists()) {
                TextImporter.Result orphan = TextImporter.readIndex(destination);
                if (!result.id.equals(orphan.id)) throw new IOException("Conflicting library directory");
                verifyDuplicate(staging, destination, result.id, observer);
            } else {
                DurableFiles.syncDirectory(staging);
                if (!staging.renameTo(destination)) throw new IOException("Cannot publish imported book");
                DurableFiles.syncDirectory(books);
            }
            ContentValues values = new ContentValues();
            values.put("id", result.id); values.put("title", title(name)); values.put("charset", result.charset);
            values.put("length", result.length); values.put("source_bytes", result.sourceBytes);
            if (observer != null) observer.beforeWrite(65536);
            SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
            try { db.insertOrThrow("books", null, values); db.setTransactionSuccessful(); }
            finally { db.endTransaction(); }
            return get(result.id);
        } finally { removeOwned(staging); }
    }
    private static void verifyDuplicate(File staging, File destination, String id, TextImporter.Observer observer) throws IOException {
        TextImporter.Result stored = TextImporter.readIndex(destination);
        if (!id.equals(stored.id) || !id.equals(CoverStore.hash(new File(destination, "original.bin"), observer)))
            throw new IOException("Damaged private original");
        try (InputStream a = new FileInputStream(new File(staging, "text.u16")); InputStream b = new FileInputStream(new File(destination, "text.u16"))) {
            byte[] left = new byte[16384], right = new byte[16384]; int n;
            while ((n = a.read(left)) != -1) {
                TextImporter.check(observer); int offset = 0;
                while (offset < n) { int read = b.read(right, offset, n - offset); if (read < 0) throw new IOException("Truncated private text"); offset += read; }
                for (int i = 0; i < n; i++) if (left[i] != right[i]) throw new IOException("Damaged private text");
            }
            if (b.read() != -1) throw new IOException("Conflicting private text");
        }
    }
    File original(String id) throws IOException {
        return original(id, null);
    }
    private File original(String id, TextImporter.Observer observer) throws IOException {
        if (get(id) == null) throw new IOException("Book unavailable");
        File original = originalIn(directory(id));
        if (!id.equals(CoverStore.hash(original, observer))) throw new IOException("Damaged private original");
        return original;
    }
    private File originalIn(File folder) throws IOException {
        checkOwnedDirectory(folder);
        File original = new File(folder, "original.bin");
        if (!regularChild(folder, original)) throw new IOException("Private original unavailable");
        return original;
    }
    private boolean verifiedOriginal(File folder, String id) throws IOException {
        if (!folder.exists()) return false;
        try { return id.equals(CoverStore.hash(originalIn(folder))); }
        catch (InterruptedIOException error) { throw error; }
        catch (IOException error) { return false; }
    }

    synchronized TextImporter.Result ensureCache(String id, TextImporter.Observer observer) throws IOException {
        Book book = get(id);
        if (book == null) throw new IOException("Book unavailable");
        try { return validCache(directory(id), book, observer); }
        catch (InterruptedIOException error) { throw error; }
        catch (IOException damaged) {
            rebuildInternal(book, book.charset, observer, false);
            return validCache(directory(id), get(id), observer);
        }
    }
    synchronized Book rebuild(String id, String charset, TextImporter.Observer observer) throws IOException {
        Book book = get(id);
        if (book == null) throw new IOException("Book unavailable");
        return rebuildInternal(book, charset, observer, true);
    }
    private Book rebuildInternal(Book old, String charset, TextImporter.Observer observer, boolean remap) throws IOException {
        File original = original(old.id, observer);
        String requested;
        try { requested = charset == null ? null : Charset.forName(charset).name(); }
        catch (IllegalArgumentException invalid) { throw new IOException("Unsupported text encoding", invalid); }
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
            TextImporter.Result result = TextImporter.prepare(new FileInputStream(original), staging, requested, guarded);
            if (!old.id.equals(result.id)) throw new IOException("Private original changed during rebuild");
            writeCacheHash(staging, guarded);
            TextImporter.check(guarded); DurableFiles.syncDirectory(staging);
            if (!destination.renameTo(previous)) throw new IOException("Cannot preserve previous text");
            swapped = true; DurableFiles.syncDirectory(books);
            if (!staging.renameTo(destination)) throw new IOException("Cannot publish rebuilt text");
            DurableFiles.syncDirectory(books);
            boolean changed = remap && !Charset.forName(old.charset).name().equals(result.charset);
            guarded.beforeWrite(65536);
            SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
            try {
                ContentValues values = new ContentValues(); values.put("charset", result.charset); values.put("length", result.length);
                values.put("source_bytes", result.sourceBytes);
                if (changed) values.put("offset", remap(old.offset, old.length, result.length));
                if (db.update("books", values, "id=?", new String[]{old.id}) != 1) throw new IOException("Book removed during rebuild");
                if (changed) {
                    List<TextImporter.Chapter> bookmarks = bookmarks(old.id);
                    db.delete("bookmarks", "book_id=?", new String[]{old.id});
                    for (TextImporter.Chapter bookmark : bookmarks) {
                        long offset = remap(bookmark.offset, old.length, result.length);
                        // An encoding can collapse adjacent positions; retain both labels at that anchor.
                        String label = bookmark.title;
                        try (Cursor cursor = db.query("bookmarks", new String[]{"label"}, "book_id=? AND offset=?", new String[]{old.id, Long.toString(offset)}, null, null, null)) {
                            if (cursor.moveToFirst()) label = cursor.getString(0) + " / " + label;
                        }
                        ContentValues mark = new ContentValues(); mark.put("book_id", old.id); mark.put("offset", offset); mark.put("label", label);
                        if (db.insertWithOnConflict("bookmarks", null, mark, SQLiteDatabase.CONFLICT_REPLACE) < 0)
                            throw new IOException("Cannot remap bookmark");
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            removeOwned(previous); DurableFiles.syncDirectory(books);
            return get(old.id);
        } catch (IOException | RuntimeException error) {
            if (swapped) try { recoverPrevious(previous, old.id, token); }
            catch (IOException | RuntimeException recovery) { error.addSuppressed(recovery); }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot rebuild private text", error);
        } finally {
            if (staging.exists() && !previous.exists() && verifiedOriginal(destination, old.id)) removeOwned(staging);
        }
    }
    private static long remap(long offset, long oldLength, long newLength) {
        if (oldLength <= 0 || newLength <= 0) return 0;
        return Math.max(0, Math.min(newLength, Math.round((double) Math.max(0, Math.min(oldLength, offset)) / oldLength * newLength)));
    }
    private TextImporter.Observer guarded(TextImporter.Observer observer) {
        return new TextImporter.Observer() {
            public void progress(String phase, long bytes) { if (observer != null) observer.progress(phase, bytes); }
            public boolean cancelled() { return observer != null && observer.cancelled(); }
            public void beforeWrite(long bytes) throws IOException {
                TextImporter.check(observer);
                if (bytes < 0 || books.getUsableSpace() < SPACE_MARGIN + bytes) throw new IOException("Not enough private storage");
                if (observer != null) observer.beforeWrite(bytes);
            }
        };
    }
    private static boolean matches(TextImporter.Result result, Book book) {
        return book != null && result.id.equals(book.id) && result.charset.equalsIgnoreCase(book.charset)
                && result.length == book.length && result.sourceBytes == book.sourceBytes;
    }
    private TextImporter.Result validCache(File folder, Book book) throws IOException {
        return validCache(folder, book, null);
    }
    private TextImporter.Result validCache(File folder, Book book, TextImporter.Observer observer) throws IOException {
        checkOwnedDirectory(folder);
        for (String name : new String[]{"text.u16", "index.bin"})
            if (!regularChild(folder, new File(folder, name))) throw new IOException("Text cache unavailable");
        TextImporter.Result result = TextImporter.readIndex(folder);
        if (!matches(result, book)) throw new IOException("Text cache metadata mismatch");
        File fingerprint = new File(folder, "cache.sha256");
        if (!Files.exists(fingerprint.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            // M1.5 caches predate fingerprints. Keep them readable even when storage is full.
            if (!result.id.equals(CoverStore.hash(originalIn(folder), observer))) throw new IOException("Damaged private original");
            if (folder.getUsableSpace() >= SPACE_MARGIN + 4096) {
                try { writeCacheHash(folder, observer); }
                catch (InterruptedIOException cancelled) { throw cancelled; }
                catch (IOException unavailable) { /* Fingerprinting is optional for a structurally valid legacy cache. */ }
            }
            return result;
        }
        if (!regularChild(folder, fingerprint)) throw new IOException("Unsafe cache fingerprint");
        if (fingerprint.length() != 64) throw new IOException("Invalid cache fingerprint");
        byte[] bytes = new byte[64];
        try (DataInputStream in = new DataInputStream(new FileInputStream(fingerprint))) { in.readFully(bytes); }
        String expected = new String(bytes, "US-ASCII");
        if (!expected.matches("[0-9a-f]{64}") || !expected.equals(CoverStore.hash(new File(folder, "text.u16"), observer)))
            throw new IOException("Damaged text cache");
        return result;
    }
    private static void writeCacheHash(File folder, TextImporter.Observer observer) throws IOException {
        String hash = CoverStore.hash(new File(folder, "text.u16"), observer);
        TextImporter.check(observer);
        if (observer != null) observer.beforeWrite(64);
        File temporary = new File(folder, ".cache.sha256.tmp");
        if (Files.isSymbolicLink(temporary.toPath())) throw new IOException("Unsafe cache fingerprint staging");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(hash.getBytes("US-ASCII")); out.getFD().sync(); }
            if (!temporary.renameTo(new File(folder, "cache.sha256"))) throw new IOException("Cannot publish cache fingerprint");
            DurableFiles.syncDirectory(folder);
        } finally { if (temporary.exists()) temporary.delete(); }
    }
    private void recoverPrevious(File previous, String id, String token) throws IOException {
        if (!previous.exists()) return;
        checkOwnedDirectory(previous);
        Book book = get(id);
        if (book == null) return; // No deletion journal: ownership of a surviving original is uncertain.
        File destination = directory(id), staging = new File(books, ".rebuild-" + id + "-" + token);
        try {
            validCache(destination, book);
            if (verifiedOriginal(destination, id)) { removeOwned(previous); DurableFiles.syncDirectory(books); return; }
        } catch (InterruptedIOException error) { throw error; }
        catch (IOException unavailable) { /* The database still describes the previous generation. */ }
        if (!verifiedOriginal(previous, id)) {
            if (verifiedOriginal(destination, id)) return; // A partial old-cache cleanup must not block repair of the surviving original.
            throw new IOException("Cannot verify previous private original");
        }
        if (destination.exists()) {
            if (staging.exists()) throw new IOException("Conflicting rebuild recovery");
            if (!destination.renameTo(staging)) throw new IOException("Cannot retain uncommitted cache");
            DurableFiles.syncDirectory(books);
        }
        if (!previous.renameTo(destination)) throw new IOException("Cannot restore private original");
        DurableFiles.syncDirectory(books);
        removeOwned(staging);
    }

    synchronized void deleteBook(String id) throws IOException {
        if (get(id) == null) return;
        ensureLibrary();
        File source = directory(id), journal = new File(books, ".delete-" + id + "-" + UUID.randomUUID());
        if (source.exists()) {
            checkOwnedDirectory(source);
            if (!source.renameTo(journal)) throw new IOException("Cannot stage book deletion");
        } else if (!journal.mkdir()) throw new IOException("Cannot stage book deletion");
        try {
            DurableFiles.syncDirectory(books);
            SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
            try {
                db.delete("bookmarks", "book_id=?", new String[]{id});
                db.delete("books", "id=?", new String[]{id});
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (IOException | RuntimeException error) {
            try { recoverDeletion(journal, id); } catch (IOException | RuntimeException recovery) { error.addSuppressed(recovery); }
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
                if (!journal.renameTo(destination)) throw new IOException("Cannot restore deleted book");
                DurableFiles.syncDirectory(books);
            }
            return;
        }
        covers.remove(id);
        File[] entries = books.listFiles();
        if (entries == null) throw new IOException("Cannot inspect deleted book files");
        for (File entry : entries) {
            Matcher operation = OPERATION.matcher(entry.getName());
            if (!entry.equals(journal) && (entry.getName().equals(id) || (operation.matches() && operation.group(2).equals(id)))) removeOwned(entry);
        }
        removeOwned(journal); DurableFiles.syncDirectory(books);
    }
    private void checkOwnedDirectory(File folder) throws IOException {
        if (Files.isSymbolicLink(books.toPath()) || !Files.isDirectory(folder.toPath(), LinkOption.NOFOLLOW_LINKS)
                || !folder.getCanonicalFile().getParentFile().equals(books.getCanonicalFile()))
            throw new IOException("Unsafe private book directory");
    }
    static boolean regularChild(File folder, File file) throws IOException {
        return Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
                && file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile());
    }
    private void removeOwned(File folder) throws IOException {
        if (!Files.exists(folder.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
        checkOwnedDirectory(folder);
        removeTree(folder);
    }
    /** Caller established a private directory; links may be unlinked, never followed. */
    private static void removeTree(File folder) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder.toPath())) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) removeTree(child.toFile());
                else Files.delete(child);
            }
        }
        Files.delete(folder.toPath());
    }
    private static String title(String name) {
        if (name == null || name.trim().isEmpty()) return "未命名 TXT";
        String value = name.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".txt")) value = value.substring(0, value.length() - 4);
        return value.isEmpty() ? "未命名 TXT" : value.substring(0, Math.min(160, value.length()));
    }
    private static String label(String text, int limit) {
        String value = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Name is empty");
        return value.substring(0, Math.min(limit, value.length()));
    }
    synchronized void saveProgress(String id, long offset) {
        ContentValues values = new ContentValues(); values.put("offset", Math.max(0, offset)); values.put("opened", System.currentTimeMillis());
        getWritableDatabase().update("books", values, "id=?", new String[]{id});
    }
    synchronized boolean saveProgressIf(String id, long offset, java.util.function.BooleanSupplier current) {
        // Check under the same repository monitor as deletion/reimport, including already-dequeued writes.
        if (!current.getAsBoolean()) return false;
        saveProgress(id, offset); return true;
    }
    synchronized void addBookmark(String id, long offset, String label) {
        ContentValues values = new ContentValues(); values.put("book_id", id); values.put("offset", offset); values.put("label", label);
        getWritableDatabase().insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized void removeBookmark(String id, long offset) {
        getWritableDatabase().delete("bookmarks", "book_id=? AND offset=?", new String[]{id, Long.toString(offset)});
    }
    synchronized void renameBookmark(String id, long offset, String label) {
        ContentValues values = new ContentValues(); values.put("label", label(label, 160));
        getWritableDatabase().update("bookmarks", values, "book_id=? AND offset=?", new String[]{id, Long.toString(offset)});
    }
    List<TextImporter.Chapter> bookmarks(String id) {
        List<TextImporter.Chapter> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("bookmarks", new String[]{"label", "offset"}, "book_id=?", new String[]{id}, null, null, "offset")) {
            while (cursor.moveToNext()) list.add(new TextImporter.Chapter(cursor.getString(0), cursor.getLong(1)));
        }
        return list;
    }
}
