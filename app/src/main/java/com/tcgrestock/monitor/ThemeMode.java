package com.tcgrestock.monitor;

final class ThemeMode {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String[] LABELS = {"System default", "Light", "Dark"};
    static final String[] VALUES = {SYSTEM, LIGHT, DARK};

    private ThemeMode() {}

    static boolean isDark(String mode, boolean systemDark) {
        if (LIGHT.equals(mode)) return false;
        if (DARK.equals(mode)) return true;
        return systemDark;
    }

    static int optionIndex(String mode) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(mode)) return i;
        }
        return 0;
    }

    static String valueAt(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : SYSTEM;
    }
}
