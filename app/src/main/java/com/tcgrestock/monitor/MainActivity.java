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
    private TextView pokemonName, pokemonFact, monitorState;
    private ImageView pokemonImage;
    private Button learnMoreButton, startStopButton;
    private JSONArray products = new JSONArray();
    private JSONObject settings = new JSONObject();
    private final ScheduledExecutorService pokemonScheduler = Executors.newSingleThreadScheduledExecutor();
    private String currentPokemonName = "";
    private int currentPokemonId = 0;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { runOnUiThread(MainActivity.this::reload); }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        settings = Store.loadSettings(this);
        requestNotificationPermission();
        buildUi();
        reload();
        IntentFilter dataFilter = new IntentFilter("com.tcgrestock.monitor.DATA_CHANGED");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dataReceiver, dataFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dataReceiver, dataFilter);
        }
        schedulePokemon();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(12),dp(12),dp(24));
        applyBackground();
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout logos = row();
        ImageView pLogo = new ImageView(this);
        pLogo.setImageResource(com.tcgrestock.monitor.R.drawable.pokemon_logo);
        pLogo.setAdjustViewBounds(true); pLogo.setMaxHeight(dp(70));
        ImageView oLogo = new ImageView(this);
        oLogo.setImageResource(com.tcgrestock.monitor.R.drawable.one_piece_logo);
        oLogo.setAdjustViewBounds(true); oLogo.setMaxHeight(dp(70));
        logos.addView(pLogo,new LinearLayout.LayoutParams(0,dp(72),1));
        logos.addView(oLogo,new LinearLayout.LayoutParams(0,dp(72),1));
        root.addView(logos);

        TextView title = text("TCG RESTOCK MONITOR  MV0.01",20,true);
        title.setGravity(Gravity.CENTER); root.addView(title);

        LinearLayout pokeCard = panel();
        LinearLayout pokeRow = row();
        pokemonImage = new ImageView(this); pokemonImage.setAdjustViewBounds(true);
        pokeRow.addView(pokemonImage,new LinearLayout.LayoutParams(dp(120),dp(120)));
        LinearLayout facts = new LinearLayout(this); facts.setOrientation(LinearLayout.VERTICAL);
        pokemonName=text("Loading Pokémon…",17,true); facts.addView(pokemonName);
        pokemonFact=text("Loading a fun fact…",14,false); facts.addView(pokemonFact);
        learnMoreButton = button("Learn More", v -> openPokemon());
        facts.addView(learnMoreButton);
        pokeRow.addView(facts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        pokeCard.addView(pokeRow); root.addView(pokeCard);

        LinearLayout controls = panel();
        monitorState=text("Monitoring stopped",15,true); controls.addView(monitorState);
        LinearLayout buttons=row();
        startStopButton=button("Start Monitoring",v->toggleMonitoring());
        buttons.addView(startStopButton,new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(button("Check Now",v->checkNow()),new LinearLayout.LayoutParams(0,dp(48),1));
        buttons.addView(button("Add Product",v->editProduct(-1)),new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(buttons);
        LinearLayout buttons2=row();
        buttons2.addView(button("Settings",v->showSettings()),new LinearLayout.LayoutParams(0,dp(44),1));
        buttons2.addView(button("Data Folder",v->showDataPath()),new LinearLayout.LayoutParams(0,dp(44),1));
        controls.addView(buttons2);
        root.addView(controls);

        TextView productHeader=text("Products",18,true); root.addView(productHeader);
        listBox=new LinearLayout(this);listBox.setOrientation(LinearLayout.VERTICAL);root.addView(listBox);
    }

    private void reload() {
        products=Store.loadProducts(this);
        settings=Store.loadSettings(this);
        boolean enabled=settings.optBoolean("monitor_enabled",false);
        monitorState.setText(enabled ? "Monitoring active — foreground service" : "Monitoring stopped");
        startStopButton.setText(enabled ? "Stop Monitoring" : "Start Monitoring");
        renderProducts();
    }

    private void renderProducts() {
        listBox.removeAllViews();
        for(int i=0;i<products.length();i++) {
            JSONObject p=products.optJSONObject(i); if(p==null)continue;Store.migrate(p);
            final int idx=i;
            LinearLayout card=panel();
            TextView name=text(p.optString("name","Product"),16,true);card.addView(name);
            String price = p.has("current_price") && !p.optString("current_price","").isEmpty()
                    ? String.format(Locale.US,"$%.2f",p.optDouble("current_price")) : "—";
            String msrp = p.has("msrp") && !p.optString("msrp","").isEmpty()
                    ? String.format(Locale.US,"$%.2f",p.optDouble("msrp")) : "—";
            TextView info=text(p.optString("status","Waiting...")+"\nPrice: "+price+"   MSRP: "+msrp+
                    "\nLast checked: "+formatTime(p.optLong("last_checked",0)),13,false);
            card.addView(info);
            LinearLayout actions=row();
            actions.addView(button("Open",v->openProduct(p)),new LinearLayout.LayoutParams(0,dp(42),1));
            actions.addView(button(p.optBoolean("paused",false)?"Resume":"Pause",v->togglePause(idx)),new LinearLayout.LayoutParams(0,dp(42),1));
            actions.addView(button("Edit",v->editProduct(idx)),new LinearLayout.LayoutParams(0,dp(42),1));
            actions.addView(button("History",v->showHistory(p)),new LinearLayout.LayoutParams(0,dp(42),1));
            card.addView(actions);
            card.setOnLongClickListener(v->{ confirmDelete(idx); return true; });
            listBox.addView(card);
        }
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
    private Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);return b;}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);return e;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private String formatTime(long ts){if(ts<=0)return "—";return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(ts*1000));}
    private String titleCase(String s){StringBuilder b=new StringBuilder();for(String x:s.split(" ")){if(x.length()>0)b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)).append(" ");}return b.toString().trim();}
    private void applyBackground(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(40,45,53),Color.rgb(24,29,35)});root.setBackground(g);}

    @Override protected void onDestroy() {
        try{unregisterReceiver(dataReceiver);}catch(Exception ignored){}
        pokemonScheduler.shutdownNow();
        super.onDestroy();
    }
}
