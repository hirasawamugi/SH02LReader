package com.sh02l.reader.core;

/** Logical keys. Hardware values live in the selected DeviceKeyMap. */
public enum Key {
    UNKNOWN, UP, DOWN, LEFT, RIGHT, CENTER, F1, F2, F3, F4,
    ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, STAR, POUND, CALL;
    /** Compatibility for diagnostic fixtures; production injects its selected map. */
    public static Key normalize(int code, int scan) { return DeviceProfiles.sh02l().normalize(code, scan); }
}
