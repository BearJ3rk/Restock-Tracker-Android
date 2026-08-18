package com.tcgrestock.monitor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlertRulesTest {
    @Test public void productPriceOverridesGlobalPrice() {
        assertTrue(AlertRules.effectiveMaximumPrice(40.00, false, 60.00) == 40.00);
    }

    @Test public void blankProductPriceUsesGlobalUnlessIgnored() {
        assertTrue(AlertRules.effectiveMaximumPrice(null, false, 60.00) == 60.00);
        assertTrue(AlertRules.effectiveMaximumPrice(null, true, 60.00) == null);
    }

    @Test public void allowsAnyVerifiedOrUnknownPriceWhenNoRuleExists() {
        assertTrue(AlertRules.priceAllowed(null, null, false, null));
        assertTrue(AlertRules.priceAllowed(199.99, null, false, null));
    }

    @Test public void maximumPriceRequiresAFreshPrice() {
        assertFalse(AlertRules.priceAllowed(null, 49.99, false, null));
    }

    @Test public void maximumPriceAllowsEqualOrLowerPricesOnly() {
        assertTrue(AlertRules.priceAllowed(49.99, 49.99, false, null));
        assertTrue(AlertRules.priceAllowed(39.99, 49.99, false, null));
        assertFalse(AlertRules.priceAllowed(50.00, 49.99, false, null));
    }

    @Test public void msrpRuleRequiresBothPriceAndMsrp() {
        assertFalse(AlertRules.priceAllowed(null, null, true, 49.99));
        assertFalse(AlertRules.priceAllowed(49.99, null, true, null));
        assertTrue(AlertRules.priceAllowed(49.99, null, true, 49.99));
        assertFalse(AlertRules.priceAllowed(50.00, null, true, 49.99));
    }

    @Test public void bothRulesMustPass() {
        assertFalse(AlertRules.priceAllowed(55.00, 60.00, true, 49.99));
        assertFalse(AlertRules.priceAllowed(55.00, 50.00, true, 60.00));
        assertTrue(AlertRules.priceAllowed(45.00, 50.00, true, 49.99));
    }
}
