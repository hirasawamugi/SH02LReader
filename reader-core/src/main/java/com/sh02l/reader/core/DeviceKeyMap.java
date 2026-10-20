package com.sh02l.reader.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Immutable profile; scan fallback never overrides a valid keyCode. */
public final class DeviceKeyMap {
    private final String id;
    private final String helpLayout;
    private final Map<Key, String> labels;
    private final Map<Integer, Key> codes, scans;
    private final Map<Key, Integer> primaryCodes, primaryScans;

    private DeviceKeyMap(Builder source) {
        id = source.id;
        helpLayout = source.helpLayout;
        labels = Collections.unmodifiableMap(new EnumMap<>(source.labels));
        codes = Collections.unmodifiableMap(new HashMap<>(source.codes));
        scans = Collections.unmodifiableMap(new HashMap<>(source.scans));
        primaryCodes = new EnumMap<>(source.primaryCodes);
        primaryScans = new EnumMap<>(source.primaryScans);
    }

    public String id() {
        return id;
    }

    public Key normalize(int code, int scan) {
        return (code == 0 ? scans : codes).getOrDefault(code == 0 ? scan : code, Key.UNKNOWN);
    }

    public String label(Key key) {
        return labels.getOrDefault(key, key.name());
    }

    public String helpLayout() {
        return helpLayout;
    }

    public int keyCode(Key key) {
        return primaryCodes.getOrDefault(key, 0);
    }

    public int scanCode(Key key) {
        return primaryScans.getOrDefault(key, 0);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String helpLayout = "";
        private final EnumMap<Key, String> labels = new EnumMap<>(Key.class);
        private final Map<Integer, Key> codes = new HashMap<>(), scans = new HashMap<>();
        private final EnumMap<Key, Integer> primaryCodes = new EnumMap<>(Key.class),
                primaryScans = new EnumMap<>(Key.class);

        private Builder(String id) {
            if (id == null || id.isEmpty()) throw new IllegalArgumentException("profile id");
            this.id = id;
        }

        public Builder bind(Key key, int code, int scan) {
            if (key == null || key == Key.UNKNOWN || code <= 0 || scan < 0)
                throw new IllegalArgumentException("key binding");
            codes.put(code, key);
            if (scan > 0) scans.put(scan, key);
            primaryCodes.putIfAbsent(key, code);
            if (scan > 0) primaryScans.putIfAbsent(key, scan);
            return this;
        }

        public DeviceKeyMap build() {
            return new DeviceKeyMap(this);
        }

        public Builder label(Key key, String label) {
            if (key == null
                    || key == Key.UNKNOWN
                    || label == null
                    || label.isEmpty()
                    || label.length() > 16) throw new IllegalArgumentException("key label");
            labels.put(key, label);
            return this;
        }

        public Builder helpLayout(String id) {
            if (id == null || !id.matches("[a-z0-9-]{1,32}"))
                throw new IllegalArgumentException("help layout");
            helpLayout = id;
            return this;
        }
    }
}
