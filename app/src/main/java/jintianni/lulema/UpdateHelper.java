package jintianni.lulema;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Helper to fetch latest release from Gitee and auto download/install the APK.
 */
public class UpdateHelper {
    private static long downloadId = -1;
    private static BroadcastReceiver downloadReceiver;
    private static final String RELEASE_API = "https://gitee.com/api/v5/repos/mayixuanemail-web/lulema/releases/latest";

    public static void checkAndDownload(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            Context dialogCtx = (context instanceof android.app.Activity) ? context : app;
            new AlertDialog.Builder(dialogCtx)
                    .setTitle("发现新版本")
                    .setMessage("是否下载并安装最新版本？")
                    .setPositiveButton("立即更新", (d, which) -> checkAndDownloadInternal(app))
                    .setNegativeButton("稍后", null)
                    .show();
        });
    }

    private static void checkAndDownloadInternal(Context app) {
        if (app == null) return;
        showToast(app, "正在检查更新...");
        new Thread(() -> {
            try {
                String apkUrl = fetchLatestApkUrl();
                if (TextUtils.isEmpty(apkUrl)) {
                    showToast(app, "未找到可用的更新包");
                    return;
                }
                startDownload(app, apkUrl);
            } catch (Exception e) {
                showToast(app, "检查更新失败，请稍后再试");
            }
        }).start();
    }

    private static String fetchLatestApkUrl() throws Exception {
        URL url = new URL(RELEASE_API);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() != 200) {
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        JSONObject json = new JSONObject(sb.toString());
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "");
                if (name.endsWith(".apk")) {
                    String urlField = asset.optString("browser_download_url", "");
                    if (TextUtils.isEmpty(urlField)) {
                        urlField = asset.optString("url", "");
                    }
                    if (!TextUtils.isEmpty(urlField)) {
                        return urlField;
                    }
                }
            }
        }
        // fallback to release page
        return json.optString("html_url", "");
    }

    private static void startDownload(Context app, String url) {
        if (TextUtils.isEmpty(url)) {
            showToast(app, "下载链接无效");
            return;
        }

        // 权限检查：未知来源安装
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!app.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + app.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(intent);
                showToast(app, "请允许安装未知来源应用后重试");
                return;
            }
        }

        DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            showToast(app, "无法获取下载服务");
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("下载更新");
        request.setDescription("正在下载最新版本...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");
        request.setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "update-latest.apk");

        downloadId = dm.enqueue(request);

        if (downloadReceiver == null) {
            downloadReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        installApk(app);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            ContextCompat.registerReceiver(app, downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        showToast(app, "开始下载更新");
    }

    private static void installApk(Context app) {
        DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            showToast(app, "下载管理不可用");
            return;
        }

        Uri uri = dm.getUriForDownloadedFile(downloadId);
        if (uri == null) {
            showToast(app, "未找到下载的文件");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            app.startActivity(intent);
        } catch (Exception e) {
            try {
                java.io.File file = new java.io.File(uri.getPath());
                Uri contentUri = FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", file);
                intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                app.startActivity(intent);
            } catch (Exception ex) {
                showToast(app, "安装失败，请手动安装");
            }
        }
    }

    private static void showToast(Context context, String msg) {
        if (context == null) return;
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }
}
