package com.sh02l.reader.core;

import java.util.HashMap;
import java.util.Map;

/** Owns the whole physical key cycle, even when its action opens another screen. */
public final class KeyRouter {
    public interface Target {
        long contextVersion();

        Action resolve(Key key);

        void perform(Action action);
    }

    public static final class Result {
        public final Key key;
        public final boolean consumed;
        public final Action executed;
        public final String reason;

        Result(Key key, boolean consumed, Action executed, String reason) {
            this.key = key;
            this.consumed = consumed;
            this.executed = executed;
            this.reason = reason;
        }
    }

    private static final class Cycle {
        long downTime, context, lastEvent;
        int repeat;
        Action action;
    }

    private final Target target;
    private final DeviceKeyMap mapping;
    private final Map<Long, Cycle> cycles = new HashMap<>();
    private static final long REPEAT_INTERVAL_MS = 90;

    public KeyRouter(Target target) {
        this(target, DeviceProfiles.sh02l());
    }

    public KeyRouter(Target target, DeviceKeyMap mapping) {
        this.target = target;
        this.mapping = mapping;
    }

    public Key normalize(KeyInput input) {
        return mapping.normalize(input.code, input.scan);
    }

    private static long identity(KeyInput input) {
        long physical = input.scan > 0 ? input.scan : 0x10000L + input.code;
        return ((long) input.deviceId << 32) | (physical & 0xffffffffL);
    }

    /** A key that opened an input screen still owns its remaining repeat/release events. */
    public boolean owns(KeyInput input) {
        long identity = identity(input);
        Cycle cycle = cycles.get(identity);
        return cycle != null && cycle.downTime == input.downTime;
    }

    public Result dispatch(KeyInput input) {
        Key key = normalize(input);
        if (key == Key.UNKNOWN) return result(key, false, Action.NONE, "unmapped");
        long identity = identity(input);
        if (input.canceled || input.action == KeyInput.UP) {
            Cycle cycle = cycles.get(identity);
            if (cycle != null && cycle.downTime == input.downTime) cycles.remove(identity);
            return result(key, true, Action.NONE, input.canceled ? "canceled" : "release");
        }
        if (input.action != KeyInput.DOWN)
            return result(key, true, Action.NONE, "unsupported-action");
        Cycle cycle = cycles.get(identity);
        if (input.repeat == 0) {
            if (cycle != null && cycle.downTime == input.downTime) {
                return result(key, true, Action.NONE, "duplicate-down");
            }
            cycle = new Cycle();
            cycle.downTime = input.downTime;
            cycle.context = target.contextVersion();
            cycle.action = target.resolve(key);
            cycle.lastEvent = input.eventTime;
            cycles.put(identity, cycle); // Must precede an action that changes the active screen.
            target.perform(cycle.action);
            return result(key, true, cycle.action, "press");
        }
        if (cycle == null || cycle.downTime != input.downTime) {
            return result(key, true, Action.NONE, "orphan-repeat");
        }
        if (cycle.context != target.contextVersion())
            return result(key, true, Action.NONE, "context-changed");
        if (!cycle.action.repeatable) return result(key, true, Action.NONE, "not-repeatable");
        if (input.repeat <= cycle.repeat
                || input.eventTime - cycle.lastEvent < REPEAT_INTERVAL_MS) {
            return result(key, true, Action.NONE, "repeat-throttled");
        }
        cycle.repeat = input.repeat;
        cycle.lastEvent = input.eventTime;
        target.perform(cycle.action);
        return result(key, true, cycle.action, "repeat");
    }

    public void reset() {
        cycles.clear();
    }

    private static Result result(Key key, boolean consumed, Action action, String reason) {
        return new Result(key, consumed, action, reason);
    }
}
