package com.tcgrestock.monitor;

final class SilenceRules {
    static final int UNTIL_PRICE_DROPS = -1;
    static final long PRACTICALLY_FOREVER = 253402300799L;
    static final int[] MINUTE_OPTIONS = {60, 360, 1440, 4320, UNTIL_PRICE_DROPS};
    static final String[] LABELS = {"1 hour", "6 hours", "24 hours", "3 days", "Until price drops"};

    private SilenceRules() {}

    static long snoozedUntil(long nowSeconds, int minutes) {
        if (minutes == UNTIL_PRICE_DROPS) return PRACTICALLY_FOREVER;
        return nowSeconds + Math.max(1, minutes) * 60L;
    }

    static String mode(int minutes) {
        return minutes == UNTIL_PRICE_DROPS ? "until_price_drop" : "timed";
    }

    static int optionIndex(int minutes) {
        for (int i = 0; i < MINUTE_OPTIONS.length; i++) {
            if (MINUTE_OPTIONS[i] == minutes) return i;
        }
        return 2;
    }
}
