package com.sh02l.reader.core;

public final class KeyInput {
    public static final int DOWN = 0, UP = 1;
    public final int action, code, scan, repeat, deviceId;
    public final long downTime, eventTime;
    public final boolean canceled;

    public KeyInput(int action, int code, int scan, int repeat, int deviceId,
                    long downTime, long eventTime, boolean canceled) {
        this.action = action; this.code = code; this.scan = scan; this.repeat = repeat;
        this.deviceId = deviceId; this.downTime = downTime; this.eventTime = eventTime;
        this.canceled = canceled;
    }
}
