package com.sh02l.reader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/** The screen stack and its selection state; views never own a second selection. */
final class NavigationState {
    enum Kind {
        LIBRARY, SOURCES, FILES, IMPORT, IMPORT_MODE, READER, SETTINGS, HELP, DIAGNOSTICS,
        MENU, CONTENTS, BOOKMARKS, EXIT, INPUT, SEARCH_RESULTS, ENCODING, FONTS,
        BOOK_ACTION, MARK_ACTION, STORAGE, STORAGE_BOOKS, IMPORT_CLEANUP, CONFIRM;
        boolean overlay() {
            return this != LIBRARY && this != SOURCES && this != FILES && this != IMPORT
                    && this != READER && this != DIAGNOSTICS;
        }
    }
    static final class Row {
        final String id, label;
        final Object value;
        final Runnable action;
        final IntConsumer adjustment;
        Row(String id, String label) { this(id, label, null); }
        Row(String id, String label, Object value) { this(id, label, value, null, null); }
        private Row(String id, String label, Object value, Runnable action, IntConsumer adjustment) {
            this.id = id; this.label = label; this.value = value; this.action = action; this.adjustment = adjustment;
        }
        static Row action(String id, String label, Runnable action) { return new Row(id, label, null, action, null); }
        static Row adjust(String id, String label, IntConsumer action) { return new Row(id, label, null, () -> action.accept(1), action); }
    }
    static final class Screen {
        final Kind kind;
        final List<Row> rows = new ArrayList<>();
        final Set<String> checked = new LinkedHashSet<>();
        final String targetBookId;
        int selected, group, selectionTop, page;
        boolean hasSelectionTop, helpReading, busy;
        String title;
        File directory;
        String[] helpFunctions;
        Screen(Kind kind, String title) { this(kind, title, ""); }
        Screen(Kind kind, String title, String targetBookId) {
            this.kind = kind; this.title = title; this.targetBookId = targetBookId;
        }
    }
    final List<Screen> screens = new ArrayList<>();
    private long version = 1;
    Screen top() { return screens.get(screens.size() - 1); }
    Screen base() {
        for (int i = screens.size() - 1; i >= 0; i--) if (!screens.get(i).kind.overlay()) return screens.get(i);
        return screens.get(0);
    }
    void changed() { version++; }
    long contextVersion() { return version; }
}
