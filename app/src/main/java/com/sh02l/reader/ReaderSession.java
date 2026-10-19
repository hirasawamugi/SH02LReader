package com.sh02l.reader;

import com.sh02l.reader.core.DocumentSession;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.txt.TxtDocumentSession;
import com.sh02l.reader.txt.TxtPaginator;
import java.io.File;

/** Format-specific content behind the shared seek/move contract. */
final class ReaderSession {
    final BookRepository.Book book;
    final TextImporter.Result index;
    final File normalized;
    final DocumentSession<TxtPaginator.Page, TxtPaginator.Spec> paginator;
    ReaderSession(BookRepository.Book book, TextImporter.Result index, File directory) {
        this.book = book; this.index = index; normalized = new File(directory, "text.u16");
        paginator = new TxtDocumentSession(normalized, index.length);
    }
}
