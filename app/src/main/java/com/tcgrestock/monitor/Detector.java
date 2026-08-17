package com.tcgrestock.monitor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public final class Detector {
    private Detector() {}

    public static class Result {
        public Boolean stock;
        public Double price;
        public String seller = "";
        public String error = "";
    }

    public static Result check(String url) {
        Result r = new Result();
        try {
            String html = fetch(url);
            r.stock = detectStock(url, html);
            r.price = extractPrice(html);
            r.seller = extractSeller(html);
        } catch (Exception e) {
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return r;
    }

    public static String fetch(String urlText) throws Exception {
        URL url = new URL(urlText);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/151 Mobile Safari/537.36");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) throw new IOException("HTTP " + code);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        if (code >= 400) throw new IOException("HTTP " + code);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean containsAny(String h, String... terms) {
        for (String t : terms) if (h.contains(t)) return true;
        return false;
    }

    public static Boolean detectStock(String url, String html) {
        String h = html.toLowerCase();
        String u = url.toLowerCase();

        if (u.contains("target.com")) {
            if (containsAny(h, "\"availability\":\"outofstock\"", "\"availability\":\"out_of_stock\"",
                    "\"availability_status\":\"out_of_stock\"", "out of stock")) return false;
            if (containsAny(h, "\"availability\":\"instock\"", "\"availability\":\"in_stock\"",
                    "\"availability_status\":\"in_stock\"")) return true;
            return null;
        }

        if (u.contains("walmart.com")) {
            if (containsAny(h, "\"availabilitystatus\":\"out_of_stock\"", "\"availability\":\"outofstock\"",
                    "\"isoutofstock\":true", "out of stock", "sold out")) return false;
            if (containsAny(h, "\"availabilitystatus\":\"in_stock\"", "\"availability\":\"instock\"",
                    "\"isoutofstock\":false", "data-automation-id=\"atc\"")) return true;
            return null;
        }

        if (u.contains("pokemoncenter.com")) {
            if (containsAny(h, "\"availability\":\"outofstock\"", "\"available\":false",
                    "out of stock", "sold out", "currently unavailable")) return false;
            if (containsAny(h, "\"availability\":\"instock\"", "\"available\":true",
                    "data-testid=\"add-to-cart\"", "aria-label=\"add to cart\"")) return true;
            return null;
        }

        if (u.contains("gamersguildaz.com") || u.contains("doubleinfinitygaming.com")) {
            if (containsAny(h, "sale sold out", ">sold out<", "\"available\":false", "notify me when available")) return false;
            if (containsAny(h, "\"available\":true", "\"availability\":\"instock\"")) return true;
            return null;
        }

        if (containsAny(h, "\"availability\":\"outofstock\"", "\"available\":false",
                "out of stock", "sold out", "currently unavailable")) return false;
        if (containsAny(h, "\"availability\":\"instock\"", "\"available\":true",
                "data-testid=\"add-to-cart\"", "aria-label=\"add to cart\"")) return true;
        return null;
    }

    public static Double extractPrice(String html) {
        String[] patterns = {
                "\"price\"\\s*:\\s*\"(\\d+(?:\\.\\d{1,2})?)\"",
                "\"price\"\\s*:\\s*(\\d+(?:\\.\\d{1,2})?)",
                "\"current_retail\"\\s*:\\s*(\\d+(?:\\.\\d{1,2})?)",
                "\"formatted_current_price\"\\s*:\\s*\"\\$(\\d+(?:\\.\\d{1,2})?)\"",
                "\"offerPrice\"\\s*:\\s*\"?(\\d+(?:\\.\\d{1,2})?)\"?"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1));
                    if (v >= 0.5 && v <= 5000) return v;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static String extractSeller(String html) {
        String[] patterns = {
                "\"sellerName\"\\s*:\\s*\"([^\"]+)\"",
                "\"seller_name\"\\s*:\\s*\"([^\"]+)\"",
                "\"sellerDisplayName\"\\s*:\\s*\"([^\"]+)\"",
                "\"merchantName\"\\s*:\\s*\"([^\"]+)\""
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }
}
