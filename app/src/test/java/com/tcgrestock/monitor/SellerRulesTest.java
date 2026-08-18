package com.tcgrestock.monitor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SellerRulesTest {
    @Test public void directRetailersDoNotRequireSellerMetadata() {
        assertTrue(SellerRules.sellerAllowed(
                "https://www.pokemoncenter.com/product/example", "", true));
        assertTrue(SellerRules.sellerAllowed(
                "https://doubleinfinitygaming.com/products/example", "", true));
    }

    @Test public void walmartRequiresWalmartAsSeller() {
        String url = "https://www.walmart.com/ip/123";
        assertTrue(SellerRules.sellerAllowed(url, "Walmart.com", true));
        assertFalse(SellerRules.sellerAllowed(url, "Card Reseller LLC", true));
        assertFalse(SellerRules.sellerAllowed(url, "", true));
    }

    @Test public void targetRequiresTargetAsSeller() {
        String url = "https://www.target.com/p/item/-/A-123";
        assertTrue(SellerRules.sellerAllowed(url, "Target", true));
        assertFalse(SellerRules.sellerAllowed(url, "Marketplace Cards", true));
    }

    @Test public void sellerVerificationCanBeDisabledPerProduct() {
        assertTrue(SellerRules.sellerAllowed(
                "https://www.walmart.com/ip/123", "Marketplace Cards", false));
    }
}
