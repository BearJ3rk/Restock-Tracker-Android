TCG Restock Monitor MV0.8 — Alert Accuracy Update

NEW IN MV0.8
------------
- Requires consecutive in-stock checks before sending an alert (two by default).
- Adds a global default maximum alert price with per-product overrides.
- Blocks unverified third-party sellers on Walmart, Target, Amazon, and eBay listings.
- Shows confirming, seller-unverified, missing-price, and above-limit states in the app.
- Saves the seller and trigger reason on each alert card.
- Adds unit coverage for confirmation, price inheritance, and seller verification rules.

TCG Restock Monitor MV0.07 — Mobile Icon Update

NEW IN MV0.07
-------------
- Added the new custom mobile launcher artwork.
- Added density-specific legacy and round launcher icons.
- Added an adaptive launcher icon for modern Android devices.

TCG Restock Monitor MV0.06 — Alert Organization and Price Controls

NEW IN MV0.06
-------------
- Current alerts remain at the top of the Alerts tab.
- Silencing an alert moves it into a collapsible Silenced Alerts group below current alerts.
- Alert cards now show their configured price limit and provide a direct Alert Price action.
- Price-limited alerts require a fresh detected price, preventing alerts based on missing or stale prices.
- Products that become affordable while still in stock can alert when they cross below the configured limit.

TCG Restock Monitor MV0.05 — Alert Deep-Link Update

NEW IN MV0.05
-------------
- Added a dedicated Alerts tab.
- Triggered restocks are saved in their own alert cards.
- Tapping a restock notification opens directly to the Alerts tab.
- Single-item alerts carry the exact product URL into the app.
- Alert cards provide Open Product, Silence 24h, Go to Product, and History.
- Triggered alerts persist across app restarts until cleared.

TCG Restock Monitor MV0.04 — Bottom Navigation Update

NEW IN MV0.04
-------------
- Added four bottom navigation tabs:
  * Products
  * In Stock
  * Activity
  * Settings
- Tabs stay reachable at the bottom of the screen for one-thumb use.
- In Stock tab shows only currently available products, with Open and Silence 24h actions.
- Activity tab shows recent product check/status activity.
- Settings tab collects app settings, data location, and refresh controls.
- Products tab keeps the compact random Pokémon section, monitor controls, and product cards.
- All prior MV0.03 phone-friendly controls and large touch targets remain.

TCG Restock Monitor MV0.03 — Mobile UI Update

NEW IN MV0.03
-------------
- Added a fixed bottom action bar with Start/Stop, Check, and + Product.
- Main controls stay reachable with one thumb even when scrolled deep into the product list.
- Increased button/touch target sizes.
- Product cards now use two rows of larger buttons instead of four cramped buttons.
- Compact top header saves vertical space.
- Random Pokémon section can be hidden/shown.
- Settings and Data buttons remain near the top but use larger mobile-friendly targets.
- Product details are condensed for narrow screens.
- Long-press a product card to delete it.

All MV0.02 monitoring, notifications, price history, snooze, price-drop, and foreground-service
features remain.

TCG Restock Monitor MV0.02 — Android Sideload Build

CHANGED
-------
- Removed the Pokémon logo from the Android app.
- Removed the One Piece logo from the Android app.
- The header now uses only the neutral TCG Restock Monitor title.
- Replaced the launcher icon with a neutral TCG icon.
- No Pokémon or One Piece logo image resources are included.

This is the native Android edition of the desktop restock monitor.

WHAT IS INCLUDED
- Direct-install Android project (no Play Store required)
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
2. Open the TCG_Restock_Monitor_MV0.8_Android folder.
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

This is a pre-release MV0.8 build and is intended for personal sideload/testing.
