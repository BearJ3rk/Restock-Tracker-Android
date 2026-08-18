package com.tcgrestock.monitor;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class MonitorService extends Service {
    public static final String ACTION_START = "com.tcgrestock.monitor.START";
    public static final String ACTION_STOP = "com.tcgrestock.monitor.STOP";
    public static final String CHANNEL_MONITOR = "monitor_service";
    public static final String CHANNEL_ALERTS = "restock_alerts";
    private static final int SERVICE_ID = 4701;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            startForeground(SERVICE_ID, serviceNotification("Monitoring active"));
            scheduler.scheduleWithFixedDelay(this::checkCycle, 0, 15, TimeUnit.SECONDS);
        }
        return START_STICKY;
    }

    private void checkCycle() {
        if (!running) return;
        JSONArray products = Store.loadProducts(this);
        long now = System.currentTimeMillis() / 1000L;
        ArrayList<JSONObject> alerts = new ArrayList<>();

        for (int i=0;i<products.length();i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            Store.migrate(p);
            if (p.optBoolean("paused", false)) continue;
            long next = p.optLong("next_check", 0);
            if (now < next) continue;
            int interval = Math.max(15, p.optInt("check_interval", 30));
            try { p.put("next_check", now + interval); } catch (Exception ignored) {}

            String url = p.optString("url","");
            if (url.isEmpty()) continue;
            Boolean old = p.has("last") && !p.isNull("last") ? p.optBoolean("last") : null;
            Detector.Result r = Detector.check(url);

            try {
                p.put("last_checked", now);
                if (!r.error.isEmpty()) {
                    p.put("status", "Check failed");
                    continue;
                }
                if (r.price != null) p.put("current_price", r.price);
                if (!r.seller.isEmpty()) p.put("last_seller", r.seller);

                if (Boolean.TRUE.equals(r.stock)) {
                    p.put("last_in_stock", now);
                    p.put("status", snoozed(p,now) ? "IN STOCK — silenced" : "IN STOCK — checked");
                } else if (Boolean.FALSE.equals(r.stock)) {
                    p.put("status", "Out of stock — checked");
                } else {
                    p.put("status", "Unknown — checked, no alert");
                }
                if (r.stock == null) p.put("last", JSONObject.NULL); else p.put("last", r.stock);

                boolean transitioned = Boolean.TRUE.equals(r.stock) && !Boolean.TRUE.equals(old);
                boolean priceDrop = Boolean.TRUE.equals(r.stock) && r.price != null
                        && snoozed(p,now) && lowerThanAlertPrice(p);
                boolean priceRulesAllow = alertRulesAllow(p, r.price);
                boolean hadRuleState = p.has("last_alert_rules_allow")
                        && !p.isNull("last_alert_rules_allow");
                boolean becamePriceEligible = Boolean.TRUE.equals(r.stock)
                        && priceRulesAllow && hadRuleState
                        && !p.optBoolean("last_alert_rules_allow", false);

                if (!hasPriceRule(p) || r.price != null) {
                    p.put("last_alert_rules_allow", priceRulesAllow);
                } else if (transitioned) {
                    // Remember that an in-stock transition is waiting for a verified price.
                    p.put("last_alert_rules_allow", false);
                }

                if ((transitioned || priceDrop || becamePriceEligible) && priceRulesAllow) alerts.add(p);
                recordHistory(p, r.stock);
            } catch (Exception ignored) {}
        }

        Store.saveProducts(this, products);
        if (!alerts.isEmpty()) postGroupedAlert(alerts);
        updateServiceNotification(products.length());
        sendBroadcast(new Intent("com.tcgrestock.monitor.DATA_CHANGED").setPackage(getPackageName()));
    }

    private boolean snoozed(JSONObject p,long now) {
        return p.optLong("snoozed_until",0) > now;
    }

    private boolean lowerThanAlertPrice(JSONObject p) {
        try {
            if (!p.has("current_price") || !p.has("last_alert_price")) return false;
            double c = p.getDouble("current_price");
            double a = p.getDouble("last_alert_price");
            return c < a;
        } catch (Exception e) { return false; }
    }

    private boolean alertRulesAllow(JSONObject p, Double freshlyDetectedPrice) {
        Double maximumPrice = optionalDouble(p, "alert_max_price");
        Double msrp = optionalDouble(p, "msrp");
        return AlertRules.priceAllowed(
                freshlyDetectedPrice,
                maximumPrice,
                p.optBoolean("alert_at_or_below_msrp", false),
                msrp);
    }

    private boolean hasPriceRule(JSONObject p) {
        return optionalDouble(p, "alert_max_price") != null
                || p.optBoolean("alert_at_or_below_msrp", false);
    }

    private Double optionalDouble(JSONObject p, String key) {
        try {
            if (!p.has(key) || p.isNull(key) || p.optString(key, "").isEmpty()) return null;
            return p.getDouble(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void recordHistory(JSONObject p, Boolean stock) {
        try {
            if (!p.has("current_price") || p.optString("current_price","").isEmpty()) return;
            JSONObject h = Store.loadHistory(this);
            String key = p.optString("url",p.optString("name",""));
            JSONArray arr = h.optJSONArray(key);
            if (arr == null) arr = new JSONArray();
            JSONObject row = new JSONObject();
            row.put("time", p.optLong("last_checked",0));
            row.put("price", p.getDouble("current_price"));
            row.put("status", Boolean.TRUE.equals(stock) ? "in_stock" : Boolean.FALSE.equals(stock) ? "out_of_stock" : "unknown");
            arr.put(row);
            while (arr.length() > 500) {
                JSONArray trimmed = new JSONArray();
                for (int i=arr.length()-500;i<arr.length();i++) trimmed.put(arr.get(i));
                arr = trimmed;
            }
            h.put(key, arr);
            Store.saveHistory(this,h);
        } catch (Exception ignored) {}
    }

    private void saveTriggeredAlerts(ArrayList<JSONObject> items) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("alerts", MODE_PRIVATE);
            JSONArray existing = new JSONArray(prefs.getString("triggered_items", "[]"));
            long now = System.currentTimeMillis() / 1000L;

            for (JSONObject p : items) {
                JSONObject row = new JSONObject();
                row.put("name", p.optString("name","Product"));
                row.put("url", p.optString("url",""));
                row.put("status", p.optString("status","IN STOCK"));
                if (p.has("current_price")) row.put("current_price", p.opt("current_price"));
                row.put("triggered_at", now);
                row.put("alert_state", "active");
                existing.put(row);
            }

            if (existing.length() > 100) {
                JSONArray trimmed = new JSONArray();
                for (int i = existing.length() - 100; i < existing.length(); i++) {
                    trimmed.put(existing.get(i));
                }
                existing = trimmed;
            }

            prefs.edit().putString("triggered_items", existing.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void postGroupedAlert(ArrayList<JSONObject> items) {
        saveTriggeredAlerts(items);
        try {
            Notification.InboxStyle style = new Notification.InboxStyle();
            int max = Math.min(items.size(), 6);
            for (int i=0;i<max;i++) {
                JSONObject p=items.get(i);
                String price=p.has("current_price") ? String.format(Locale.US,"$%.2f",p.optDouble("current_price")) : "";
                style.addLine(p.optString("name","Product")+" "+price);
                if (p.has("current_price")) p.put("last_alert_price",p.getDouble("current_price"));
            }
            style.setSummaryText(items.size()+" item(s) available");
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.putExtra("open_triggered_alerts", true);
            if (items.size() == 1) {
                open.putExtra("alert_url", items.get(0).optString("url",""));
            }
            PendingIntent pi = PendingIntent.getActivity(
                    this, 47, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(this, CHANNEL_ALERTS)
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle("TCG Restock Alert")
                    .setContentText(items.size()+" product(s) triggered")
                    .setStyle(style)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            getSystemService(NotificationManager.class).notify(4800, n);
        } catch (Exception ignored) {}
    }

    private Notification serviceNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, MonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("TCG Restock Monitor")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null,"Stop",stopPi).build())
                .build();
    }

    private void updateServiceNotification(int count) {
        getSystemService(NotificationManager.class).notify(SERVICE_ID, serviceNotification("Monitoring "+count+" products"));
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_MONITOR,"Monitoring service",NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ALERTS,"Restock alerts",NotificationManager.IMPORTANCE_HIGH));
    }

    private void stopMonitoring() {
        running=false;
        scheduler.shutdownNow();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        try {
            JSONObject s=Store.loadSettings(this);s.put("monitor_enabled",false);Store.saveSettings(this,s);
        } catch(Exception ignored){}
    }

    @Override public void onDestroy() {
        running=false;
        scheduler.shutdownNow();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
