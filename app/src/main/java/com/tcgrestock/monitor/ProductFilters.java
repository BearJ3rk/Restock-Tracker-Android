package com.tcgrestock.monitor;

import java.net.URI;
import java.util.Locale;

final class ProductFilters {
    static final String[] STATUS_OPTIONS = {
            "All products", "In stock", "Paused", "Alerts on", "Alerts off", "Price limit"
    };

    private ProductFilters() {}

    static boolean matches(
            String name,
            String url,
            String status,
            boolean paused,
            boolean alertsEnabled,
            boolean hasPriceLimit,
            String query,
            String statusFilter,
            String retailerFilter) {
        String search = query == null ? "" : query.trim().toLowerCase(Locale.US);
        String searchable = ((name == null ? "" : name) + " " + (url == null ? "" : url))
                .toLowerCase(Locale.US);
        if (!search.isEmpty() && !searchable.contains(search)) return false;

        if ("In stock".equals(statusFilter) && (status == null || !status.startsWith("IN STOCK"))) {
            return false;
        }
        if ("Paused".equals(statusFilter) && !paused) return false;
        if ("Alerts on".equals(statusFilter) && !alertsEnabled) return false;
        if ("Alerts off".equals(statusFilter) && alertsEnabled) return false;
        if ("Price limit".equals(statusFilter) && !hasPriceLimit) return false;

        return retailerFilter == null || retailerFilter.equals("All retailers")
                || retailerFilter.equals(retailer(url));
    }

    static String retailer(String url) {
        try {
            String host = new URI(url == null ? "" : url).getHost();
            if (host == null || host.isEmpty()) return "Other";
            host = host.toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                String label = parts[parts.length - 2];
                return Character.toUpperCase(label.charAt(0)) + label.substring(1);
            }
            return host;
        } catch (Exception e) {
            return "Other";
        }
    }
}
