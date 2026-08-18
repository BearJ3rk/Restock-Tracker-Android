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
import android.text.*;
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
    private boolean silencedAlertsExpanded = false;
    private TextView activityText;
    private TextView pokemonName, pokemonFact, monitorState, updateStatus;
    private ImageView pokemonImage;
    private Button learnMoreButton, startStopButton, updateButton;
    private Button bulkToggleButton;
    private EditText productSearch;
    private Spinner productStatusFilter, productRetailerFilter;
    private LinearLayout bulkActions;
    private TextView bulkSelectionText, productCountText;
    private JSONArray products = new JSONArray();
    private JSONObject settings = new JSONObject();
    private final ScheduledExecutorService pokemonScheduler = Executors.newSingleThreadScheduledExecutor();
    private UpdateManager updateManager;
    private String currentPokemonName = "";
    private int currentPokemonId = 0;
    private boolean bulkMode = false;
    private boolean updatingRetailerFilter = false;
    private final HashSet<String> selectedProductKeys = new HashSet<>();

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

        LinearLayout productHeading = row();
        TextView productHeader = text("Products", 18, true);
        productCountText = text("", 12, false);
        productCountText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bulkToggleButton = button("Select", v -> setBulkMode(!bulkMode));
        productHeading.addView(productHeader, new LinearLayout.LayoutParams(0, dp(48), 1));
        productHeading.addView(productCountText, new LinearLayout.LayoutParams(dp(64), dp(48)));
        productHeading.addView(bulkToggleButton, new LinearLayout.LayoutParams(dp(90), dp(48)));
        productsPage.addView(productHeading);

        productSearch = input("Search products or retailers", "");
        productSearch.setSingleLine(true);
        productSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderProducts();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        productsPage.addView(productSearch);

        LinearLayout filterRow = row();
        productStatusFilter = new Spinner(this);
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ProductFilters.STATUS_OPTIONS);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        productStatusFilter.setAdapter(statusAdapter);
        productRetailerFilter = new Spinner(this);
        filterRow.addView(productStatusFilter, new LinearLayout.LayoutParams(0, dp(52), 1));
        filterRow.addView(productRetailerFilter, new LinearLayout.LayoutParams(0, dp(52), 1));
        productsPage.addView(filterRow);

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!updatingRetailerFilter) renderProducts();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        productStatusFilter.setOnItemSelectedListener(filterListener);
        productRetailerFilter.setOnItemSelectedListener(filterListener);

        bulkActions = panel();
        bulkSelectionText = text("0 selected", 14, true);
        bulkActions.addView(bulkSelectionText);
        LinearLayout bulkRow1 = row();
        bulkRow1.addView(button("Pause", v -> bulkSetPaused(true)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        bulkRow1.addView(button("Resume", v -> bulkSetPaused(false)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        bulkActions.addView(bulkRow1);
        LinearLayout bulkRow2 = row();
        bulkRow2.addView(button("Alerts On", v -> bulkSetAlerts(true)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        bulkRow2.addView(button("Alerts Off", v -> bulkSetAlerts(false)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        bulkActions.addView(bulkRow2);
        LinearLayout bulkRow3 = row();
        bulkRow3.addView(button("Set Price", v -> showBulkPriceDialog()),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        bulkRow3.addView(button("Delete", v -> confirmBulkDelete()),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        bulkActions.addView(bulkRow3);
        bulkActions.setVisibility(View.GONE);
        productsPage.addView(bulkActions);

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
        refreshRetailerFilter();
        renderProducts();
        renderInStockPage();
        renderTriggeredPage();
        renderActivityPage();
    }

    private void renderProducts() {
        if (listBox == null) return;
        listBox.removeAllViews();

        String query = productSearch == null ? "" : productSearch.getText().toString();
        String statusFilter = selectedSpinnerValue(productStatusFilter, "All products");
        String retailerFilter = selectedSpinnerValue(productRetailerFilter, "All retailers");
        int visibleCount = 0;

        for(int i=0;i<products.length();i++) {
            JSONObject p=products.optJSONObject(i);
            if(p==null) continue;
            Store.migrate(p);
            final int idx=i;

            boolean hasPriceLimit = !p.optString("alert_max_price", "").isEmpty()
                    || (!p.optBoolean("ignore_global_alert_max_price", false)
                    && !settings.optString("global_alert_max_price", "").isEmpty())
                    || p.optBoolean("alert_at_or_below_msrp", false);
            if (!ProductFilters.matches(
                    p.optString("name", ""),
                    p.optString("url", ""),
                    p.optString("status", ""),
                    p.optBoolean("paused", false),
                    p.optBoolean("alerts_enabled", true),
                    hasPriceLimit,
                    query,
                    statusFilter,
                    retailerFilter)) continue;
            visibleCount++;

            LinearLayout card=panel();

            // Product name + status first, so the most important information is visible
            // without wasting horizontal space.
            if (bulkMode) {
                CheckBox selected = new CheckBox(this);
                selected.setText(p.optString("name", "Product"));
                selected.setTextColor(Color.WHITE);
                String key = productKey(p, idx);
                selected.setChecked(selectedProductKeys.contains(key));
                selected.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) selectedProductKeys.add(key); else selectedProductKeys.remove(key);
                    updateBulkActions();
                });
                card.addView(selected);
            } else {
                TextView name=text(p.optString("name","Product"),16,true);
                card.addView(name);
            }

            String price = p.has("current_price") && !p.optString("current_price","").isEmpty()
                    ? String.format(Locale.US,"$%.2f",p.optDouble("current_price")) : "—";
            String msrp = p.has("msrp") && !p.optString("msrp","").isEmpty()
                    ? String.format(Locale.US,"$%.2f",p.optDouble("msrp")) : "—";

            String status = p.optString("status","Waiting...");
            String compact =
                    status +
                    "\nPrice: " + price + "   MSRP: " + msrp +
                    "\nRetailer: " + ProductFilters.retailer(p.optString("url", ""))
                    + "   Alerts: " + (p.optBoolean("alerts_enabled", true) ? "on" : "off")
                    + "\nChecked: " + formatTime(p.optLong("last_checked",0));

            TextView info=text(compact,13,false);
            card.addView(info);

            if (bulkMode) {
                listBox.addView(card);
                continue;
            }

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

        if (productCountText != null) productCountText.setText(visibleCount + "/" + products.length());
        if (visibleCount == 0) {
            listBox.addView(text("No products match the current search and filters.", 14, false));
        }
        updateBulkActions();
    }

    private String selectedSpinnerValue(Spinner spinner, String fallback) {
        return spinner == null || spinner.getSelectedItem() == null
                ? fallback : String.valueOf(spinner.getSelectedItem());
    }

    private void refreshRetailerFilter() {
        if (productRetailerFilter == null) return;
        String previous = selectedSpinnerValue(productRetailerFilter, "All retailers");
        TreeSet<String> retailers = new TreeSet<>();
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p != null) retailers.add(ProductFilters.retailer(p.optString("url", "")));
        }
        ArrayList<String> options = new ArrayList<>();
        options.add("All retailers");
        options.addAll(retailers);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        updatingRetailerFilter = true;
        productRetailerFilter.setAdapter(adapter);
        int selected = options.indexOf(previous);
        productRetailerFilter.setSelection(selected >= 0 ? selected : 0);
        updatingRetailerFilter = false;
    }

    private String productKey(JSONObject product, int index) {
        String url = product.optString("url", "");
        return url.isEmpty() ? product.optString("name", "Product") + "#" + index : url;
    }

    private ArrayList<JSONObject> selectedProducts() {
        ArrayList<JSONObject> selected = new ArrayList<>();
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product != null && selectedProductKeys.contains(productKey(product, i))) {
                selected.add(product);
            }
        }
        return selected;
    }

    private void setBulkMode(boolean enabled) {
        bulkMode = enabled;
        if (!enabled) selectedProductKeys.clear();
        if (bulkToggleButton != null) bulkToggleButton.setText(enabled ? "Done" : "Select");
        renderProducts();
    }

    private void updateBulkActions() {
        if (bulkActions == null) return;
        bulkActions.setVisibility(bulkMode ? View.VISIBLE : View.GONE);
        if (bulkSelectionText != null) {
            bulkSelectionText.setText(selectedProductKeys.size() + " selected");
        }
    }

    private void bulkSetPaused(boolean paused) {
        ArrayList<JSONObject> selected = selectedProducts();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }
        for (JSONObject product : selected) {
            try {
                product.put("paused", paused);
                product.put("status", paused ? "Paused" : "Waiting...");
            } catch (Exception ignored) {}
        }
        Store.saveProducts(this, products);
        reload();
    }

    private void bulkSetAlerts(boolean enabled) {
        ArrayList<JSONObject> selected = selectedProducts();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }
        for (JSONObject product : selected) {
            try {
                boolean wasEnabled = product.optBoolean("alerts_enabled", true);
                product.put("alerts_enabled", enabled);
                if (enabled && !wasEnabled) {
                    product.put("last_confirmed_in_stock", false);
                    product.put("in_stock_confirmation_count", 0);
                }
            } catch (Exception ignored) {}
        }
        Store.saveProducts(this, products);
        reload();
    }

    private void showBulkPriceDialog() {
        if (selectedProducts().isEmpty()) {
            Toast.makeText(this, "Select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText price = input("Maximum alert price; blank uses global", "");
        price.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
                .setTitle("Set Alert Price")
                .setView(price)
                .setPositiveButton("Apply", (dialog, which) -> {
                    try {
                        String raw = price.getText().toString().trim().replace("$", "");
                        Object value = raw.isEmpty() ? "" : Double.parseDouble(raw);
                        if (value instanceof Double && (Double)value <= 0) {
                            throw new IllegalArgumentException("Price must be positive");
                        }
                        for (JSONObject product : selectedProducts()) {
                            product.put("alert_max_price", value);
                            product.put("ignore_global_alert_max_price", false);
                            product.remove("last_alert_rules_allow");
                        }
                        Store.saveProducts(this, products);
                        reload();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid price", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmBulkDelete() {
        int count = selectedProducts().size();
        if (count == 0) {
            Toast.makeText(this, "Select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete products")
                .setMessage("Delete " + count + " selected product(s)?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    JSONArray kept = new JSONArray();
                    for (int i = 0; i < products.length(); i++) {
                        JSONObject product = products.optJSONObject(i);
                        if (product != null && !selectedProductKeys.contains(productKey(product, i))) {
                            kept.put(product);
                        }
                    }
                    products = kept;
                    selectedProductKeys.clear();
                    Store.saveProducts(this, products);
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            actions.addView(button("Silence…", v -> showSilenceDialog(null, p)),
                    new LinearLayout.LayoutParams(0, dp(48), 1));
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
        TextView heading = text("Alerts", 18, true);
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

        ArrayList<JSONObject> currentAlerts = new ArrayList<>();
        ArrayList<JSONObject> silencedAlerts = new ArrayList<>();
        boolean migrated = false;

        for (int i = triggeredItems.length() - 1; i >= 0; i--) {
            JSONObject alert = triggeredItems.optJSONObject(i);
            if (alert == null) continue;
            String state = alert.optString("alert_state", "");
            if (state.isEmpty()) {
                JSONObject product = findProductByUrl(alert.optString("url", ""));
                boolean alreadySnoozed = product != null
                        && product.optLong("snoozed_until", 0) > System.currentTimeMillis()/1000L;
                try {
                    state = alreadySnoozed ? "silenced" : "active";
                    alert.put("alert_state", state);
                    if (alreadySnoozed) {
                        alert.put("silenced_at", Math.max(
                                alert.optLong("triggered_at", 0),
                                product.optLong("snoozed_until", 0) - 86400));
                        alert.put("silenced_until", product.optLong("snoozed_until", 0));
                    }
                    migrated = true;
                } catch (Exception ignored) {}
            }
            if ("silenced".equals(state)) silencedAlerts.add(alert);
            else currentAlerts.add(alert);
        }

        if (migrated) saveTriggeredItems();

        triggeredPage.addView(text("Current Alerts (" + currentAlerts.size() + ")", 16, true));
        if (currentAlerts.isEmpty()) {
            triggeredPage.addView(text("No current alerts.", 14, false));
        } else {
            for (JSONObject alert : currentAlerts) {
                triggeredPage.addView(buildTriggeredAlertCard(alert, false));
            }
        }

        LinearLayout silencedHeading = row();
        TextView silencedTitle = text("Silenced Alerts (" + silencedAlerts.size() + ")", 16, true);
        silencedHeading.addView(silencedTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        if (!silencedAlerts.isEmpty()) {
            Button toggle = button(silencedAlertsExpanded ? "Hide" : "Show", v -> {
                silencedAlertsExpanded = !silencedAlertsExpanded;
                renderTriggeredPage();
            });
            silencedHeading.addView(toggle, new LinearLayout.LayoutParams(dp(90), dp(48)));
        }
        triggeredPage.addView(silencedHeading);

        if (silencedAlerts.isEmpty()) {
            triggeredPage.addView(text("Silenced alerts will be kept here.", 13, false));
        } else if (!silencedAlertsExpanded) {
            triggeredPage.addView(text("Tap Show to view silenced alert history.", 13, false));
        } else {
            for (JSONObject alert : silencedAlerts) {
                triggeredPage.addView(buildTriggeredAlertCard(alert, true));
            }
        }
    }

    private LinearLayout buildTriggeredAlertCard(JSONObject alert, boolean silenced) {
        String url = alert.optString("url", "");
        JSONObject product = findProductByUrl(url);
        final JSONObject p = product != null ? product : alert;

        LinearLayout card = panel();
        card.addView(text(p.optString("name", "Product"), silenced ? 15 : 16, true));

        String price = p.has("current_price") && !p.optString("current_price","").isEmpty()
                ? String.format(Locale.US, "$%.2f", p.optDouble("current_price")) : "—";
        String details = "Triggered: " + formatTime(alert.optLong("triggered_at", 0))
                + "\nPrice: " + price
                + "   Alert price: " + alertPriceLabel(p);
        String seller = alert.optString("seller", p.optString("last_seller", ""));
        if (!seller.isEmpty()) details += "\nSeller: " + seller;
        String reason = alert.optString("reason", "");
        if (!reason.isEmpty()) details += "\nWhy: " + reason;
        if (silenced) {
            details += "\nSilenced: " + formatTime(alert.optLong("silenced_at", 0));
            if ("until_price_drop".equals(alert.optString("silence_mode", ""))) {
                details += "\nResumes: when the price drops";
            } else if (alert.optLong("silenced_until", 0) > 0) {
                details += "\nResumes: " + formatTime(alert.optLong("silenced_until", 0));
            }
        } else {
            details += "\n" + p.optString("status", "IN STOCK");
        }
        card.addView(text(details, 13, false));

        if (silenced) {
            LinearLayout actions = row();
            actions.addView(button("Open", v -> openProduct(p)),
                    new LinearLayout.LayoutParams(0, dp(50), 1));
            Button priceButton = button("Alert Price", v -> showAlertPriceDialog(product));
            priceButton.setEnabled(product != null);
            actions.addView(priceButton, new LinearLayout.LayoutParams(0, dp(50), 1));
            actions.addView(button("History", v -> showHistory(p)),
                    new LinearLayout.LayoutParams(0, dp(50), 1));
            card.addView(actions);
            return card;
        }

        LinearLayout actions1 = row();
        actions1.addView(button("Open Product", v -> openProduct(p)),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        actions1.addView(button("Silence…", v -> silenceTriggeredAlert(alert, product)),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        card.addView(actions1);

        LinearLayout actions2 = row();
        Button priceButton = button("Alert Price", v -> showAlertPriceDialog(product));
        priceButton.setEnabled(product != null);
        actions2.addView(priceButton, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions2.addView(button("History", v -> showHistory(p)),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        card.addView(actions2);
        return card;
    }

    private void silenceTriggeredAlert(JSONObject alert, JSONObject product) {
        showSilenceDialog(alert, product);
    }

    private void showSilenceDialog(JSONObject alert, JSONObject product) {
        int preferred = settings.optInt("notification_silence_minutes", 1440);
        final int[] selected = {SilenceRules.optionIndex(preferred)};
        new AlertDialog.Builder(this)
                .setTitle("Silence alert")
                .setSingleChoiceItems(SilenceRules.LABELS, selected[0],
                        (dialog, which) -> selected[0] = which)
                .setPositiveButton("Silence", (dialog, which) -> applySilence(
                        alert, product, SilenceRules.MINUTE_OPTIONS[selected[0]]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applySilence(JSONObject alert, JSONObject product, int minutes) {
        long now = System.currentTimeMillis()/1000L;
        long until = SilenceRules.snoozedUntil(now, minutes);
        String mode = SilenceRules.mode(minutes);
        try {
            if (alert != null) {
                alert.put("alert_state", "silenced");
                alert.put("silenced_at", now);
                alert.put("silenced_until", until);
                alert.put("silence_mode", mode);
            }
            if (product != null) {
                product.put("snoozed_until", until);
                product.put("silence_mode", mode);
                product.put("status", "IN STOCK — silenced");
                if (product.has("current_price") && !product.optString("current_price", "").isEmpty()) {
                    product.put("last_alert_price", product.getDouble("current_price"));
                }
                Store.saveProducts(this, products);
            }
            saveTriggeredItems();
            silencedAlertsExpanded = false;
            reload();
        } catch (Exception e) {
            Toast.makeText(this, "Could not silence alert", Toast.LENGTH_SHORT).show();
        }
    }

    private String alertPriceLabel(JSONObject p) {
        String label = "Any price";
        if (p != null && p.has("alert_max_price") && !p.optString("alert_max_price", "").isEmpty()) {
            label = String.format(Locale.US, "$%.2f max", p.optDouble("alert_max_price"));
        } else if (p != null && !p.optBoolean("ignore_global_alert_max_price", false)
                && !settings.optString("global_alert_max_price", "").isEmpty()) {
            label = String.format(Locale.US, "$%.2f global max",
                    settings.optDouble("global_alert_max_price"));
        }
        if (p != null && p.optBoolean("alert_at_or_below_msrp", false)) {
            return "Any price".equals(label) ? "MSRP or less" : label + " + MSRP";
        }
        return label;
    }

    private void showAlertPriceDialog(JSONObject product) {
        if (product == null) {
            Toast.makeText(this, "This product is no longer in the product list", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);
        box.addView(text(
                "Only notify when a freshly detected price is at or below your limit. "
                        + "Leave blank to use the global limit ("
                        + globalAlertPriceLabel() + ").",
                13, false));
        EditText maxPrice = input("Maximum alert price", product.optString("alert_max_price", ""));
        maxPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(maxPrice);
        CheckBox ignoreGlobal = new CheckBox(this);
        ignoreGlobal.setText("No maximum price for this product");
        ignoreGlobal.setChecked(product.optBoolean("ignore_global_alert_max_price", false));
        box.addView(ignoreGlobal);
        CheckBox atMsrp = new CheckBox(this);
        atMsrp.setText("Also require price at or below MSRP");
        atMsrp.setChecked(product.optBoolean("alert_at_or_below_msrp", false));
        box.addView(atMsrp);

        new AlertDialog.Builder(this)
                .setTitle("Alert Price")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String raw = maxPrice.getText().toString().trim().replace("$", "");
                        if (raw.isEmpty()) product.put("alert_max_price", "");
                        else {
                            double value = Double.parseDouble(raw);
                            if (value <= 0) throw new IllegalArgumentException("Price must be positive");
                            product.put("alert_max_price", value);
                        }
                        product.put("ignore_global_alert_max_price",
                                raw.isEmpty() && ignoreGlobal.isChecked());
                        product.put("alert_at_or_below_msrp", atMsrp.isChecked());
                        product.remove("last_alert_rules_allow");
                        Store.saveProducts(this, products);
                        renderTriggeredPage();
                        Toast.makeText(this, "Alert price saved", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid price", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Use Global", (d, w) -> {
                    try {
                        product.put("alert_max_price", "");
                        product.put("ignore_global_alert_max_price", false);
                        product.remove("last_alert_rules_allow");
                        Store.saveProducts(this, products);
                        renderTriggeredPage();
                        Toast.makeText(this, "Using global price limit", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String globalAlertPriceLabel() {
        if (settings.optString("global_alert_max_price", "").isEmpty()) return "not set";
        return String.format(Locale.US, "$%.2f", settings.optDouble("global_alert_max_price"));
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
        ScrollView formScroll=new ScrollView(this);formScroll.addView(box);
        EditText name=input("Product name",p.optString("name",""));box.addView(name);
        EditText url=input("Product URL",p.optString("url",""));box.addView(url);
        EditText msrp=input("MSRP",p.optString("msrp",""));box.addView(msrp);
        EditText interval=input("Check interval seconds",String.valueOf(p.optInt("check_interval",30)));box.addView(interval);
        EditText maxPrice=input("Max alert price (blank uses global)",p.optString("alert_max_price",""));box.addView(maxPrice);
        maxPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox ignoreGlobal=new CheckBox(this);ignoreGlobal.setText("No maximum price for this product");ignoreGlobal.setChecked(p.optBoolean("ignore_global_alert_max_price",false));box.addView(ignoreGlobal);
        CheckBox atMsrp=new CheckBox(this);atMsrp.setText("Alert only at/below MSRP");atMsrp.setChecked(p.optBoolean("alert_at_or_below_msrp",false));box.addView(atMsrp);
        CheckBox verifySeller=new CheckBox(this);verifySeller.setText("Block third-party marketplace sellers");verifySeller.setChecked(p.optBoolean("ignore_third_party",true));box.addView(verifySeller);
        CheckBox alertsEnabled=new CheckBox(this);alertsEnabled.setText("Restock alerts enabled");alertsEnabled.setChecked(p.optBoolean("alerts_enabled",true));box.addView(alertsEnabled);

        new AlertDialog.Builder(this).setTitle(idx>=0?"Edit Product":"Add Product").setView(formScroll)
                .setPositiveButton("Save",(d,w)->{
                    try{
                        JSONObject obj=idx>=0?products.getJSONObject(idx):new JSONObject();
                        obj.put("name",name.getText().toString().trim());
                        obj.put("url",url.getText().toString().trim());
                        obj.put("msrp",parseMaybe(msrp.getText().toString()));
                        obj.put("check_interval",Math.max(15,Integer.parseInt(interval.getText().toString().trim())));
                        obj.put("alert_max_price",parseMaybe(maxPrice.getText().toString()));
                        obj.put("ignore_global_alert_max_price",
                                maxPrice.getText().toString().trim().isEmpty() && ignoreGlobal.isChecked());
                        obj.put("alert_at_or_below_msrp",atMsrp.isChecked());
                        obj.put("ignore_third_party",verifySeller.isChecked());
                        boolean wasAlertsEnabled=obj.optBoolean("alerts_enabled",true);
                        obj.put("alerts_enabled",alertsEnabled.isChecked());
                        if(alertsEnabled.isChecked() && !wasAlertsEnabled){
                            obj.put("last_confirmed_in_stock",false);
                            obj.put("in_stock_confirmation_count",0);
                        }
                        obj.remove("last_alert_rules_allow");
                        obj.remove("last_seller_rules_allow");
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
        ScrollView settingsScroll=new ScrollView(this);settingsScroll.addView(box);
        EditText poke=input("Random Pokémon refresh seconds",String.valueOf(settings.optInt("pokemon_refresh_seconds",60)));box.addView(poke);
        EditText globalMax=input("Default maximum alert price (optional)",settings.optString("global_alert_max_price",""));globalMax.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);box.addView(globalMax);
        EditText confirmations=input("Consecutive in-stock checks required (1-5)",String.valueOf(settings.optInt("in_stock_confirmations_required",2)));confirmations.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);box.addView(confirmations);
        CheckBox sellers=new CheckBox(this);sellers.setText("Verify sellers on marketplace listings");sellers.setChecked(settings.optBoolean("verify_marketplace_sellers",true));box.addView(sellers);
        CheckBox snooze=new CheckBox(this);snooze.setText("Silence opened in-stock items for 24 hours");snooze.setChecked(settings.optBoolean("snooze_after_open_24h",true));box.addView(snooze);
        CheckBox quietHours=new CheckBox(this);quietHours.setText("Delay alerts during quiet hours");quietHours.setChecked(settings.optBoolean("quiet_hours_enabled",false));box.addView(quietHours);
        EditText quietStart=input("Quiet hours start (HH:mm)",settings.optString("quiet_hours_start","22:00"));box.addView(quietStart);
        EditText quietEnd=input("Quiet hours end (HH:mm)",settings.optString("quiet_hours_end","07:00"));box.addView(quietEnd);
        box.addView(text("Notification Silence button duration",13,true));
        Spinner notificationSilence=new Spinner(this);
        ArrayAdapter<String> silenceAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,SilenceRules.LABELS);
        silenceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationSilence.setAdapter(silenceAdapter);
        notificationSilence.setSelection(SilenceRules.optionIndex(settings.optInt("notification_silence_minutes",1440)));
        box.addView(notificationSilence);
        new AlertDialog.Builder(this).setTitle("Settings").setView(settingsScroll).setPositiveButton("Save",(d,w)->{
            try{
                settings.put("pokemon_refresh_seconds",Math.max(30,Integer.parseInt(poke.getText().toString().trim())));
                String globalRaw=globalMax.getText().toString().trim().replace("$","");
                if(globalRaw.isEmpty())settings.put("global_alert_max_price","");
                else{
                    double value=Double.parseDouble(globalRaw);
                    if(value<=0)throw new IllegalArgumentException("Price must be positive");
                    settings.put("global_alert_max_price",value);
                }
                settings.put("in_stock_confirmations_required",Math.max(1,Math.min(5,Integer.parseInt(confirmations.getText().toString().trim()))));
                settings.put("verify_marketplace_sellers",sellers.isChecked());
                settings.put("snooze_after_open_24h",snooze.isChecked());
                String quietStartValue=quietStart.getText().toString().trim();
                String quietEndValue=quietEnd.getText().toString().trim();
                if(QuietHours.parseMinutes(quietStartValue)<0 || QuietHours.parseMinutes(quietEndValue)<0)
                    throw new IllegalArgumentException("Use HH:mm time format");
                settings.put("quiet_hours_enabled",quietHours.isChecked());
                settings.put("quiet_hours_start",quietStartValue);
                settings.put("quiet_hours_end",quietEndValue);
                settings.put("notification_silence_minutes",
                        SilenceRules.MINUTE_OPTIONS[notificationSilence.getSelectedItemPosition()]);
                Store.saveSettings(this,settings);schedulePokemon();
                Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Check prices, confirmations, and HH:mm quiet times",Toast.LENGTH_SHORT).show();}
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
