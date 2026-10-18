package com.sh02l.reader.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppearanceStateTest {
    @Test public void readerChangeDoesNotChangeUi() {
        AppearanceState original = new AppearanceState(2, 2, 1);
        AppearanceState changed = original.withReaderTheme(0);
        assertEquals(2, changed.uiTheme); assertEquals(0, changed.readerTheme);
        assertEquals(2, original.readerTheme);
    }
    @Test public void changingShelfStylePreservesBothThemes() {
        AppearanceState state = new AppearanceState(2, 1, 1).withBookStyle(2);
        assertEquals(2, state.bookStyle); assertEquals(2, state.uiTheme); assertEquals(1, state.readerTheme);
    }
    @Test public void invalidValuesFallBackOnlyWithinTheirOwnScope() {
        AppearanceState state = new AppearanceState(-1, 2, 99);
        assertEquals(0, state.uiTheme); assertEquals(2, state.readerTheme); assertEquals(1, state.bookStyle);
    }
}
