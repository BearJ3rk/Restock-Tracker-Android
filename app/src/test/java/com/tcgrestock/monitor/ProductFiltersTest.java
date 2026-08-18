package com.tcgrestock.monitor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProductFiltersTest {
    @Test public void searchMatchesNameOrUrlIgnoringCase() {
        assertTrue(ProductFilters.matches("Pokemon Elite Box", "https://target.com/p/1", "Waiting",
                false, true, false, "POKEMON", "All products", "All retailers"));
        assertTrue(ProductFilters.matches("Elite Box", "https://target.com/p/1", "Waiting",
                false, true, false, "target", "All products", "All retailers"));
        assertFalse(ProductFilters.matches("Elite Box", "https://target.com/p/1", "Waiting",
                false, true, false, "walmart", "All products", "All retailers"));
    }

    @Test public void filtersByStateAndRetailer() {
        assertTrue(ProductFilters.matches("Box", "https://www.target.com/p/1", "IN STOCK — confirmed",
                false, true, true, "", "In stock", "Target"));
        assertFalse(ProductFilters.matches("Box", "https://www.target.com/p/1", "IN STOCK — confirmed",
                false, true, true, "", "Paused", "Target"));
        assertTrue(ProductFilters.matches("Box", "https://www.target.com/p/1", "Waiting",
                false, false, false, "", "Alerts off", "Target"));
    }
}
