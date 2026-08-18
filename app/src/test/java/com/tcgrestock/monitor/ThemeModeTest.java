package com.tcgrestock.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThemeModeTest {
    @Test public void systemFollowsDeviceAppearance() {
        assertTrue(ThemeMode.isDark(ThemeMode.SYSTEM, true));
        assertFalse(ThemeMode.isDark(ThemeMode.SYSTEM, false));
    }

    @Test public void manualModesOverrideDeviceAppearance() {
        assertTrue(ThemeMode.isDark(ThemeMode.DARK, false));
        assertFalse(ThemeMode.isDark(ThemeMode.LIGHT, true));
    }

    @Test public void mapsSettingsOptionsSafely() {
        assertEquals(0, ThemeMode.optionIndex("unknown"));
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueAt(1));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.valueAt(99));
    }
}
