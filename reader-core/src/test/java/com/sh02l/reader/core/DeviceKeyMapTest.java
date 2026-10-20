package com.sh02l.reader.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class DeviceKeyMapTest {
    @Test
    public void modelLabelsAndOptionalHelpMetadataBelongToTheProfile() {
        DeviceKeyMap.Builder builder =
                DeviceKeyMap.builder("future-v1")
                        .bind(Key.F1, 301, 401)
                        .label(Key.F1, "左软键")
                        .helpLayout("future-pad");
        DeviceKeyMap map = builder.build();
        builder.label(Key.F1, "新标签");
        assertEquals("左软键", map.label(Key.F1));
        assertEquals("future-pad", map.helpLayout());
        assertEquals(Key.F1, map.normalize(301, 0));
        assertEquals("", DeviceProfiles.select("other", "unknown", null).helpLayout());
    }

    @Test
    public void fallbackIsRestrictedToTheSelectedProfile() {
        DeviceKeyMap map = DeviceProfiles.select("SHARP", "SH-02L", null);
        assertEquals(Key.F2, map.normalize(0, 753));
        assertEquals(Key.UP, map.normalize(19, 753));
        assertEquals(Key.UNKNOWN, map.normalize(200, 753));
        assertEquals(Key.UNKNOWN, DeviceProfiles.select("Other", "Other", null).normalize(0, 753));
    }

    @Test
    public void differentPhysicalKeysCanOwnIndependentCyclesForOneLogicalAction() {
        DeviceKeyMap map =
                DeviceKeyMap.builder("fixture")
                        .bind(Key.DOWN, 301, 401)
                        .bind(Key.DOWN, 302, 402)
                        .build();
        final int[] movements = {0};
        KeyRouter router =
                new KeyRouter(
                        new KeyRouter.Target() {
                            public long contextVersion() {
                                return 1;
                            }

                            public Action resolve(Key key) {
                                return key == Key.DOWN ? Action.DOWN : Action.NONE;
                            }

                            public void perform(Action action) {
                                if (action == Action.DOWN) movements[0]++;
                            }
                        },
                        map);
        router.dispatch(new KeyInput(0, 301, 401, 0, 1, 1000, 1000, false));
        router.dispatch(new KeyInput(0, 302, 402, 0, 1, 1100, 1100, false));
        router.dispatch(new KeyInput(0, 301, 401, 1, 1, 1000, 1200, false));
        assertEquals(3, movements[0]);
        router.dispatch(new KeyInput(1, 302, 402, 0, 1, 1100, 1210, false));
        router.dispatch(new KeyInput(0, 301, 401, 2, 1, 1000, 1320, false));
        assertEquals(4, movements[0]);
    }
}
