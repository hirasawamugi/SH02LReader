package com.sh02l.reader.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyRouterTest {
    private final ReaderModel model = new ReaderModel();
    private final KeyRouter router = new KeyRouter(model);
    private long clock = 1000;

    private KeyInput event(Key key, int action, int repeat, long down, long when) {
        return new KeyInput(action, DeviceProfiles.sh02l().keyCode(key), DeviceProfiles.sh02l().scanCode(key), repeat, 3, down, when, false);
    }
    private void press(Key key) {
        long now = clock; clock += 40;
        router.dispatch(event(key, KeyInput.DOWN, 0, now, now));
        router.dispatch(event(key, KeyInput.UP, 0, now, now + 10));
    }

    @Test public void allMeasuredKeysNormalizeWithoutChangingValidCodes() {
        for (Key key : Key.values()) if (key != Key.UNKNOWN) {
            assertEquals(key, Key.normalize(DeviceProfiles.sh02l().keyCode(key), DeviceProfiles.sh02l().scanCode(key)));
            assertEquals(key, Key.normalize(0, DeviceProfiles.sh02l().scanCode(key)));
        }
        assertEquals(Key.UNKNOWN, Key.normalize(200, 753));
        assertEquals(Key.UNKNOWN, Key.normalize(0, 999));
        assertEquals(Key.UP, Key.normalize(19, 753));
    }
    @Test public void releaseAfterOpeningMenuCannotActivateItsFirstItem() {
        press(Key.F1);
        assertEquals(ReaderModel.Kind.MENU, model.top().kind);
        assertEquals(0, model.top().selected());
        assertEquals(2, model.frames().size());
    }
    @Test public void knownAndUnknownF2CloseOnlyOneLayerAndConsumeRelease() {
        press(Key.F1);
        for (int i = 0; i < 3; i++) press(Key.DOWN);
        press(Key.CENTER);
        assertEquals(ReaderModel.Kind.EXIT_CONFIRM, model.top().kind);
        long now = clock;
        KeyRouter.Result down = router.dispatch(new KeyInput(0, 0, 753, 0, 3, now, now, false));
        assertEquals(Action.BACK, down.executed);
        assertEquals(ReaderModel.Kind.MENU, model.top().kind);
        KeyRouter.Result up = router.dispatch(new KeyInput(1, 132, 753, 0, 3, now, now + 10, false));
        assertTrue(up.consumed);
        assertEquals(Action.NONE, up.executed);
        assertEquals(ReaderModel.Kind.MENU, model.top().kind);
        press(Key.F2);
        assertEquals(ReaderModel.Kind.HOME, model.top().kind);
    }
    @Test public void quickSeparateShortPressesAreNotLostToRepeatThrottle() {
        press(Key.DOWN); press(Key.DOWN);
        assertEquals(2, model.top().selected());
    }
    @Test public void duplicateInitialEventDoesNotActTwice() {
        KeyInput down = event(Key.DOWN, 0, 0, 1000, 1000);
        router.dispatch(down); router.dispatch(down);
        assertEquals(1, model.top().selected());
    }
    @Test public void holdCanMoveButDoesNotReplayAfterFocusLoss() {
        router.dispatch(event(Key.DOWN, 0, 0, 1000, 1000));
        router.dispatch(event(Key.DOWN, 0, 1, 1000, 1100));
        assertEquals(2, model.top().selected());
        router.dispatch(event(Key.DOWN, 0, 2, 1000, 1110));
        assertEquals(2, model.top().selected());
        router.reset();
        router.dispatch(event(Key.DOWN, 0, 3, 1000, 1300));
        assertEquals(2, model.top().selected());
    }
    @Test public void confirmHoldCannotOpenTheSampleAndThenTurnPages() {
        router.dispatch(event(Key.CENTER, 0, 0, 1000, 1000));
        assertEquals(ReaderModel.Kind.READER, model.top().kind);
        router.dispatch(event(Key.CENTER, 0, 1, 1000, 1400));
        assertEquals(0, model.page());
    }
    @Test public void readerConfirmCanRepeatWhenTheCycleStartedInTheReader() {
        press(Key.CENTER);
        router.dispatch(event(Key.CENTER, 0, 0, 2000, 2000));
        router.dispatch(event(Key.CENTER, 0, 1, 2000, 2400));
        assertEquals(2, model.page());
    }
    @Test public void repeatsDoNotCrossNavigationContexts() {
        router.dispatch(event(Key.DOWN, 0, 0, 1000, 1000));
        press(Key.F1);
        router.dispatch(event(Key.DOWN, 0, 1, 1000, 1500));
        assertEquals(ReaderModel.Kind.MENU, model.top().kind);
        assertEquals(0, model.top().selected());
    }
    @Test public void canceledDownDoesNotAct() {
        KeyRouter.Result result = router.dispatch(new KeyInput(0, 20, 108, 0, 3, 1000, 1000, true));
        assertTrue(result.consumed);
        assertEquals(0, model.top().selected());
    }
    @Test public void unknownKeysAreDelegatedButCallIsConsumedWithoutAnAction() {
        assertFalse(router.dispatch(new KeyInput(0, 26, 0, 0, 3, 1000, 1000, false)).consumed);
        KeyRouter.Result call = router.dispatch(event(Key.CALL, 0, 0, 2000, 2000));
        assertTrue(call.consumed);
        assertEquals(Action.NONE, call.executed);
        assertEquals(ReaderModel.Kind.HOME, model.top().kind);
    }
    @Test public void repeatFromAnotherDeviceCannotReuseAHeldCycle() {
        router.dispatch(event(Key.DOWN, 0, 0, 1000, 1000));
        router.dispatch(new KeyInput(0, 20, 108, 1, 9, 1000, 1400, false));
        assertEquals(1, model.top().selected());
    }
    @Test public void anOldReleaseCannotCancelANewerPressOfTheSameKey() {
        router.dispatch(event(Key.DOWN, 0, 0, 1000, 1000));
        router.dispatch(event(Key.DOWN, 0, 0, 1100, 1100));
        router.dispatch(event(Key.DOWN, 1, 0, 1000, 1120));
        router.dispatch(event(Key.DOWN, 0, 1, 1100, 1500));
        assertEquals(3, model.top().selected());
    }
    @Test public void repeatedMenuAndReturnCyclesKeepTheOriginalSelection() {
        press(Key.DOWN); press(Key.DOWN);
        for (int i = 0; i < 1000; i++) { press(Key.F1); press(Key.DOWN); press(Key.F2); }
        assertEquals(ReaderModel.Kind.HOME, model.top().kind);
        assertEquals(2, model.top().selected());
        assertEquals(1, model.frames().size());
    }
}
