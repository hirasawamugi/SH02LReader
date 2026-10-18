package com.sh02l.reader.core;

/** Measured SH-02L keys; source: device/sh02l按键值.txt. */
public enum Key {
    UNKNOWN(-1, -1),
    UP(19, 103), DOWN(20, 108), LEFT(21, 105), RIGHT(22, 106), CENTER(23, 28),
    F1(131, 752), F2(132, 753), F3(133, 754), F4(134, 755),
    ZERO(7, 11), ONE(8, 2), TWO(9, 3), THREE(10, 4), FOUR(11, 5),
    FIVE(12, 6), SIX(13, 7), SEVEN(14, 8), EIGHT(15, 9), NINE(16, 10),
    STAR(17, 758), POUND(18, 759), CALL(5, 169);

    public final int code;
    public final int scan;
    Key(int code, int scan) { this.code = code; this.scan = scan; }

    public static Key normalize(int code, int scan) {
        if (code != 0) {
            for (Key key : values()) if (key != UNKNOWN && key.code == code) return key;
            return UNKNOWN;
        }
        for (Key key : values()) if (key != UNKNOWN && key.scan == scan) return key;
        return UNKNOWN;
    }
}
