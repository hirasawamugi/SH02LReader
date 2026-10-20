package com.sh02l.reader;

import android.content.Context;

import com.sh02l.reader.android.ReadingSurface;
import com.sh02l.reader.core.DocumentSession;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.epub.*;
import com.sh02l.reader.txt.*;

import java.io.File;
import java.io.IOException;

/** The application boundary that selects a format-specific engine. */
final class DocumentEngineRegistry {
    DocumentSession open(BookRepository repository, BookRepository.Book book) throws IOException {
        if ("txt".equals(book.format)) {
            TextImporter.Result index = repository.ensureCache(book.id, null);
            return new TxtDocumentSession(
                    new File(repository.directory(book.id), "text.u16"), index);
        }
        if ("epub".equals(book.format))
            return new EpubDocumentSession(repository.original(book.id));
        throw new IOException("Unsupported book format: " + book.format);
    }

    ReadingSurface surface(Context context, DocumentSession document) throws IOException {
        if ("txt".equals(document.format())) return new TxtReadingSurface(context);
        if ("epub".equals(document.format())) return new EpubReadingSurface(context);
        throw new IOException("Unsupported renderer");
    }
}
