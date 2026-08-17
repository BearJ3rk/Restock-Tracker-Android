TCG Restock Monitor MV0.01 — Android Sideload Build

This is the native Android edition of the desktop restock monitor.

WHAT IS INCLUDED
- Direct-install Android project (no Play Store required)
- Pokémon + One Piece logos
- Product list seeded from the desktop app
- Foreground monitoring service
- Persistent “monitoring active” notification
- Grouped high-priority restock notifications
- 24-hour per-item snooze after opening
- Lower-price-during-snooze re-alert behavior
- Per-product check intervals
- MSRP / current price / max alert price
- Pause / resume monitoring
- Price history
- Add / edit / delete products
- Random Pokémon artwork, name, number, fun fact, and Learn More button
- Target / Walmart / Pokémon Center / Shopify-style retailer detection

INSTALL / BUILD
1. Install Android Studio on Windows, macOS, or Linux.
2. Open the TCG_Restock_Monitor_MV0.01_Android folder.
3. Allow Android Studio to install the requested SDK / build tools and accept Google's SDK licenses.
4. Choose Build > Build APK(s).
5. The debug APK is normally created at:
   app/build/outputs/apk/debug/app-debug.apk
6. Copy the APK to your Android phone and allow “Install unknown apps” for the app you use to open it.
7. Launch TCG Restock Monitor and grant notification permission.
8. Tap Start Monitoring.

IMPORTANT ANDROID BEHAVIOR
- Frequent monitoring uses a foreground service, so Android displays a persistent notification while it runs.
- The service is started by you from the app. Android restricts starting foreground services silently from the background.
- This project targets SDK 34 while compiling against SDK 35 to keep the sideload build compatible with frequent foreground monitoring.
- Some phone manufacturers aggressively kill background apps. If monitoring stops unexpectedly, exclude this app from battery optimization on that phone.

DATA
Android stores products.json, settings.json, and price_history.json in the app's private files directory.
Deleting the app normally deletes those files unless Android backup restores them.

This is a pre-release MV0.01 build and is intended for personal sideload/testing.
