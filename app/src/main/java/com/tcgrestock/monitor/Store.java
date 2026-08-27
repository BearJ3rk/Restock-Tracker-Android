package com.tcgrestock.monitor;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Store {
    private static final String BUILT_IN_CLEANUP_V013 = "built_in_cleanup_v013";
    private Store() {}
    public static final String PRODUCTS_FILE = "products.json";
    public static final String SETTINGS_FILE = "settings.json";
    public static final String HISTORY_FILE = "price_history.json";
    public static final String HEALTH_FILE = "monitoring_health.json";

    public static synchronized JSONArray loadProducts(Context c) {
        try {
            File f = new File(c.getFilesDir(), PRODUCTS_FILE);
            if (!f.exists()) {
                String seed = readAsset(c, "default_products.json");
                writeText(f, seed);
            }
            JSONArray products = new JSONArray(readText(f));
            return removeLegacyBuiltIns(c, f, products);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static JSONArray removeLegacyBuiltIns(Context c, File productsFile, JSONArray products)
            throws Exception {
        android.content.SharedPreferences migrations = c.getSharedPreferences(
                "data_migrations", Context.MODE_PRIVATE);
        if (migrations.getBoolean(BUILT_IN_CLEANUP_V013, false)) return products;

        JSONArray legacyUrls = new JSONArray(readAsset(c, "legacy_builtin_urls_v012.json"));
        Set<String> builtInUrls = BuiltInCleanup.urlSet(legacyUrls);
        JSONArray keptProducts = BuiltInCleanup.withoutProducts(products, builtInUrls);
        int removedCount = products.length() - keptProducts.length();
        writeText(productsFile, keptProducts.toString(2));

        File historyFile = new File(c.getFilesDir(), HISTORY_FILE);
        if (historyFile.exists()) {
            try {
                JSONObject history = new JSONObject(readText(historyFile));
                writeText(historyFile, BuiltInCleanup.withoutHistory(history, builtInUrls).toString(2));
            } catch (Exception ignored) {}
        }

        android.content.SharedPreferences alerts = c.getSharedPreferences(
                "alerts", Context.MODE_PRIVATE);
        try {
            JSONArray triggered = new JSONArray(alerts.getString("triggered_items", "[]"));
            JSONArray pending = new JSONArray(alerts.getString("pending_quiet_items", "[]"));
            alerts.edit()
                    .putString("triggered_items",
                            BuiltInCleanup.withoutAlerts(triggered, builtInUrls).toString())
                    .putString("pending_quiet_items",
                            BuiltInCleanup.withoutAlerts(pending, builtInUrls).toString())
                    .apply();
        } catch (Exception ignored) {
            alerts.edit().remove("triggered_items").remove("pending_quiet_items").apply();
        }

        migrations.edit()
                .putBoolean(BUILT_IN_CLEANUP_V013, true)
                .putInt("built_in_cleanup_removed_count", removedCount)
                .putBoolean("built_in_cleanup_notice_pending", removedCount > 0)
                .apply();
        return keptProducts;
    }

    public static int consumeBuiltInCleanupCount(Context c) {
        android.content.SharedPreferences migrations = c.getSharedPreferences(
                "data_migrations", Context.MODE_PRIVATE);
        if (!migrations.getBoolean("built_in_cleanup_notice_pending", false)) return 0;
        int count = migrations.getInt("built_in_cleanup_removed_count", 0);
        migrations.edit().putBoolean("built_in_cleanup_notice_pending", false).apply();
        return count;
    }

    public static synchronized void saveProducts(Context c, JSONArray a) {
        try { writeText(new File(c.getFilesDir(), PRODUCTS_FILE), a.toString(2)); }
        catch (Exception ignored) {}
    }

    public static JSONObject loadSettings(Context c) {
        JSONObject defaults = new JSONObject();
        try {
            defaults.put("pokemon_refresh_seconds", 60);
            defaults.put("snooze_after_open_24h", true);
            defaults.put("monitor_enabled", false);
            defaults.put("dark_mode", true);
            defaults.put("theme_mode", ThemeMode.SYSTEM);
            defaults.put("global_alert_max_price", "");
            defaults.put("in_stock_confirmations_required", 2);
            defaults.put("verify_marketplace_sellers", true);
            defaults.put("quiet_hours_enabled", false);
            defaults.put("quiet_hours_start", "22:00");
            defaults.put("quiet_hours_end", "07:00");
            defaults.put("notification_silence_minutes", 1440);
            File f = new File(c.getFilesDir(), SETTINGS_FILE);
            if (!f.exists()) {
                writeText(f, defaults.toString(2));
                return defaults;
            }
            JSONObject saved = new JSONObject(readText(f));
            if (!saved.has("theme_mode")) {
                saved.put("theme_mode", saved.optBoolean("dark_mode", true)
                        ? ThemeMode.DARK : ThemeMode.LIGHT);
            }
            Iterator<String> keys = defaults.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (!saved.has(k)) saved.put(k, defaults.get(k));
            }
            return saved;
        } catch (Exception e) {
            return defaults;
        }
    }

    public static synchronized void saveSettings(Context c, JSONObject s) {
        try { writeText(new File(c.getFilesDir(), SETTINGS_FILE), s.toString(2)); }
        catch (Exception ignored) {}
    }

    public static JSONObject loadHistory(Context c) {
        try {
            File f = new File(c.getFilesDir(), HISTORY_FILE);
            if (!f.exists()) return new JSONObject();
            return new JSONObject(readText(f));
        } catch (Exception e) { return new JSONObject(); }
    }

    public static synchronized void saveHistory(Context c, JSONObject h) {
        try { writeText(new File(c.getFilesDir(), HISTORY_FILE), h.toString(2)); }
        catch (Exception ignored) {}
    }

    public static JSONObject loadHealth(Context c) {
        try {
            File f = new File(c.getFilesDir(), HEALTH_FILE);
            if (!f.exists()) return new JSONObject();
            return new JSONObject(readText(f));
        } catch (Exception e) { return new JSONObject(); }
    }

    public static synchronized void saveHealth(Context c, JSONObject health) {
        try { writeText(new File(c.getFilesDir(), HEALTH_FILE), health.toString(2)); }
        catch (Exception ignored) {}
    }

    public static String readAsset(Context c, String name) throws Exception {
        InputStream in = c.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }

    public static String readText(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }

    public static void writeText(File f, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    public static void migrate(JSONObject p) {
        try {
            if (!p.has("status")) p.put("status", "Waiting...");
            if (!p.has("msrp")) p.put("msrp", "");
            if (!p.has("current_price")) p.put("current_price", "");
            if (!p.has("snoozed_until")) p.put("snoozed_until", 0);
            if (!p.has("silence_mode")) p.put("silence_mode", "");
            if (!p.has("last_alert_price")) p.put("last_alert_price", "");
            if (!p.has("paused")) p.put("paused", false);
            if (!p.has("alerts_enabled")) p.put("alerts_enabled", true);
            if (!p.has("check_interval")) p.put("check_interval", 30);
            if (!p.has("alert_max_price")) p.put("alert_max_price", "");
            if (!p.has("ignore_global_alert_max_price")) p.put("ignore_global_alert_max_price", false);
            if (!p.has("alert_at_or_below_msrp")) p.put("alert_at_or_below_msrp", false);
            if (!p.has("ignore_third_party")) p.put("ignore_third_party", true);
            if (!p.has("in_stock_confirmation_count")) {
                p.put("in_stock_confirmation_count", p.optBoolean("last", false) ? 2 : 0);
            }
            if (!p.has("last_confirmed_in_stock")) {
                p.put("last_confirmed_in_stock", p.optBoolean("last", false));
            }
            if (!p.has("last_checked")) p.put("last_checked", 0);
            if (!p.has("last_successful_check")) p.put("last_successful_check", 0);
            if (!p.has("last_failed_check")) p.put("last_failed_check", 0);
            if (!p.has("last_check_error")) p.put("last_check_error", "");
            if (!p.has("consecutive_failures")) p.put("consecutive_failures", 0);
            if (!p.has("last_check_duration_ms")) p.put("last_check_duration_ms", 0);
            if (!p.has("last_in_stock")) p.put("last_in_stock", 0);
            if (!p.has("next_check")) p.put("next_check", 0);
        } catch (Exception ignored) {}
    }
}
