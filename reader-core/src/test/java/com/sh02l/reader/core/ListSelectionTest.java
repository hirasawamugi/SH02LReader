package com.sh02l.reader.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ListSelectionTest {
    @Test public void wrapsAndSkipsNonSelectableRows() {
        assertEquals(4, ListSelection.move(1, -1, 6, i -> i == 1 || i == 4));
        assertEquals(1, ListSelection.move(4, 1, 6, i -> i == 1 || i == 4));
        assertEquals(1, ListSelection.move(4, -1, 6, i -> i == 1 || i == 4));
    }
    @Test public void emptyAndSingleItemDoNotLoseSelection() {
        assertEquals(-1, ListSelection.move(0, 1, 0, i -> true));
        assertEquals(-1, ListSelection.move(0, 1, 3, i -> false));
        assertEquals(0, ListSelection.move(0, -1, 1, i -> true));
        assertEquals(0, ListSelection.move(0, 1, 1, i -> true));
    }
}
