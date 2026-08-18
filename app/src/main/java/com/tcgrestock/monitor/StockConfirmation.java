package com.tcgrestock.monitor;

final class StockConfirmation {
    private StockConfirmation() {}

    static int nextCount(Boolean inStock, int previousCount, int requiredCount) {
        int required = Math.max(1, requiredCount);
        if (!Boolean.TRUE.equals(inStock)) return 0;
        return Math.min(required, Math.max(0, previousCount) + 1);
    }

    static boolean isConfirmed(int count, int requiredCount) {
        return count >= Math.max(1, requiredCount);
    }
}
