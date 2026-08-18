package com.tcgrestock.monitor;

final class AlertRules {
    private AlertRules() {}

    static boolean priceAllowed(
            Double currentPrice,
            Double maximumPrice,
            boolean atOrBelowMsrp,
            Double msrp) {
        if (maximumPrice != null) {
            if (currentPrice == null || currentPrice > maximumPrice) return false;
        }
        if (atOrBelowMsrp) {
            if (currentPrice == null || msrp == null || currentPrice > msrp) return false;
        }
        return true;
    }
}
