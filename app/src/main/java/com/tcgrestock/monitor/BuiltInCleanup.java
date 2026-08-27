package com.tcgrestock.monitor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

final class BuiltInCleanup {
    private BuiltInCleanup() {}

    static Set<String> urlSet(JSONArray urls) {
        HashSet<String> values = new HashSet<>();
        for (int i = 0; i < urls.length(); i++) {
            String value = normalize(urls.optString(i, ""));
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    static JSONArray withoutProducts(JSONArray products, Set<String> builtInUrls) {
        JSONArray kept = new JSONArray();
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product == null || !builtInUrls.contains(normalize(product.optString("url", "")))) {
                kept.put(products.opt(i));
            }
        }
        return kept;
    }

    static JSONArray withoutAlerts(JSONArray alerts, Set<String> builtInUrls) {
        JSONArray kept = new JSONArray();
        for (int i = 0; i < alerts.length(); i++) {
            JSONObject alert = alerts.optJSONObject(i);
            if (alert == null || !builtInUrls.contains(normalize(alert.optString("url", "")))) {
                kept.put(alerts.opt(i));
            }
        }
        return kept;
    }

    static JSONObject withoutHistory(JSONObject history, Set<String> builtInUrls) {
        JSONObject kept = new JSONObject();
        for (java.util.Iterator<String> keys = history.keys(); keys.hasNext();) {
            String key = keys.next();
            if (builtInUrls.contains(normalize(key))) continue;
            try { kept.put(key, history.get(key)); } catch (Exception ignored) {}
        }
        return kept;
    }

    private static String normalize(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
