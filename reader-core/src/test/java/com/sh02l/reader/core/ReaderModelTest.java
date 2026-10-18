package com.sh02l.reader.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReaderModelTest {
    @Test public void closingSettingsRestoresTheSelectedHomeItem() {
        ReaderModel model = new ReaderModel();
        model.select(2); model.perform(Action.ACTIVATE);
        assertEquals(ReaderModel.Kind.SETTINGS, model.top().kind);
        model.perform(Action.BACK);
        assertEquals(ReaderModel.Kind.HOME, model.top().kind);
        assertEquals(2, model.top().selected());
    }
    @Test public void readerF2KeepsReadingAndF4ReturnsToHome() {
        ReaderModel model = new ReaderModel();
        model.perform(Action.ACTIVATE);
        model.perform(model.resolve(Key.F2));
        assertEquals(ReaderModel.Kind.READER, model.top().kind);
        model.perform(model.resolve(Key.F4));
        assertEquals(ReaderModel.Kind.HOME, model.top().kind);
    }
    @Test public void nestedExitConfirmationReturnsToTheSameMenuItem() {
        ReaderModel model = new ReaderModel();
        model.perform(Action.MENU); model.select(3); model.perform(Action.ACTIVATE);
        assertEquals(ReaderModel.Kind.EXIT_CONFIRM, model.top().kind);
        model.perform(Action.BACK);
        assertEquals(ReaderModel.Kind.MENU, model.top().kind);
        assertEquals(3, model.top().selected());
    }
    @Test public void pageAndSettingsBoundsRemainValidUnderRepeatedInput() {
        ReaderModel model = new ReaderModel();
        model.perform(Action.ACTIVATE);
        for (int i = 0; i < 100; i++) {
            model.perform(Action.NEXT_PAGE); model.perform(Action.LARGER); model.perform(Action.DIMMER);
        }
        assertEquals(8, model.page()); assertEquals(32, model.fontSize()); assertEquals(1, model.brightness());
        for (int i = 0; i < 100; i++) {
            model.perform(Action.PREVIOUS_PAGE); model.perform(Action.SMALLER); model.perform(Action.BRIGHTER);
        }
        assertEquals(0, model.page()); assertEquals(14, model.fontSize()); assertEquals(10, model.brightness());
    }
    @Test public void snapshotRestoresNestedSelectionAppearanceAndBookmarks() {
        ReaderModel model = new ReaderModel();
        model.perform(Action.ACTIVATE); model.perform(Action.NEXT_CHAPTER);
        model.perform(Action.BOOKMARKS); model.perform(Action.ACTIVATE);
        model.perform(Action.LARGER); model.perform(Action.THEME); model.perform(Action.INFO);
        model.perform(Action.MENU); model.select(2); model.perform(Action.ACTIVATE); model.select(2);
        ReaderModel restored = ReaderModel.restore(model.save());
        assertEquals(model.save(), restored.save());
        assertEquals(ReaderModel.Kind.SETTINGS, restored.top().kind);
        restored.perform(Action.BACK); restored.perform(Action.BACK); restored.perform(Action.BOOKMARKS);
        assertEquals("bookmark-3", restored.items(restored.top()).get(1).id);
    }
    @Test public void invalidSnapshotAlwaysFallsBackToAnOperableHome() {
        String[] invalid = {null, "", "garbage", "2|0|20|0|8|1|HOME,0;|", "1|100|20|0|8|1|HOME,0;|",
                "1|0|20|0|8|1|NO_SUCH_SCREEN,0;|", "1|0|20|0|8|1|MENU,0;|", "1|0|20|0|8|1|HOME,0;|99"};
        for (String state : invalid) {
            ReaderModel model = ReaderModel.restore(state);
            assertEquals(ReaderModel.Kind.HOME, model.top().kind);
            model.perform(Action.ACTIVATE);
            assertEquals(ReaderModel.Kind.READER, model.top().kind);
        }
    }
    @Test public void rootAndEmptyReaderSelectionCannotUnderflowOrOverflow() {
        ReaderModel model = new ReaderModel();
        model.select(-100); assertEquals(0, model.top().selected());
        model.select(100); assertEquals(3, model.top().selected());
        model.back(); assertEquals(1, model.frames().size());
        model.select(0); model.perform(Action.ACTIVATE); model.select(100);
        assertEquals(0, model.top().selected());
    }
    @Test public void settingsAreReachableAndAdjustableWithDirectionKeys() {
        ReaderModel model = new ReaderModel();
        model.perform(Action.SETTINGS);
        model.perform(Action.LEFT); assertEquals(19, model.fontSize());
        model.perform(Action.RIGHT); assertEquals(20, model.fontSize());
        model.select(1); model.perform(Action.LEFT); assertEquals(2, model.theme());
        model.select(3); model.perform(Action.RIGHT); assertFalse(model.infoVisible());
    }
    @Test public void sampleContentsJumpClosesMenusAndReturnsToTheReader() {
        ReaderModel model = new ReaderModel();
        model.perform(Action.ACTIVATE); model.perform(Action.MENU); model.perform(Action.ACTIVATE);
        assertEquals(ReaderModel.Kind.CONTENTS, model.top().kind);
        model.select(2); model.perform(Action.ACTIVATE);
        assertEquals(ReaderModel.Kind.READER, model.top().kind);
        assertEquals(6, model.page());
    }
    @Test public void diagnosticHistoryIsBoundedWithoutLosingEventCount() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(4);
        KeyRouter router = new KeyRouter(new ReaderModel());
        for (int i = 0; i < 1000; i++) {
            KeyInput input = new KeyInput(1, 132, 753, 0, 3, i, i, false);
            buffer.record(input, router.dispatch(input));
        }
        assertEquals(4, buffer.size()); assertEquals(1000, buffer.eventCount());
        assertTrue(buffer.tail(1).contains("time=999"));
        assertFalse(buffer.tail(4).contains("time=995 "));
        buffer.clear(); assertEquals(0, buffer.size()); assertEquals(0, buffer.eventCount());
    }
}
