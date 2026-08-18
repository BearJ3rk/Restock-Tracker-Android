package com.tcgrestock.monitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SilenceRulesTest {
    @Test public void timedSilenceUsesMinutes() {
        assertEquals(4600L, SilenceRules.snoozedUntil(1000L, 60));
        assertEquals("timed", SilenceRules.mode(60));
    }

    @Test public void priceDropSilenceDoesNotExpireOnTime() {
        assertEquals(SilenceRules.PRACTICALLY_FOREVER,
                SilenceRules.snoozedUntil(1000L, SilenceRules.UNTIL_PRICE_DROPS));
        assertEquals("until_price_drop", SilenceRules.mode(SilenceRules.UNTIL_PRICE_DROPS));
    }

    @Test public void unknownDefaultFallsBackToTwentyFourHours() {
        assertEquals(2, SilenceRules.optionIndex(999));
    }
}
