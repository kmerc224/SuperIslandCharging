package com.superisland.charging;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.superisland.charging.log.LogCapture;

import org.json.JSONObject;
import com.superisland.charging.BuildConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 关于页面
 * <p>
 * 显示应用信息、版本、开发者信息，并支持检查更新。
 */
public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "AboutActivity";
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/SuperIsland/charging-monitor/releases/latest";
    private static final String GITHUB_REPO_URL =
            "https://github.com/SuperIsland/charging-monitor";

    private MaterialTextView tvVersion;
    private MaterialTextView tvGithub;
    private MaterialButton btnCheckUpdate;

    private final LogCapture logCapture = LogCapture.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 禁用边缘到边缘显示
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_about);

        setupToolbar();
        initViews();
        setupButtons();
    }

    // ==================== 初始化 ====================

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        tvVersion = findViewById(R.id.tv_version);
        tvGithub = findViewById(R.id.tv_github);
        btnCheckUpdate = findViewById(R.id.btn_check_update);

        // 显示版本号
        String versionName = getVersionName();
        tvVersion.setText("版本 " + versionName);

        logCapture.info(TAG, "关于页面已打开，当前版本: " + versionName);
    }

    private void setupButtons() {
        // 检查更新按钮
        btnCheckUpdate.setOnClickListener(v -> checkForUpdate());

        // GitHub 链接
        tvGithub.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL));
                startActivity(intent);
                logCapture.info(TAG, "打开 GitHub 仓库链接");
            } catch (Exception e) {
                logCapture.error(TAG, "无法打开浏览器: " + e.getMessage());
            }
        });
    }

    // ==================== 检查更新 ====================

    private void checkForUpdate() {
        // 禁用按钮，显示加载状态
        btnCheckUpdate.setEnabled(false);
        btnCheckUpdate.setText("检查中...");

        logCapture.info(TAG, "开始检查更新...");

        new Thread(() -> {
            String result = null;
            String latestVersion = null;
            Exception error = null;

            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    latestVersion = json.optString("tag_name", null);

                    if (latestVersion != null && !latestVersion.isEmpty()) {
                        // 去除 "v" 前缀进行比较
                        String currentVersion = getVersionName();
                        String compareLatest = latestVersion.startsWith("v")
                                ? latestVersion.substring(1) : latestVersion;
                        String compareCurrent = currentVersion.startsWith("v")
                                ? currentVersion.substring(1) : currentVersion;

                        if (compareLatest.equals(compareCurrent)) {
                            result = "latest";
                        } else {
                            result = "update";
                        }
                    } else {
                        result = "no_release";
                    }
                } else {
                    result = "http_error";
                }

                connection.disconnect();
            } catch (Exception e) {
                error = e;
                result = "error";
            }

            // 在主线程更新 UI
            final String finalResult = result;
            final String finalVersion = latestVersion;
            final Exception finalError = error;

            runOnUiThread(() -> {
                btnCheckUpdate.setEnabled(true);
                btnCheckUpdate.setText("检查更新");

                showUpdateResult(finalResult, finalVersion, finalError);
            });
        }).start();
    }

    private void showUpdateResult(String result, String latestVersion, Exception error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);

        switch (result) {
            case "latest":
                builder.setTitle("已是最新版本");
                builder.setMessage("当前版本 " + getVersionName() + " 已是最新版本。");
                builder.setPositiveButton("确定", null);
                logCapture.info(TAG, "检查结果: 已是最新版本");
                break;

            case "update":
                builder.setTitle("发现新版本");
                builder.setMessage("最新版本: " + latestVersion + "\n当前版本: " + getVersionName()
                        + "\n\n是否前往下载？");
                builder.setPositiveButton("前往下载", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL));
                        startActivity(intent);
                    } catch (Exception e) {
                        logCapture.error(TAG, "无法打开下载链接: " + e.getMessage());
                    }
                });
                builder.setNegativeButton("稍后", null);
                logCapture.info(TAG, "检查结果: 发现新版本 " + latestVersion);
                break;

            case "no_release":
                builder.setTitle("检查失败");
                builder.setMessage("未找到任何发布版本。");
                builder.setPositiveButton("确定", null);
                logCapture.warn(TAG, "检查结果: 未找到发布版本");
                break;

            case "http_error":
                builder.setTitle("检查失败");
                builder.setMessage("服务器返回错误，请稍后再试。");
                builder.setPositiveButton("确定", null);
                logCapture.warn(TAG, "检查结果: HTTP 错误");
                break;

            case "error":
            default:
                builder.setTitle("检查失败");
                String errorMsg = (error != null && error.getMessage() != null)
                        ? "网络错误: " + error.getMessage()
                        : "网络连接失败，请检查网络后重试。";
                builder.setMessage(errorMsg);
                builder.setPositiveButton("确定", null);
                logCapture.error(TAG, "检查结果: 网络错误 - "
                        + (error != null ? error.getMessage() : "unknown"));
                break;
        }

        builder.show();
    }

    // ==================== 工具方法 ====================

    private String getVersionName() {
        try {
            String version = BuildConfig.VERSION_NAME;
            return version != null ? version : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }
}
