package com.tcgrestock.monitor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BackupData {
    static final int SCHEMA_VERSION = 1;

    private BackupData() {}

    static JSONObject create(
            String appVersion,
            long exportedAt,
            JSONArray products,
            JSONObject settings,
            JSONObject history,
            JSONArray alerts,
            JSONObject health) throws JSONException {
        JSONObject backup = new JSONObject();
        backup.put("schema_version", SCHEMA_VERSION);
        backup.put("app_version", appVersion);
        backup.put("exported_at", exportedAt);
        backup.put("products", new JSONArray(products.toString()));
        backup.put("settings", new JSONObject(settings.toString()));
        backup.put("price_history", new JSONObject(history.toString()));
        backup.put("triggered_alerts", new JSONArray(alerts.toString()));
        backup.put("monitoring_health", new JSONObject(health.toString()));
        return backup;
    }

    static void validate(JSONObject backup) throws JSONException {
        int schema = backup.getInt("schema_version");
        if (schema != SCHEMA_VERSION) {
            throw new JSONException("Unsupported backup schema: " + schema);
        }
        backup.getJSONArray("products");
        backup.getJSONObject("settings");
        backup.getJSONObject("price_history");
        backup.getJSONArray("triggered_alerts");
        backup.getJSONObject("monitoring_health");
    }
}
