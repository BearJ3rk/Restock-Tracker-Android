package com.tcgrestock.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StockConfirmationTest {
    @Test public void requiresTwoConsecutiveInStockChecksByDefault() {
        int first = StockConfirmation.nextCount(true, 0, 2);
        int second = StockConfirmation.nextCount(true, first, 2);
        assertEquals(1, first);
        assertFalse(StockConfirmation.isConfirmed(first, 2));
        assertEquals(2, second);
        assertTrue(StockConfirmation.isConfirmed(second, 2));
    }

    @Test public void outOfStockOrUnknownResetsConfirmation() {
        assertEquals(0, StockConfirmation.nextCount(false, 1, 2));
        assertEquals(0, StockConfirmation.nextCount(null, 1, 2));
    }

    @Test public void confirmationCountStopsAtRequiredCount() {
        assertEquals(2, StockConfirmation.nextCount(true, 2, 2));
    }
}
