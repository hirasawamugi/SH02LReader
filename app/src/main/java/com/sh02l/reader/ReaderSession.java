package com.sh02l.reader;

import com.sh02l.reader.android.ReadingSurface;
import com.sh02l.reader.core.DocumentSession;
import com.sh02l.reader.core.ReadingPosition;

/** One owned document, renderer and last position actually displayed. */
final class ReaderSession {
    final BookRepository.Book book;
    final DocumentSession document;
    final ReadingSurface surface;
    ReadingPosition position;
    boolean displayed;

    ReaderSession(
            BookRepository.Book book,
            DocumentSession document,
            ReadingSurface surface,
            ReadingPosition position) {
        this.book = book;
        this.document = document;
        this.surface = surface;
        this.position = position;
    }

    void close() {
        android.view.View view = surface.view();
        if (view.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        surface.close();
        document.close();
    }
}
