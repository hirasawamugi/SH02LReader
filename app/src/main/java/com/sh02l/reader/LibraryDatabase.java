package com.sh02l.reader;

import android.content.Context;
import android.database.sqlite.*;

/** Schema and explicit upgrades; book bytes and rendering never enter this component. */
final class LibraryDatabase extends SQLiteOpenHelper {
    LibraryDatabase(Context context) {
        super(context, "library.db", null, 3);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE books(id TEXT PRIMARY KEY,title TEXT NOT NULL,charset TEXT NOT"
                    + " NULL,length INTEGER NOT NULL,source_bytes INTEGER NOT NULL,offset INTEGER"
                    + " NOT NULL DEFAULT 0,locator_version INTEGER NOT NULL DEFAULT 1,opened"
                    + " INTEGER NOT NULL DEFAULT 0,format TEXT NOT NULL DEFAULT"
                    + " 'txt',locator_payload TEXT NOT NULL DEFAULT '',progression REAL NOT NULL"
                    + " DEFAULT 0)");
        bookmarks(db);
        indexes(db);
    }

    private static void bookmarks(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE bookmarks(book_id TEXT NOT NULL,offset INTEGER NOT NULL DEFAULT"
                    + " 0,label TEXT NOT NULL,format TEXT NOT NULL DEFAULT 'txt',locator_version"
                    + " INTEGER NOT NULL DEFAULT 1,locator_payload TEXT NOT NULL,PRIMARY"
                    + " KEY(book_id,format,locator_version,locator_payload))");
    }

    private static void indexes(SQLiteDatabase db) {
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS books_library_order ON books(opened DESC,title COLLATE"
                    + " NOCASE,id)");
    }

    public void onUpgrade(SQLiteDatabase db, int old, int target) {
        if (old < 1 || old > 3 || target != 3)
            throw new IllegalStateException("Unsupported library migration");
        if (old < 2) indexes(db);
        if (old < 3) {
            db.execSQL("ALTER TABLE books ADD COLUMN format TEXT NOT NULL DEFAULT 'txt'");
            db.execSQL("ALTER TABLE books ADD COLUMN locator_payload TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE books ADD COLUMN progression REAL NOT NULL DEFAULT 0");
            db.execSQL(
                    "UPDATE books SET locator_payload=CAST(offset AS TEXT),progression=CASE WHEN"
                        + " length>0 THEN MIN(1.0,1.0*offset/length) ELSE 0 END");
            db.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_v2");
            bookmarks(db);
            db.execSQL(
                    "INSERT INTO"
                        + " bookmarks(book_id,offset,label,format,locator_version,locator_payload)"
                        + " SELECT book_id,offset,label,'txt',1,CAST(offset AS TEXT) FROM"
                        + " bookmarks_v2");
            db.execSQL("DROP TABLE bookmarks_v2");
        }
    }
}
