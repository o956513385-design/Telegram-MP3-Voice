```java
package org.telegram.messenger;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {

    private static final String RELEASE_API =
            "https://api.github.com/repos/o956513385-design/Telegram-MP3-Voice/releases/latest";

    private static final String APK_FILE_NAME =
            "Telegram-MP3-Voice-update.apk";

    public interface UpdateCallback {
        void onUpdateAvailable(String version, String downloadUrl);
        void onNoUpdate();
        void onError(Exception e);
    }

    public static void check(Context context, UpdateCallback callback) {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {
                URL url = new URL(RELEASE_API);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty(
                        "Accept",
                        "application/vnd.github+json"
                );

                int responseCode = connection.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception(
                            "GitHub API HTTP " + responseCode
                    );
                }

                InputStream inputStream = connection.getInputStream();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream)
                );

                StringBuilder response = new StringBuilder();

                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONObject release =
                        new JSONObject(response.toString());

                String tagName =
                        release.optString("tag_name", "");

                String version =
                        tagName.startsWith("v")
                                ? tagName.substring(1)
                                : tagName;

                String downloadUrl = null;

                org.json.JSONArray assets =
                        release.optJSONArray("assets");

                if (assets != null) {

                    for (int i = 0; i < assets.length(); i++) {

                        JSONObject asset =
                                assets.getJSONObject(i);

                        String name =
                                asset.optString("name", "");

                        if ("app.apk".equals(name)) {

                            downloadUrl =
                                    asset.optString(
                                            "browser_download_url",
                                            null
                                    );

                            break;
                        }
                    }
                }

                if (downloadUrl == null) {
                    throw new Exception(
                            "app.apk not found in GitHub Release"
                    );
                }

                String currentVersion =
                        getCurrentVersion(context);

                if (isNewerVersion(version, currentVersion)) {

                    String finalVersion = version;
                    String finalDownloadUrl = downloadUrl;

                    runOnUiThread(context, () ->
                            callback.onUpdateAvailable(
                                    finalVersion,
                                    finalDownloadUrl
                            )
                    );

                } else {

                    runOnUiThread(
                            context,
                            callback::onNoUpdate
                    );
                }

            } catch (Exception e) {

                runOnUiThread(
                        context,
                        () -> callback.onError(e)
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    private static String getCurrentVersion(Context context) {

        try {

            PackageInfo packageInfo =
                    context.getPackageManager()
                            .getPackageInfo(
                                    context.getPackageName(),
                                    0
                            );

            return packageInfo.versionName;

        } catch (Exception e) {

            return "0.0.0";
        }
    }

    private static boolean isNewerVersion(
            String remoteVersion,
            String currentVersion
    ) {

        try {

            String[] remote =
                    remoteVersion.split("\\.");

            String[] current =
                    currentVersion.split("\\.");

            int length =
                    Math.max(
                            remote.length,
                            current.length
                    );

            for (int i = 0; i < length; i++) {

                int remotePart =
                        i < remote.length
                                ? parseVersionPart(remote[i])
                                : 0;

                int currentPart =
                        i < current.length
                                ? parseVersionPart(current[i])
                                : 0;

                if (remotePart > currentPart) {
                    return true;
                }

                if (remotePart < currentPart) {
                    return false;
                }
            }

            return false;

        } catch (Exception e) {

            return false;
        }
    }

    private static int parseVersionPart(String value) {

        StringBuilder number = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                break;
            }
        }

        if (number.length() == 0) {
            return 0;
        }

        return Integer.parseInt(number.toString());
    }

    public static void downloadAndInstall(
            Context context,
            String downloadUrl
    ) {

        try {

            if (android.os.Build.VERSION.SDK_INT >= 26) {

                if (!context.getPackageManager()
                        .canRequestPackageInstalls()) {

                    Intent settingsIntent =
                            new Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse(
                                            "package:"
                                                    + context.getPackageName()
                                    )
                            );

                    settingsIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

                    context.startActivity(settingsIntent);

                    return;
                }
            }

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(downloadUrl)
                    );

            request.setTitle(
                    "Telegram-MP3-Voice"
            );

            request.setDescription(
                    "Загрузка обновления"
            );

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILE_NAME
            );

            DownloadManager downloadManager =
                    (DownloadManager)
                            context.getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );

            if (downloadManager != null) {

                downloadManager.enqueue(request);
            }

        } catch (Exception e) {

            FileLog.e(e);
        }
    }

    private static void runOnUiThread(
            Context context,
            Runnable runnable
    ) {

        if (context instanceof Activity) {

            ((Activity) context).runOnUiThread(
                    runnable
            );

        } else {

            AndroidUtilities.runOnUIThread(
                    runnable
            );
        }
    }
}
```
