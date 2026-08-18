package com.tcgrestock.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuietHoursTest {
    @Test public void overnightQuietHoursCrossMidnight() {
        assertTrue(QuietHours.isQuiet(true, 23 * 60, 22 * 60, 7 * 60));
        assertTrue(QuietHours.isQuiet(true, 6 * 60 + 59, 22 * 60, 7 * 60));
        assertFalse(QuietHours.isQuiet(true, 7 * 60, 22 * 60, 7 * 60));
        assertFalse(QuietHours.isQuiet(true, 12 * 60, 22 * 60, 7 * 60));
    }

    @Test public void daytimeQuietHoursStayInsideRange() {
        assertTrue(QuietHours.isQuiet(true, 13 * 60, 12 * 60, 14 * 60));
        assertFalse(QuietHours.isQuiet(true, 15 * 60, 12 * 60, 14 * 60));
        assertFalse(QuietHours.isQuiet(false, 13 * 60, 12 * 60, 14 * 60));
    }

    @Test public void parsesStrictTwentyFourHourTimes() {
        assertEquals(22 * 60 + 30, QuietHours.parseMinutes("22:30"));
        assertEquals(-1, QuietHours.parseMinutes("7:00"));
        assertEquals(-1, QuietHours.parseMinutes("24:00"));
    }
}
