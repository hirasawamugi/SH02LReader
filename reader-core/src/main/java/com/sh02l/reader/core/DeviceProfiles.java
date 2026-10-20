package com.sh02l.reader.core;

/** Registry of measured model mappings and a conservative standard-key fallback. */
public final class DeviceProfiles {
    private static final Key[] KEYS = {
        Key.UP,
        Key.DOWN,
        Key.LEFT,
        Key.RIGHT,
        Key.CENTER,
        Key.F1,
        Key.F2,
        Key.F3,
        Key.F4,
        Key.ZERO,
        Key.ONE,
        Key.TWO,
        Key.THREE,
        Key.FOUR,
        Key.FIVE,
        Key.SIX,
        Key.SEVEN,
        Key.EIGHT,
        Key.NINE,
        Key.STAR,
        Key.POUND,
        Key.CALL
    };
    private static final int[] CODES = {
        19, 20, 21, 22, 23, 131, 132, 133, 134, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 5
    };
    private static final int[] SCANS = {
        103, 108, 105, 106, 28, 752, 753, 754, 755, 11, 2, 3, 4, 5, 6, 7, 8, 9, 10, 758, 759, 169
    };
    private static final DeviceKeyMap SH02L = create(true), GENERIC = create(false);

    private DeviceProfiles() {}

    private static DeviceKeyMap create(boolean measured) {
        DeviceKeyMap.Builder builder =
                DeviceKeyMap.builder(measured ? "sh02l-v1" : "android-standard-v1");
        for (int i = 0; i < KEYS.length; i++)
            builder.bind(KEYS[i], CODES[i], measured ? SCANS[i] : 0);
        return builder.build();
    }

    public static DeviceKeyMap sh02l() {
        return SH02L;
    }

    public static DeviceKeyMap select(String manufacturer, String model, String overrideId) {
        if ("sh02l-v1".equals(overrideId)) return SH02L;
        if ("android-standard-v1".equals(overrideId)) return GENERIC;
        return model != null && model.replace('_', '-').equalsIgnoreCase("SH-02L")
                ? SH02L
                : GENERIC;
    }
}
