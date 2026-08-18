package com.tcgrestock.monitor;

import java.net.URI;
import java.util.Locale;

final class SellerRules {
    private SellerRules() {}

    static boolean requiresVerification(String url, boolean verifyMarketplaceSeller) {
        return verifyMarketplaceSeller && marketplace(url) != null;
    }

    static boolean sellerAllowed(String url, String seller, boolean verifyMarketplaceSeller) {
        if (!requiresVerification(url, verifyMarketplaceSeller)) return true;
        String marketplace = marketplace(url);
        String normalized = seller == null ? "" : seller.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) return false;

        if ("walmart".equals(marketplace)) {
            return normalized.equals("walmart")
                    || normalized.startsWith("walmart.com")
                    || normalized.startsWith("walmart inc");
        }
        if ("target".equals(marketplace)) {
            return normalized.equals("target")
                    || normalized.startsWith("target.com")
                    || normalized.startsWith("target corporation");
        }
        if ("amazon".equals(marketplace)) {
            return normalized.equals("amazon")
                    || normalized.startsWith("amazon.com")
                    || normalized.startsWith("amazon services");
        }
        return "ebay".equals(marketplace) && normalized.equals("ebay");
    }

    private static String marketplace(String url) {
        String host = "";
        try {
            host = new URI(url == null ? "" : url).getHost();
        } catch (Exception ignored) {}
        host = host == null ? "" : host.toLowerCase(Locale.US);
        if (isDomain(host, "walmart.com")) return "walmart";
        if (isDomain(host, "target.com")) return "target";
        if (isDomain(host, "amazon.com")) return "amazon";
        if (isDomain(host, "ebay.com")) return "ebay";
        return null;
    }

    private static boolean isDomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
