# TCG Restock Monitor for Android

Native Android restock and price monitor for selected Pokemon, One Piece, and other trading-card products. This repository contains the pre-release **MV0.01** sideload build.

## Download the APK

Every push to `main` starts the **Build Android APK** workflow. Open the latest successful workflow run in the repository's **Actions** tab, then download the `TCG-Restock-Monitor-MV0.01-debug-apk` artifact. Extract the downloaded ZIP to get `app-debug.apk`.

On the Android device, allow **Install unknown apps** for the app used to open the APK, install it, launch **TCG Restock Monitor**, grant notification permission, and tap **Start Monitoring**.

## Build locally

Use Android Studio with JDK 17, Android SDK 35, Android Gradle Plugin 8.7.3, and Gradle 8.9. Open this repository, let Gradle sync, and choose **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. The output is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Important Android behavior

- Frequent checks use an Android foreground service, so a persistent monitoring notification is shown.
- Android 13 and newer require notification permission.
- Some manufacturers stop background apps aggressively; exclude this app from battery optimization if monitoring stops unexpectedly.
- App data is stored in Android's private application directory and is normally deleted when the app is uninstalled.

See `README_ANDROID.txt` for the original MV0.01 feature and behavior notes.
