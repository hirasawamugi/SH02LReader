package com.sh02l.reader.core;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DiagnosticBuffer {
    private final int capacity;
    private final Deque<String> lines = new ArrayDeque<>();
    private long events;
    public DiagnosticBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public void record(KeyInput input, KeyRouter.Result result) {
        events++;
        append((input.action == KeyInput.DOWN ? "DOWN" : input.action == KeyInput.UP ? "UP" : "OTHER")
                + " code=" + input.code + " scan=" + input.scan + " repeat=" + input.repeat
                + " device=" + input.deviceId + " down=" + input.downTime + " time=" + input.eventTime
                + " key=" + result.key + " consumed=" + result.consumed + " action=" + result.executed
                + " reason=" + result.reason);
    }
    public void append(String line) {
        if (lines.size() == capacity) lines.removeFirst();
        lines.addLast(line);
    }
    public String tail(int count) {
        StringBuilder text = new StringBuilder();
        int skip = Math.max(0, lines.size() - count), index = 0;
        for (String line : lines) if (index++ >= skip) text.append(line).append('\n');
        return text.toString();
    }
    public long eventCount() { return events; }
    public int size() { return lines.size(); }
    public void clear() { lines.clear(); events = 0; }
}
