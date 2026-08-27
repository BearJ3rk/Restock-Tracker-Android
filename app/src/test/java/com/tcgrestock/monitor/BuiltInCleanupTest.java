package com.tcgrestock.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Set;

public class BuiltInCleanupTest {
    @Test public void removesExactBuiltInsAndPreservesCustomProducts() throws Exception {
        Set<String> builtIns = BuiltInCleanup.urlSet(new JSONArray().put("https://store.test/built-in"));
        JSONArray products = new JSONArray()
                .put(new JSONObject().put("name", "Bundled").put("url", "https://store.test/built-in/"))
                .put(new JSONObject().put("name", "Mine").put("url", "https://store.test/custom"));

        JSONArray kept = BuiltInCleanup.withoutProducts(products, builtIns);

        assertEquals(1, kept.length());
        assertEquals("Mine", kept.getJSONObject(0).getString("name"));
    }

    @Test public void removesBuiltInHistoryAndAlerts() throws Exception {
        Set<String> builtIns = BuiltInCleanup.urlSet(new JSONArray().put("https://store.test/built-in"));
        JSONObject history = new JSONObject()
                .put("https://store.test/built-in", new JSONArray().put(1))
                .put("https://store.test/custom", new JSONArray().put(2));
        JSONArray alerts = new JSONArray()
                .put(new JSONObject().put("url", "https://store.test/built-in"))
                .put(new JSONObject().put("url", "https://store.test/custom"));

        JSONObject keptHistory = BuiltInCleanup.withoutHistory(history, builtIns);
        JSONArray keptAlerts = BuiltInCleanup.withoutAlerts(alerts, builtIns);

        assertFalse(keptHistory.has("https://store.test/built-in"));
        assertTrue(keptHistory.has("https://store.test/custom"));
        assertEquals(1, keptAlerts.length());
        assertEquals("https://store.test/custom", keptAlerts.getJSONObject(0).getString("url"));
    }
}
