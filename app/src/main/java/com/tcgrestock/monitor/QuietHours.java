package com.tcgrestock.monitor;

final class QuietHours {
    private QuietHours() {}

    static boolean isQuiet(boolean enabled, int minuteOfDay, int startMinute, int endMinute) {
        if (!enabled || startMinute == endMinute) return false;
        int minute = normalize(minuteOfDay);
        int start = normalize(startMinute);
        int end = normalize(endMinute);
        if (start < end) return minute >= start && minute < end;
        return minute >= start || minute < end;
    }

    static int parseMinutes(String value) {
        if (value == null || !value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return -1;
        String[] parts = value.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private static int normalize(int minute) {
        int normalized = minute % (24 * 60);
        return normalized < 0 ? normalized + 24 * 60 : normalized;
    }
}
