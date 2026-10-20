package com.sh02l.reader.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class LegacyAppearanceTest {
    @Test
    public void historicalSettingsMigrateWithoutKeepingTheSampleNavigator() {
        for (String state :
                new String[] {
                    null,
                    "broken",
                    "1|0|26|2|4|0|HOME,0;READER,0;|2,",
                    "1|0|99|0|8|1|HOME,0;|",
                    "1|0|24|1|8|1|READER,0;|"
                }) {
            ReaderModel historical = ReaderModel.restore(state);
            LegacyAppearance actual = LegacyAppearance.restore(state);
            assertEquals(historical.fontSize(), actual.fontSize);
            assertEquals(historical.theme(), actual.theme);
            assertEquals(historical.brightness(), actual.brightness);
            assertEquals(historical.infoVisible(), actual.infoVisible);
        }
    }
}
