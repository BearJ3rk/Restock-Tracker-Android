package com.tcgrestock.monitor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateManager {
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/BearJ3rk/Restock-Tracker-Android/releases/latest";
    private static final String USER_AGENT = "TCG-Restock-Monitor-Android/" + BuildConfig.VERSION_NAME;

    interface Callback {
        void onStatus(String message);
        void onUpdateAvailable(ReleaseInfo release);
        void onUpToDate();
        void onError(String message);
    }

    static final class ReleaseInfo {
        final String version;
        final String notes;
        final String apkName;
        final String apkUrl;
        final String sha256;

        ReleaseInfo(String version, String notes, String apkName, String apkUrl, String sha256) {
            this.version = version;
            this.notes = notes;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private File pendingInstall;

    UpdateManager(Activity activity) {
        this.activity = activity;
    }

    void check(Callback callback) {
        callback.onStatus("Checking GitHub for updates…");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(LATEST_RELEASE_URL);
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("GitHub returned HTTP " + status);
                }

                String json = readAll(connection.getInputStream());
                JSONObject release = new JSONObject(json);
                String version = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (version.isEmpty()) throw new IllegalStateException("The latest release has no version tag");

                JSONArray assets = release.optJSONArray("assets");
                JSONObject apk = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject candidate = assets.optJSONObject(i);
                        if (candidate != null && candidate.optString("name", "").toLowerCase(Locale.US).endsWith(".apk")) {
                            apk = candidate;
                            break;
                        }
                    }
                }
                if (apk == null) throw new IllegalStateException("The latest release does not contain an APK");

                String digest = apk.optString("digest", "");
                if (digest.toLowerCase(Locale.US).startsWith("sha256:")) digest = digest.substring(7);
                ReleaseInfo info = new ReleaseInfo(
                        version,
                        release.optString("body", ""),
                        apk.optString("name", "update.apk"),
                        apk.getString("browser_download_url"),
                        digest
                );

                if (compareVersions(version, BuildConfig.VERSION_NAME) > 0) {
                    main.post(() -> callback.onUpdateAvailable(info));
                } else {
                    main.post(callback::onUpToDate);
                }
            } catch (Exception error) {
                main.post(() -> callback.onError(cleanMessage(error)));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    void downloadAndInstall(ReleaseInfo release, Callback callback) {
        callback.onStatus("Downloading version " + release.version + "…");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                File base = activity.getExternalCacheDir();
                if (base == null) base = activity.getCacheDir();
                File directory = new File(base, "updates");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Could not create the update folder");
                }

                File apk = new File(directory, safeFileName(release.apkName));
                connection = open(release.apkUrl);
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("The APK download returned HTTP " + status);
                }

                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                     BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(apk))) {
                    byte[] buffer = new byte[32 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        sha256.update(buffer, 0, count);
                    }
                }

                String actualDigest = hex(sha256.digest());
                if (!release.sha256.isEmpty() && !actualDigest.equalsIgnoreCase(release.sha256)) {
                    apk.delete();
                    throw new SecurityException("The downloaded APK failed its SHA-256 verification");
                }

                File verifiedApk = apk;
                main.post(() -> requestInstall(verifiedApk, callback));
            } catch (Exception error) {
                main.post(() -> callback.onError(cleanMessage(error)));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    void resumePendingInstall(Callback callback) {
        if (pendingInstall != null && pendingInstall.isFile() && canInstallPackages()) {
            File apk = pendingInstall;
            pendingInstall = null;
            requestInstall(apk, callback);
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private void requestInstall(File apk, Callback callback) {
        if (!canInstallPackages()) {
            pendingInstall = apk;
            callback.onStatus("Allow installs from this app, then return to continue.");
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permission);
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apk
        );
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(install);
        callback.onStatus("Android is ready to install the update.");
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("[^0-9]+");
        String[] b = right.split("[^0-9]+");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int bv = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static String safeFileName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.toLowerCase(Locale.US).endsWith(".apk") ? safe : "update.apk";
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(Locale.US, "%02x", b & 0xff));
        return value.toString();
    }

    private static String cleanMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "The update could not be completed" : message;
    }

    private static String readAll(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
