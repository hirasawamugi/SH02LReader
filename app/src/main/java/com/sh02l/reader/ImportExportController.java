package com.sh02l.reader;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.sh02l.reader.core.importing.*;
import com.sh02l.reader.core.text.TextImporter;

import java.io.*;

/** Blocking transfers. The activity owns picker/permission lifecycle, never byte transactions. */
final class ImportExportController {
    enum ImportKind {
        BOOK,
        COVER,
        FONT
    }

    static final class ExportRequest {
        final File original;
        final String name, mime, sha256;

        ExportRequest(File original, String name, String mime, String sha256) {
            this.original = original;
            this.name = name;
            this.mime = mime;
            this.sha256 = sha256;
        }
    }

    private final Context context;
    private final BookRepository repository;

    ImportExportController(Context context, BookRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
    }

    ImportCoordinator.Outcome importAsset(
            AndroidImportSource source,
            ImportCoordinator.Mode mode,
            ImportKind kind,
            String target,
            TextImporter.Observer observer)
            throws IOException {
        ImportCoordinator coordinator =
                new ImportCoordinator(
                        new SpaceGuard(
                                () -> context.getFilesDir().getUsableSpace(), 8L * 1024 * 1024));
        return coordinator.run(
                source,
                mode,
                new ImportCoordinator.Committer() {
                    public long peakBytes(long known) throws IOException {
                        if (kind == ImportKind.BOOK)
                            return ImportCoordinator.Committer.super.peakBytes(known);
                        try {
                            return known < 0
                                    ? 1024 * 1024
                                    : kind == ImportKind.FONT
                                            ? Math.addExact(
                                                    Math.multiplyExact(known, 2), 1024 * 1024)
                                            : Math.addExact(known, 1024 * 1024);
                        } catch (ArithmeticException error) {
                            throw new IOException("Import budget overflow", error);
                        }
                    }

                    public ImportCoordinator.CommittedAsset commit(
                            ImportSource input, TextImporter.Observer guarded) throws IOException {
                        if (kind == ImportKind.BOOK) {
                            BookRepository.Book book =
                                    repository.importBook(
                                            input.open(), source.displayName(), guarded);
                            return new ImportCoordinator.CommittedAsset(
                                    book.id, repository.original(book.id), book.id);
                        }
                        if (kind == ImportKind.FONT)
                            return new FontStore(context).importFont(input.open(), guarded);
                        if (repository.get(target) == null)
                            throw new IOException("Cover target unavailable");
                        CoverStore.Record cover =
                                new CoverStore(context)
                                        .importCover(
                                                target,
                                                input.open(),
                                                source.displayName(),
                                                guarded);
                        return new ImportCoordinator.CommittedAsset(
                                cover.bookId, cover.original, cover.sha256);
                    }
                },
                observer);
    }

    ExportRequest prepareExport(String id, boolean cover) throws IOException {
        if (cover) {
            CoverStore.Record record = new CoverStore(context).get(id);
            if (record == null || !record.sha256.equals(CoverStore.hash(record.original)))
                throw new IOException("Cover original unavailable");
            return new ExportRequest(record.original, record.name, record.mime, record.sha256);
        }
        BookRepository.Book book = repository.get(id);
        if (book == null) throw new IOException("Book unavailable");
        return new ExportRequest(
                repository.original(id),
                book.title + "." + book.format,
                "epub".equals(book.format) ? "application/epub+zip" : "text/plain",
                id);
    }

    void export(ExportRequest request, Uri destination) throws IOException {
        if (!"content".equals(destination.getScheme()))
            throw new IOException("Document destination required");
        if (!request.sha256.equals(ImportCoordinator.sha256(request.original)))
            throw new IOException("Original changed");
        OriginalExporter.copy(
                request.original,
                context.getContentResolver().openOutputStream(destination, "w"),
                null);
    }

    boolean cleanupFailedExport(Uri destination, int grants) {
        if ((grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) return false;
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                destination,
                                new String[] {DocumentsContract.Document.COLUMN_FLAGS},
                                null,
                                null,
                                null)) {
            if (cursor != null
                    && cursor.moveToFirst()
                    && (cursor.getInt(0) & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0)
                return DocumentsContract.deleteDocument(context.getContentResolver(), destination);
        } catch (Exception ignored) {
        }
        return false;
    }
}
