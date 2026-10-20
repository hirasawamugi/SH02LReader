package com.sh02l.reader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.sh02l.reader.android.*;
import com.sh02l.reader.core.*;

import java.io.IOException;
import java.util.ArrayDeque;

/** Owns reading jobs and callbacks independently of menu/import metadata jobs. UI-thread API. */
final class ReadingController {
    interface Listener {
        void opened(ReaderSession session);

        void changed(ReaderSession session);

        void failed(Exception error);
    }

    private final Context context;
    private final BookRepository repository;
    private final Listener listener;
    private final DocumentEngineRegistry engines = new DocumentEngineRegistry();
    private final LatestWorker worker = new LatestWorker();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayDeque<DocumentLocation> history = new ArrayDeque<>();
    private ReaderSession session;
    private long epoch;
    private boolean destroyed;

    ReadingController(Context context, BookRepository repository, Listener listener) {
        this.context = context;
        this.repository = repository;
        this.listener = listener;
    }

    void open(String id, ReadingPosition live, ReadingStyle style) {
        close();
        final long token = epoch;
        worker.submit(
                () -> {
                    DocumentSession document = null;
                    try {
                        BookRepository.Book book = repository.get(id);
                        if (book == null) throw new IOException("Book unavailable");
                        document = engines.open(repository, book);
                        final DocumentSession opened = document;
                        ReadingPosition position =
                                live != null
                                        ? live
                                        : new ReadingPosition(
                                                book.location == null
                                                        ? document.locationAt(book.progression)
                                                        : book.location,
                                                book.progression);
                        ui.post(
                                () -> {
                                    if (destroyed || epoch != token) {
                                        opened.close();
                                        return;
                                    }
                                    try {
                                        ReaderSession target =
                                                new ReaderSession(
                                                        book,
                                                        opened,
                                                        engines.surface(context, opened),
                                                        position);
                                        session = target;
                                        listener.opened(target);
                                        target.surface.open(
                                                opened,
                                                position.location,
                                                style,
                                                new ReadingSurface.Listener() {
                                                    public void onLocation(
                                                            DocumentLocation location,
                                                            double progression) {
                                                        if (current(token, target)) {
                                                            target.position =
                                                                    new ReadingPosition(
                                                                            location, progression);
                                                            target.displayed = true;
                                                        }
                                                    }

                                                    public void onReady() {
                                                        if (current(token, target))
                                                            listener.changed(target);
                                                    }

                                                    public void onError(Exception error) {
                                                        if (current(token, target))
                                                            listener.failed(error);
                                                    }
                                                });
                                    } catch (Exception error) {
                                        opened.close();
                                        if (current(token, null)) listener.failed(error);
                                    }
                                });
                    } catch (Exception error) {
                        if (document != null) document.close();
                        ui.post(
                                () -> {
                                    if (!destroyed && epoch == token) listener.failed(error);
                                });
                    }
                });
    }

    private boolean current(long token, ReaderSession target) {
        return !destroyed && epoch == token && (target == null || target == session);
    }

    View view() {
        return session == null ? null : session.surface.view();
    }

    void apply(ReadingStyle style) {
        if (session != null) session.surface.apply(style);
    }

    void move(int direction) {
        if (session != null) session.surface.move(direction);
    }

    void seek(DocumentLocation location) {
        if (session == null) return;
        if (session.displayed && !session.position.location.equals(location)) {
            if (history.size() == 32) history.removeFirst();
            history.addLast(session.position.location);
        }
        session.surface.seek(location);
    }

    boolean canReturn() {
        return !history.isEmpty();
    }

    void returnFromJump() {
        if (session != null && !history.isEmpty()) session.surface.seek(history.removeLast());
    }

    boolean ready() {
        return session != null && session.surface.ready();
    }

    String text() {
        return session == null ? "" : session.surface.text();
    }

    DocumentLocation end() {
        return session == null ? null : session.surface.endLocation();
    }

    void close() {
        epoch++;
        worker.cancel();
        history.clear();
        if (session != null) {
            session.close();
            session = null;
        }
    }

    void destroy() {
        close();
        destroyed = true;
        worker.close();
    }
}
