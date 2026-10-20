package com.sh02l.reader.core;

import java.util.function.IntPredicate;

/** Shared circular selection policy, independent of Android scroll state. */
public final class ListSelection {
    private ListSelection() { }
    public static int move(int selected, int delta, int count, IntPredicate selectable) {
        if (count <= 0) return -1;
        int direction = delta < 0 ? -1 : 1;
        int current = selected < 0 || selected >= count ? (direction > 0 ? -1 : 0) : selected;
        if (delta == 0 && current >= 0 && selectable.test(current)) return current;
        for (int tried = 0; tried < count; tried++) {
            current = (current + direction + count) % count;
            if (selectable.test(current)) return current;
        }
        return -1;
    }
}
