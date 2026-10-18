package com.sh02l.reader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.util.*;

/** Private library. A complete directory is published before its catalogue transaction. */
final class BookRepository extends SQLiteOpenHelper {
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
    private BookRepository(Context context) {
        super(context, "library.db", null, 1); books = new File(context.getFilesDir(), "books");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE books(id TEXT PRIMARY KEY,title TEXT NOT NULL,charset TEXT NOT NULL,"
                + "length INTEGER NOT NULL,source_bytes INTEGER NOT NULL,offset INTEGER NOT NULL DEFAULT 0,"
                + "locator_version INTEGER NOT NULL DEFAULT 1,opened INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE bookmarks(book_id TEXT NOT NULL,offset INTEGER NOT NULL,label TEXT NOT NULL,"
                + "PRIMARY KEY(book_id,offset))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No migration from " + oldVersion + " to " + newVersion);
    }
    File directory(String id) {
        if (!id.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("book id");
        return new File(books, id);
    }
    List<Book> list() {
        List<Book> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("books", null, null, null, null, null, "opened DESC, title COLLATE NOCASE, id")) {
            while (cursor.moveToNext()) list.add(book(cursor));
        }
        return list;
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
    synchronized void cleanupStaging() throws IOException {
        if (!books.isDirectory() && !books.mkdirs()) throw new IOException("Cannot create library");
        File[] directories = books.listFiles(file -> file.getName().startsWith(".import-"));
        if (directories != null) for (File directory : directories) removeStaging(directory);
    }
    synchronized Book importBook(InputStream source, String name, TextImporter.Observer observer) throws IOException {
        if (!books.isDirectory() && !books.mkdirs()) { source.close(); throw new IOException("Cannot create library"); }
        File staging = new File(books, ".import-" + UUID.randomUUID());
        try {
            TextImporter.Result result = TextImporter.prepare(source, staging, null, observer);
            TextImporter.check(observer);
            Book existing = get(result.id);
            File destination = directory(result.id);
            if (existing != null) { TextImporter.readIndex(destination); return existing; }
            if (destination.exists()) {
                // A complete directory may survive a crash between rename and database commit.
                TextImporter.Result orphan = TextImporter.readIndex(destination);
                if (!result.id.equals(orphan.id)) throw new IOException("Conflicting library directory");
            } else if (!staging.renameTo(destination)) throw new IOException("Cannot publish imported book");
            // Once the complete directory is published, finish the short catalogue transaction even if
            // cancellation arrives. A late cancel can keep a completed book, never a half-written book.
            ContentValues values = new ContentValues();
            values.put("id", result.id); values.put("title", title(name)); values.put("charset", result.charset);
            values.put("length", result.length); values.put("source_bytes", result.sourceBytes);
            SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
            try { db.insertOrThrow("books", null, values); db.setTransactionSuccessful(); }
            finally { db.endTransaction(); }
            return get(result.id);
        } finally { removeStaging(staging); }
    }
    private static String title(String name) {
        if (name == null || name.trim().isEmpty()) return "未命名 TXT";
        String value = name.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".txt")) value = value.substring(0, value.length() - 4);
        return value.substring(0, Math.min(160, value.length()));
    }
    private void removeStaging(File staging) throws IOException {
        if (!staging.getName().startsWith(".import-") || !staging.getCanonicalFile().getParentFile().equals(books.getCanonicalFile()))
            throw new IOException("Unsafe staging path");
        File[] files = staging.listFiles();
        if (files != null) for (File file : files) if (!file.isDirectory() && !file.delete()) file.deleteOnExit();
        if (staging.exists() && !staging.delete()) throw new IOException("Cannot clean import staging");
    }
    void saveProgress(String id, long offset) {
        ContentValues values = new ContentValues(); values.put("offset", Math.max(0, offset)); values.put("opened", System.currentTimeMillis());
        getWritableDatabase().update("books", values, "id=?", new String[]{id});
    }
    void addBookmark(String id, long offset, String label) {
        ContentValues values = new ContentValues(); values.put("book_id", id); values.put("offset", offset); values.put("label", label);
        getWritableDatabase().insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
    List<TextImporter.Chapter> bookmarks(String id) {
        List<TextImporter.Chapter> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("bookmarks", new String[]{"label", "offset"}, "book_id=?", new String[]{id}, null, null, "offset")) {
            while (cursor.moveToNext()) list.add(new TextImporter.Chapter(cursor.getString(0), cursor.getLong(1)));
        }
        return list;
    }
}
