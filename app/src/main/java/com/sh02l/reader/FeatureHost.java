package com.sh02l.reader;

import android.app.Activity;

import com.sh02l.reader.NavigationState.Screen;
import com.sh02l.reader.core.DocumentLocation;

import java.util.function.Consumer;

/** Small UI seam shared by independent reading and library workflows. */
interface FeatureHost {
    interface Work<T> {
        T run() throws Exception;
    }

    interface InputSubmit {
        String submit(String text);
    }

    Activity activity();

    BookRepository repository();

    StorageManager storage();

    ReaderSession reading();

    ReadingSettings readingSettings();

    long currentOffset();

    void push(Screen screen);

    void back();

    void closeOverlays();

    void render();

    void notice(String message);

    <T> void task(String message, Work<T> work, Consumer<T> done);

    void input(String title, String initial, boolean numeric, InputSubmit submit);

    void refreshShelf(String preferredId);

    void open(BookRepository.Book book);

    void seekTo(long offset);

    void seekTo(DocumentLocation location);

    void quiesceBook(String id);

    void forgetBook(String id);

    void retryProgress();

    void layoutChanged();

    void importFont();

    void awaitProgress() throws Exception;
}
