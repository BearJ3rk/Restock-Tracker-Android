package com.tcgrestock.monitor;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class BackupDataTest {
    @Test public void createsCompleteValidatedBackup() throws Exception {
        JSONArray products = new JSONArray().put(new JSONObject().put("name", "Test product"));
        JSONObject backup = BackupData.create("0.11", 1234L, products,
                new JSONObject().put("monitor_enabled", false),
                new JSONObject(), new JSONArray(), new JSONObject());

        BackupData.validate(backup);
        assertEquals(1, backup.getInt("schema_version"));
        assertEquals("Test product", backup.getJSONArray("products")
                .getJSONObject(0).getString("name"));
    }

    @Test(expected = JSONException.class)
    public void rejectsUnsupportedSchema() throws Exception {
        JSONObject backup = BackupData.create("0.11", 1234L, new JSONArray(),
                new JSONObject(), new JSONObject(), new JSONArray(), new JSONObject());
        backup.put("schema_version", 99);
        BackupData.validate(backup);
    }
}
