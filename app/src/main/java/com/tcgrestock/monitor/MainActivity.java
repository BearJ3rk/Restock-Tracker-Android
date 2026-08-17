package com.tcgrestock.monitor;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private LinearLayout root, listBox;
    private LinearLayout screen, contentHost, productsPage, inStockPage, triggeredPage, activityPage, settingsPage;
    private JSONArray triggeredItems = new JSONArray();
    private TextView activityText;
    private TextView pokemonName, pokemonFact, monitorState, updateStatus;
    private ImageView pokemonImage;
    private Button learnMoreButton, startStopButton, updateButton;
    private JSONArray products = new JSONArray();
    private JSONObject settings = new JSONObject();
    private final ScheduledExecutorService pokemonScheduler = Executors.newSingleThreadScheduledExecutor();
    private UpdateManager updateManager;
    private String currentPokemonName = "";
    private int currentPokemonId = 0;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { runOnUiThread(MainActivity.this::reload); }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        settings = Store.loadSettings(this);
        requestNotificationPermission();
        updateManager = new UpdateManager(this);
        buildUi();
        reload();
        IntentFilter dataFilter = new IntentFilter("com.tcgrestock.monitor.DATA_CHANGED");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dataReceiver, dataFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dataReceiver, dataFilter);
        }
        schedulePokemon();
        handleAlertIntent(getIntent());
        checkForUpdates(false);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAlertIntent(intent);
    }

    private void handleAlertIntent(Intent intent) {
        if (intent == null) return;
        String alertUrl = intent.getStringExtra("alert_url");
        if ((alertUrl != null && !alertUrl.isEmpty())
                || intent.getBooleanExtra("open_triggered_alerts", false)) {
            showPage("triggered");
            renderTriggeredPage();
        }
    }

    private void buildUi() {
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable screenBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(40,45,53), Color.rgb(24,29,35)}
        );
        screen.setBackground(screenBg);

        // Compact top header.
        LinearLayout header = row();
        header.setPadding(dp(10), dp(8), dp(10), dp(4));
        TextView title = text("TCG RESTOCK MONITOR", 18, true);
        TextView version = text("MV" + BuildConfig.VERSION_NAME, 12, false);
        version.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        header.addView(version, new LinearLayout.LayoutParams(dp(74), dp(44)));
        screen.addView(header);

        // Main page host.
        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        screen.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        buildProductsPage();
        buildInStockPage();
        buildTriggeredPage();
        buildActivityPage();
        buildSettingsPage();

        // Bottom navigation stays reachable by thumb.
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(4), dp(4), dp(4), dp(6));
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(Color.rgb(31,35,42));
        navBg.setStroke(dp(1), Color.rgb(72,80,90));
        nav.setBackground(navBg);

        Button productsTab = button("Products", v -> showPage("products"));
        Button stockTab = button("In Stock", v -> showPage("stock"));
        Button triggeredTab = button("Alerts", v -> showPage("triggered"));
        Button activityTab = button("Activity", v -> showPage("activity"));
        Button settingsTab = button("Settings", v -> showPage("settings"));

        productsTab.setMinHeight(dp(58));
        stockTab.setMinHeight(dp(58));
        triggeredTab.setMinHeight(dp(58));
        activityTab.setMinHeight(dp(58));
        settingsTab.setMinHeight(dp(58));

        nav.addView(productsTab, new LinearLayout.LayoutParams(0, dp(60), 1));
        nav.addView(stockTab, new LinearLayout.LayoutParams(0, dp(60), 1));
        nav.addView(triggeredTab, new LinearLayout.LayoutParams(0, dp(60), 1));
        nav.addView(activityTab, new LinearLayout.LayoutParams(0, dp(60), 1));
        nav.addView(settingsTab, new LinearLayout.LayoutParams(0, dp(60), 1));

        screen.addView(nav);
        setContentView(screen);

        showPage("products");
    }

    private void buildProductsPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        productsPage = new LinearLayout(this);
        productsPage.setOrientation(LinearLayout.VERTICAL);
        productsPage.setPadding(dp(10), dp(2), dp(10), dp(10));
        scroll.addView(productsPage);

        // Random Pokémon card.
        LinearLayout pokeCard = panel();
        LinearLayout pokeTop = row();
        TextView pokeHeading = text("Random Pokémon", 15, true);
        Button collapsePokemon = button("Hide", v -> {
            View details = pokeCard.findViewWithTag("pokemon_details");
            if (details != null) {
                boolean visible = details.getVisibility() == View.VISIBLE;
                details.setVisibility(visible ? View.GONE : View.VISIBLE);
                ((Button)v).setText(visible ? "Show" : "Hide");
            }
        });
        pokeTop.addView(pokeHeading, new LinearLayout.LayoutParams(0, dp(46), 1));
        pokeTop.addView(collapsePokemon, new LinearLayout.LayoutParams(dp(86), dp(46)));
        pokeCard.addView(pokeTop);

        LinearLayout pokeRow = row();
        pokeRow.setTag("pokemon_details");
        pokemonImage = new ImageView(this);
        pokemonImage.setAdjustViewBounds(true);
        pokeRow.addView(pokemonImage, new LinearLayout.LayoutParams(dp(92), dp(92)));

        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.VERTICAL);
        pokemonName = text("Loading Pokémon…", 16, true);
        pokemonFact = text("Loading a fun fact…", 13, false);
        learnMoreButton = button("Learn More", v -> openPokemon());
        facts.addView(pokemonName);
        facts.addView(pokemonFact);
        facts.addView(learnMoreButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        pokeRow.addView(facts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pokeCard.addView(pokeRow);
        productsPage.addView(pokeCard);

        // Monitor state + quick actions.
        LinearLayout monitorCard = panel();
        monitorState = text("Monitoring stopped", 15, true);
        monitorCard.addView(monitorState);

        LinearLayout quick = row();
        startStopButton = button("Start", v -> toggleMonitoring());
        Button checkButton = button("Check", v -> checkNow());
        Button addButton = button("+ Product", v -> editProduct(-1));
        quick.addView(startStopButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        quick.addView(checkButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        quick.addView(addButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        monitorCard.addView(quick);
        productsPage.addView(monitorCard);

        TextView productHeader = text("Products", 18, true);
        productsPage.addView(productHeader);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        productsPage.addView(listBox);

        contentHost.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setTag("products_page");
    }

    private void buildInStockPage() {
        ScrollView scroll = new ScrollView(this);
        inStockPage = new LinearLayout(this);
        inStockPage.setOrientation(LinearLayout.VERTICAL);
        inStockPage.setPadding(dp(10), dp(6), dp(10), dp(10));
        scroll.addView(inStockPage);
        scroll.setTag("stock_page");
        contentHost.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildTriggeredPage() {
        ScrollView scroll = new ScrollView(this);
        triggeredPage = new LinearLayout(this);
        triggeredPage.setOrientation(LinearLayout.VERTICAL);
        triggeredPage.setPadding(dp(10), dp(6), dp(10), dp(10));
        scroll.addView(triggeredPage);
        scroll.setTag("triggered_page");
        contentHost.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildActivityPage() {
        ScrollView scroll = new ScrollView(this);
        activityPage = new LinearLayout(this);
        activityPage.setOrientation(LinearLayout.VERTICAL);
        activityPage.setPadding(dp(10), dp(6), dp(10), dp(10));

        TextView heading = text("Activity", 18, true);
        activityPage.addView(heading);

        activityText = text("No activity yet.", 13, false);
        activityText.setTextIsSelectable(true);
        activityPage.addView(activityText);

        scroll.addView(activityPage);
        scroll.setTag("activity_page");
        contentHost.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        settingsPage = new LinearLayout(this);
        settingsPage.setOrientation(LinearLayout.VERTICAL);
        settingsPage.setPadding(dp(10), dp(6), dp(10), dp(10));

        TextView heading = text("Settings", 18, true);
        settingsPage.addView(heading);

        LinearLayout card = panel();
        Button settingsButton = button("App Settings", v -> showSettings());
        Button dataButton = button("App Data Location", v -> showDataPath());
        Button backupButton = button("Refresh Product View", v -> reload());
        updateButton = button("Check for App Updates", v -> checkForUpdates(true));
        card.addView(settingsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        card.addView(dataButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        card.addView(backupButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        card.addView(updateButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        updateStatus = text("App version MV" + BuildConfig.VERSION_NAME, 12, false);
        card.addView(updateStatus);
        settingsPage.addView(card);

        TextView tips = text(
                "Tip: Android may restrict background activity on some phones. "
                + "If monitoring stops unexpectedly, exclude this app from battery optimization.",
                13, false
        );
        settingsPage.addView(tips);

        scroll.addView(settingsPage);
        scroll.setTag("settings_page");
        contentHost.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showPage(String page) {
        for (int i = 0; i < contentHost.getChildCount(); i++) {
            View child = contentHost.getChildAt(i);
            child.setVisibility(View.GONE);
        }

        String wanted = page + "_page";
        for (int i = 0; i < contentHost.getChildCount(); i++) {
            View child = contentHost.getChildAt(i);
            if (wanted.equals(child.getTag())) {
                child.setVisibility(View.VISIBLE);
                break;
            }
        }

        if ("stock".equals(page)) renderInStockPage();
        if ("triggered".equals(page)) renderTriggeredPage();
        if ("activity".equals(page)) renderActivityPage();
    }

    private void reload() {
        products=Store.loadProducts(this);
        settings=Store.loadSettings(this);
        boolean enabled=settings.optBoolean("monitor_enabled",false);
        monitorState.setText(enabled ? "Monitoring active — foreground service" : "Monitoring stopped");
        startStopButton.setText(enabled ? "Stop" : "Start");
        renderProducts();
        renderInStockPage();
        renderTriggeredPage();
        renderActivityPage();
    }

    private void renderProducts() {
        listBox.removeAllViews();

        for(int i=0;i<products.length();i++) {
            JSONObject p=products.optJSONObject(i);
            if(p==null) continue;
            Store.migrate(p);
            final int idx=i;

            LinearLayout card=panel();

            // Product name + status first, so the most important information is visible
            // without wasting horizontal space.
            TextView name=text(p.optString("name","Product"),16,true);
            card.addView(name);

            String price = p.has("current_price") && !p.optString("current_price","").isEmpty()
                    ? String.format(Locale.US,"$%.2f",p.optDouble("current_price")) : "—";
            String msrp = p.has("msrp") && !p.optString("msrp","").isEmpty()
                    ? String.format(Locale.US,"$%.2f",p.optDouble("msrp")) : "—";

            String status = p.optString("status","Waiting...");
            String compact =
                    status +
                    "\nPrice: " + price + "   MSRP: " + msrp +
                    "\nChecked: " + formatTime(p.optLong("last_checked",0));

            TextView info=text(compact,13,false);
            card.addView(info);

            // Two rows of larger buttons are easier to use on narrow screens
            // than four tiny buttons squeezed onto one line.
            LinearLayout actions1=row();
            Button open=button("Open",v->openProduct(p));
            Button pause=button(p.optBoolean("paused",false)?"Resume":"Pause",v->togglePause(idx));
            open.setMinHeight(dp(48));
            pause.setMinHeight(dp(48));
            actions1.addView(open,new LinearLayout.LayoutParams(0,dp(50),1));
            actions1.addView(pause,new LinearLayout.LayoutParams(0,dp(50),1));
            card.addView(actions1);

            LinearLayout actions2=row();
            Button edit=button("Edit",v->editProduct(idx));
            Button hist=button("History",v->showHistory(p));
            edit.setMinHeight(dp(48));
            hist.setMinHeight(dp(48));
            actions2.addView(edit,new LinearLayout.LayoutParams(0,dp(50),1));
            actions2.addView(hist,new LinearLayout.LayoutParams(0,dp(50),1));
            card.addView(actions2);

            card.setOnLongClickListener(v->{ confirmDelete(idx); return true; });
            listBox.addView(card);
        }
    }

    private void renderInStockPage() {
        if (inStockPage == null) return;
        inStockPage.removeAllViews();
        TextView heading = text("In Stock", 18, true);
        inStockPage.addView(heading);

        int count = 0;
        for (int i=0;i<products.length();i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            String status = p.optString("status","");
            if (!status.startsWith("IN STOCK")) continue;
            count++;

            LinearLayout card = panel();
            card.addView(text(p.optString("name","Product"), 16, true));

            String price = p.has("current_price") && !p.optString("current_price","").isEmpty()
                    ? String.format(Locale.US,"$%.2f",p.optDouble("current_price")) : "—";
            card.addView(text(status + "\nPrice: " + price, 13, false));

            LinearLayout actions = row();
            actions.addView(button("Open", v -> openProduct(p)),
                    new LinearLayout.LayoutParams(0, dp(48), 1));
            actions.addView(button("Silence 24h", v -> {
                try {
                    p.put("snoozed_until", System.currentTimeMillis()/1000L + 86400);
                    if (p.has("current_price")) p.put("last_alert_price", p.getDouble("current_price"));
                    Store.saveProducts(this, products);
                    reload();
                } catch (Exception ignored) {}
            }), new LinearLayout.LayoutParams(0, dp(48), 1));
            card.addView(actions);
            inStockPage.addView(card);
        }

        if (count == 0) {
            inStockPage.addView(text("No products are currently detected in stock.", 14, false));
        }
    }

    private void renderTriggeredPage() {
        if (triggeredPage == null) return;
        triggeredPage.removeAllViews();

        LinearLayout headingRow = row();
        TextView heading = text("Triggered Alerts", 18, true);
        Button clear = button("Clear", v -> {
            triggeredItems = new JSONArray();
            saveTriggeredItems();
            renderTriggeredPage();
        });
        headingRow.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        headingRow.addView(clear, new LinearLayout.LayoutParams(dp(90), dp(48)));
        triggeredPage.addView(headingRow);

        loadTriggeredItems();

        if (triggeredItems.length() == 0) {
            triggeredPage.addView(text(
                    "No triggered alerts yet. When a restock alert fires, the product will appear here.",
                    14, false));
            return;
        }

        for (int i = triggeredItems.length() - 1; i >= 0; i--) {
            JSONObject alert = triggeredItems.optJSONObject(i);
            if (alert == null) continue;

            String url = alert.optString("url", "");
            JSONObject product = findProductByUrl(url);
            final JSONObject p = product != null ? product : alert;
            final String productUrl = url;

            LinearLayout card = panel();
            card.addView(text(p.optString("name", "Product"), 16, true));

            String price = p.has("current_price") && !p.optString("current_price","").isEmpty()
                    ? String.format(Locale.US, "$%.2f", p.optDouble("current_price")) : "—";
            card.addView(text(
                    "Triggered: " + formatTime(alert.optLong("triggered_at", 0))
                    + "\n" + p.optString("status","IN STOCK")
                    + "\nPrice: " + price,
                    13, false));

            LinearLayout actions1 = row();
            actions1.addView(button("Open Product", v -> openProduct(p)),
                    new LinearLayout.LayoutParams(0, dp(50), 1));
            actions1.addView(button("Silence 24h", v -> {
                try {
                    p.put("snoozed_until", System.currentTimeMillis()/1000L + 86400);
                    if (p.has("current_price")) p.put("last_alert_price", p.getDouble("current_price"));
                    Store.saveProducts(this, products);
                    reload();
                } catch (Exception ignored) {}
            }), new LinearLayout.LayoutParams(0, dp(50), 1));
            card.addView(actions1);

            LinearLayout actions2 = row();
            actions2.addView(button("Go to Product", v -> goToTriggeredProduct(productUrl)),
                    new LinearLayout.LayoutParams(0, dp(50), 1));
            actions2.addView(button("History", v -> showHistory(p)),
                    new LinearLayout.LayoutParams(0, dp(50), 1));
            card.addView(actions2);

            triggeredPage.addView(card);
        }
    }

    private JSONObject findProductByUrl(String url) {
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p != null && url.equals(p.optString("url",""))) return p;
        }
        return null;
    }

    private void goToTriggeredProduct(String url) {
        showPage("products");
        JSONObject p = findProductByUrl(url);
        if (p != null) {
            Toast.makeText(this, "Triggered item: " + p.optString("name","Product"),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadTriggeredItems() {
        try {
            String raw = getSharedPreferences("alerts", MODE_PRIVATE)
                    .getString("triggered_items", "[]");
            triggeredItems = new JSONArray(raw);
        } catch (Exception e) {
            triggeredItems = new JSONArray();
        }
    }

    private void saveTriggeredItems() {
        getSharedPreferences("alerts", MODE_PRIVATE)
                .edit()
                .putString("triggered_items", triggeredItems.toString())
                .apply();
    }

    private void renderActivityPage() {
        if (activityText == null) return;
        StringBuilder b = new StringBuilder();
        for (int i = products.length() - 1; i >= 0; i--) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            long checked = p.optLong("last_checked",0);
            if (checked <= 0) continue;
            b.append(formatTime(checked))
                    .append("  ")
                    .append(p.optString("name","Product"))
                    .append("\n")
                    .append(p.optString("status","Waiting..."))
                    .append("\n\n");
            if (b.length() > 6000) break;
        }
        activityText.setText(b.length() == 0 ? "No activity yet." : b.toString());
    }

    private void toggleMonitoring() {
        boolean on=settings.optBoolean("monitor_enabled",false);
        try { settings.put("monitor_enabled",!on);Store.saveSettings(this,settings); }catch(Exception ignored){}
        if(on) stopService(new Intent(this,MonitorService.class).setAction(MonitorService.ACTION_STOP));
        else startForegroundService(new Intent(this,MonitorService.class).setAction(MonitorService.ACTION_START));
        reload();
    }

    private void checkNow() {
        for(int i=0;i<products.length();i++) try { products.getJSONObject(i).put("next_check",0); }catch(Exception ignored){}
        Store.saveProducts(this,products);
        if(!settings.optBoolean("monitor_enabled",false)) {
            startForegroundService(new Intent(this,MonitorService.class).setAction(MonitorService.ACTION_START));
            try{settings.put("monitor_enabled",true);Store.saveSettings(this,settings);}catch(Exception ignored){}
        }
        Toast.makeText(this,"Check requested",Toast.LENGTH_SHORT).show();
    }

    private void openProduct(JSONObject p) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(p.optString("url",""))));
            long now=System.currentTimeMillis()/1000L;
            if(settings.optBoolean("snooze_after_open_24h",true) && p.optString("status","").startsWith("IN STOCK")) {
                p.put("snoozed_until",now+86400);
                if(p.has("current_price"))p.put("last_alert_price",p.getDouble("current_price"));
                Store.saveProducts(this,products);reload();
            }
        } catch(Exception e){ Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show(); }
    }

    private void togglePause(int idx) {
        try{
            JSONObject p=products.getJSONObject(idx);boolean paused=!p.optBoolean("paused",false);
            p.put("paused",paused);p.put("status",paused?"Paused":"Waiting...");
            Store.saveProducts(this,products);reload();
        }catch(Exception ignored){}
    }

    private void confirmDelete(int idx) {
        JSONObject p=products.optJSONObject(idx);
        new AlertDialog.Builder(this).setTitle("Delete product")
                .setMessage(p==null?"Delete this product?":"Delete "+p.optString("name","product")+"?")
                .setPositiveButton("Delete",(d,w)->{
                    JSONArray n=new JSONArray();
                    for(int i=0;i<products.length();i++)if(i!=idx)n.put(products.opt(i));
                    products=n;Store.saveProducts(this,products);reload();
                }).setNegativeButton("Cancel",null).show();
    }

    private void editProduct(int idx) {
        JSONObject p=idx>=0?products.optJSONObject(idx):new JSONObject();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(8),dp(18),0);
        EditText name=input("Product name",p.optString("name",""));box.addView(name);
        EditText url=input("Product URL",p.optString("url",""));box.addView(url);
        EditText msrp=input("MSRP",p.optString("msrp",""));box.addView(msrp);
        EditText interval=input("Check interval seconds",String.valueOf(p.optInt("check_interval",30)));box.addView(interval);
        EditText maxPrice=input("Max alert price (optional)",p.optString("alert_max_price",""));box.addView(maxPrice);
        CheckBox atMsrp=new CheckBox(this);atMsrp.setText("Alert only at/below MSRP");atMsrp.setChecked(p.optBoolean("alert_at_or_below_msrp",false));box.addView(atMsrp);

        new AlertDialog.Builder(this).setTitle(idx>=0?"Edit Product":"Add Product").setView(box)
                .setPositiveButton("Save",(d,w)->{
                    try{
                        JSONObject obj=idx>=0?products.getJSONObject(idx):new JSONObject();
                        obj.put("name",name.getText().toString().trim());
                        obj.put("url",url.getText().toString().trim());
                        obj.put("msrp",parseMaybe(msrp.getText().toString()));
                        obj.put("check_interval",Math.max(15,Integer.parseInt(interval.getText().toString().trim())));
                        obj.put("alert_max_price",parseMaybe(maxPrice.getText().toString()));
                        obj.put("alert_at_or_below_msrp",atMsrp.isChecked());
                        Store.migrate(obj);
                        if(idx<0)products.put(obj);
                        Store.saveProducts(this,products);reload();
                    }catch(Exception e){Toast.makeText(this,"Could not save product",Toast.LENGTH_SHORT).show();}
                }).setNegativeButton("Cancel",null).show();
    }

    private Object parseMaybe(String s) {
        s=s.trim().replace("$","");
        if(s.isEmpty())return "";
        try{return Double.parseDouble(s);}catch(Exception e){return "";}
    }

    private void showHistory(JSONObject p) {
        JSONObject hist=Store.loadHistory(this);JSONArray arr=hist.optJSONArray(p.optString("url",""));
        StringBuilder b=new StringBuilder();
        if(arr!=null)for(int i=Math.max(0,arr.length()-30);i<arr.length();i++){
            JSONObject r=arr.optJSONObject(i);if(r!=null)b.append(formatTime(r.optLong("time",0))).append("  ")
                    .append(String.format(Locale.US,"$%.2f",r.optDouble("price"))).append("  ")
                    .append(r.optString("status","")).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("Price History").setMessage(b.length()==0?"No price history yet.":b.toString()).setPositiveButton("Close",null).show();
    }

    private void showSettings() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(8),dp(18),0);
        EditText poke=input("Random Pokémon refresh seconds",String.valueOf(settings.optInt("pokemon_refresh_seconds",60)));box.addView(poke);
        CheckBox snooze=new CheckBox(this);snooze.setText("Silence opened in-stock items for 24 hours");snooze.setChecked(settings.optBoolean("snooze_after_open_24h",true));box.addView(snooze);
        new AlertDialog.Builder(this).setTitle("Settings").setView(box).setPositiveButton("Save",(d,w)->{
            try{
                settings.put("pokemon_refresh_seconds",Math.max(30,Integer.parseInt(poke.getText().toString().trim())));
                settings.put("snooze_after_open_24h",snooze.isChecked());
                Store.saveSettings(this,settings);schedulePokemon();
            }catch(Exception ignored){}
        }).setNegativeButton("Cancel",null).show();
    }

    private void showDataPath() {
        new AlertDialog.Builder(this).setTitle("App data location")
                .setMessage(getFilesDir().getAbsolutePath()+"\n\nAndroid keeps this private to the app. Use Android backup/export later to move it.")
                .setPositiveButton("OK",null).show();
    }

    private void checkForUpdates(boolean userInitiated) {
        if (!userInitiated) {
            long lastCheck = getSharedPreferences("updater", MODE_PRIVATE)
                    .getLong("last_check", 0L);
            if (System.currentTimeMillis() - lastCheck < TimeUnit.HOURS.toMillis(24)) return;
        }
        getSharedPreferences("updater", MODE_PRIVATE).edit()
                .putLong("last_check", System.currentTimeMillis()).apply();
        updateButton.setEnabled(false);
        updateManager.check(updateCallback(userInitiated));
    }

    private UpdateManager.Callback updateCallback(boolean userInitiated) {
        return new UpdateManager.Callback() {
            @Override public void onStatus(String message) {
                updateButton.setEnabled(true);
                updateStatus.setText(message);
            }

            @Override public void onUpdateAvailable(UpdateManager.ReleaseInfo release) {
                updateButton.setEnabled(true);
                updateStatus.setText("MV" + release.version + " is available");
                String notes = release.notes.trim();
                String message = "Installed: MV" + BuildConfig.VERSION_NAME
                        + "\nAvailable: MV" + release.version;
                if (!notes.isEmpty()) message += "\n\n" + notes;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("App update available")
                        .setMessage(message)
                        .setPositiveButton("Download & Install", (dialog, which) -> {
                            updateButton.setEnabled(false);
                            updateManager.downloadAndInstall(release, updateCallback(true));
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }

            @Override public void onUpToDate() {
                updateButton.setEnabled(true);
                updateStatus.setText("App is up to date — MV" + BuildConfig.VERSION_NAME);
                if (userInitiated) {
                    Toast.makeText(MainActivity.this, "You have the latest version", Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onError(String message) {
                updateButton.setEnabled(true);
                updateStatus.setText("Update check unavailable");
                if (userInitiated) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Could not update")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                }
            }
        };
    }

    private void schedulePokemon() {
        pokemonScheduler.schedule(this::loadPokemon,0,TimeUnit.SECONDS);
    }

    private void loadPokemon() {
        try{
            int id=1+new Random().nextInt(1025);
            String data=Detector.fetch("https://pokeapi.co/api/v2/pokemon/"+id);
            JSONObject j=new JSONObject(data);String name=j.optString("name","pokemon").replace("-"," ");
            String species=Detector.fetch("https://pokeapi.co/api/v2/pokemon-species/"+id);
            JSONObject s=new JSONObject(species);String fact="No fun fact available.";
            JSONArray entries=s.optJSONArray("flavor_text_entries");
            if(entries!=null)for(int i=0;i<entries.length();i++){
                JSONObject e=entries.optJSONObject(i);
                if(e!=null && "en".equals(e.optJSONObject("language").optString("name"))){
                    fact=e.optString("flavor_text","").replace("\n"," ").replace("\f"," ");break;
                }
            }
            URL u=new URL("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/"+id+".png");
            Bitmap bmp=BitmapFactory.decodeStream(u.openStream());
            String n=titleCase(name),f=fact;int pid=id;
            runOnUiThread(()->{
                currentPokemonName=n;currentPokemonId=pid;pokemonName.setText(n+"  #"+pid);pokemonFact.setText(f);pokemonImage.setImageBitmap(bmp);
            });
        }catch(Exception ignored){}
        int sec=settings.optInt("pokemon_refresh_seconds",60);
        pokemonScheduler.schedule(this::loadPokemon,Math.max(30,sec),TimeUnit.SECONDS);
    }

    private void openPokemon() {
        if(currentPokemonName.isEmpty())return;
        String slug=currentPokemonName.replace(" ","_");
        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://bulbapedia.bulbagarden.net/wiki/"+slug+"_(Pokémon)")));
    }

    private void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},47);
    }

    private LinearLayout panel() {
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(43,45,49));g.setCornerRadius(dp(12));p.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(6),0,dp(6));p.setLayoutParams(lp);return p;
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);if(bold)t.setTypeface(null,1);t.setPadding(dp(4),dp(4),dp(4),dp(4));return t;}
    private Button button(String s,View.OnClickListener l){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(dp(46));
        b.setPadding(dp(8),dp(4),dp(8),dp(4));
        b.setOnClickListener(l);
        return b;
    }
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);return e;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private String formatTime(long ts){if(ts<=0)return "—";return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(ts*1000));}
    private String titleCase(String s){StringBuilder b=new StringBuilder();for(String x:s.split(" ")){if(x.length()>0)b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)).append(" ");}return b.toString().trim();}
    private void applyBackground(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(40,45,53),Color.rgb(24,29,35)});root.setBackground(g);}

    @Override protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.resumePendingInstall(updateCallback(true));
    }

    @Override protected void onDestroy() {
        try{unregisterReceiver(dataReceiver);}catch(Exception ignored){}
        pokemonScheduler.shutdownNow();
        if (updateManager != null) updateManager.shutdown();
        super.onDestroy();
    }
}
