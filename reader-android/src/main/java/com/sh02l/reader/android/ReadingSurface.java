package com.sh02l.reader.android;

import android.view.View;

import com.sh02l.reader.core.*;

public interface ReadingSurface extends AutoCloseable {
    interface Listener {
        void onReady();

        void onLocation(DocumentLocation location, double progression);

        void onError(Exception error);

        default void onNavigate(DocumentLocation location) {}
    }

    View view();

    void open(
            DocumentSession document,
            DocumentLocation location,
            ReadingStyle style,
            Listener listener);

    void move(int direction);

    void seek(DocumentLocation location);

    void apply(ReadingStyle style);

    boolean ready();

    String text();

    DocumentLocation endLocation();

    @Override
    void close();
}
