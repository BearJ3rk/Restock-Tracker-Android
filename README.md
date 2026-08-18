# TCG Restock Monitor for Android

Native Android restock and price monitor for selected Pokemon, One Piece, and other trading-card products. The current mobile build is **MV0.11**.

## Install and update

Signed, installable versions are published on the repository's **Releases** page. The app checks that page when it starts (at most once per day), and the **Check Updates** button can check immediately. When a newer version exists, the app downloads the release APK, verifies its GitHub-provided SHA-256 digest, and opens Android's installer for user confirmation.

For the first installation, allow **Install unknown apps** for the app used to open the APK. For an in-app update, Android may ask once to allow TCG Restock Monitor to request installations. Android always controls the final installation confirmation.

> The original v0.01 debug APK used an ephemeral debug certificate. Uninstall it once before installing any signed v0.02-or-newer release. Beginning with v0.02, releases use the same protected signing key and install over one another without deleting app data.

MV0.11 adds JSON backup/import for products, settings, price history, alerts, and health data. A new monitoring-health screen reports battery restrictions, service cycles, successful checks, failures, timing, and retailer errors.

MV0.10 adds product search, retailer and state filters, per-product alert toggles, multi-select, and bulk pause, alert, price, and delete controls.

MV0.9 adds custom silence durations, overnight quiet hours that keep monitoring while delaying notifications, and Open, Silence, and Dismiss actions directly on Android restock notifications.

MV0.8 reduces false alerts with consecutive in-stock confirmation, a global default maximum price with per-product overrides, and verified marketplace seller rules. Alert cards now show the seller and the reason each notification triggered.

MV0.07 adds the new custom mobile launcher icon in legacy, round, and adaptive Android formats.

MV0.06 groups silenced alert history beneath current alerts, adds an Alert Price control directly to alert cards, and requires a freshly verified price before sending a price-limited alert.

## Automated builds

Every push to `main` starts **Build Android APK** and produces a temporary debug APK for development testing. Version tags such as `v0.05` start **Publish Signed Android Release**, which signs the release APK, verifies its signature, creates a checksum, and publishes both files on GitHub Releases.

The release keystore and its passwords are stored only as encrypted GitHub Actions secrets. Signing material is never committed to this repository.

To publish a future version:

1. Increase both `versionCode` and `versionName` in `app/build.gradle`.
2. Commit and push the change to `main`.
3. Create and push a tag exactly matching `v<versionName>`.
4. Wait for **Publish Signed Android Release** to publish the APK and checksum.

## Build locally

Use Android Studio with JDK 17, Android SDK 35, Android Gradle Plugin 8.7.3, and Gradle 8.9. Open this repository, let Gradle sync, and choose **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Important Android behavior

- Frequent checks use an Android foreground service, so a persistent monitoring notification is shown.
- Android 13 and newer require notification permission.
- Some manufacturers stop background apps aggressively; exclude this app from battery optimization if monitoring stops unexpectedly.
- App data is stored in Android's private application directory and is normally deleted when the app is uninstalled.

See `README_ANDROID.txt` for the original MV0.01 feature and behavior notes.
