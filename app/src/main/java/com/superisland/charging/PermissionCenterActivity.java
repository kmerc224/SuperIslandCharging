package com.superisland.charging;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.superisland.charging.log.LogCapture;

/**
 * 权限中心页面
 * <p>
 * 集中展示和管理应用所需的各类权限：
 * - 通知权限 (POST_NOTIFICATIONS)
 * - 电池优化白名单
 * - 无限制后台运行
 * - Shizuku 授权
 * - Root 授权
 */
public class PermissionCenterActivity extends AppCompatActivity {

    private static final int SHIZUKU_REQUEST_CODE = 1001;
    private static final int GRANTED_STROKE_COLOR = Color.parseColor("#FF4CAF50");

    // 通知权限
    private MaterialCardView cardNotification;
    private MaterialTextView tvNotificationStatus;
    private MaterialButton btnGrantNotification;

    // 电池优化白名单
    private MaterialCardView cardBatteryOptimization;
    private MaterialTextView tvBatteryOptimizationStatus;
    private MaterialButton btnGrantBattery;

    // 无限制后台
    private MaterialCardView cardUnrestrictedBg;
    private MaterialTextView tvUnrestrictedBgStatus;
    private MaterialButton btnUnrestrictedBg;

    // Shizuku
    private MaterialCardView cardShizuku;
    private MaterialTextView tvShizukuStatus;
    private MaterialButton btnShizuku;

    // Root
    private MaterialCardView cardRoot;
    private MaterialTextView tvRootStatus;
    private MaterialButton btnRoot;

    // 通知权限请求启动器
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_center);

        LogCapture.getInstance().info("PermissionCenter", "Permission center opened");

        setupToolbar();
        setupViews();
        setupNotificationPermissionLauncher();
        setupButtonListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到页面时刷新所有权限状态
        refreshAllPermissionStatus();
    }

    /**
     * 初始化工具栏，设置返回导航
     */
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    /**
     * 绑定视图引用
     */
    private void setupViews() {
        // 通知权限
        cardNotification = findViewById(R.id.card_notification);
        tvNotificationStatus = findViewById(R.id.tv_notification_status);
        btnGrantNotification = findViewById(R.id.btn_grant_notification);

        // 电池优化白名单
        cardBatteryOptimization = findViewById(R.id.card_battery_optimization);
        tvBatteryOptimizationStatus = findViewById(R.id.tv_battery_optimization_status);
        btnGrantBattery = findViewById(R.id.btn_grant_battery);

        // 无限制后台
        cardUnrestrictedBg = findViewById(R.id.card_unrestricted_bg);
        tvUnrestrictedBgStatus = findViewById(R.id.tv_unrestricted_bg_status);
        btnUnrestrictedBg = findViewById(R.id.btn_unrestricted_bg);

        // Shizuku
        cardShizuku = findViewById(R.id.card_shizuku);
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status);
        btnShizuku = findViewById(R.id.btn_shizuku);

        // Root
        cardRoot = findViewById(R.id.card_root);
        tvRootStatus = findViewById(R.id.tv_root_status);
        btnRoot = findViewById(R.id.btn_root);
    }

    /**
     * 注册通知权限请求回调
     */
    private void setupNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        LogCapture.getInstance().info("PermissionCenter",
                                "Notification permission granted");
                        updateCardStatus(cardNotification, tvNotificationStatus,
                                true, "已授予");
                    } else {
                        LogCapture.getInstance().warn("PermissionCenter",
                                "Notification permission denied");
                        updateCardStatus(cardNotification, tvNotificationStatus,
                                false, "已拒绝");
                    }
                }
        );
    }

    /**
     * 设置各权限卡片的按钮点击事件
     */
    private void setupButtonListeners() {
        // 通知权限 - 请求授予
        btnGrantNotification.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(
                        android.Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Android 13 以下默认有权限
                updateCardStatus(cardNotification, tvNotificationStatus,
                        true, "已授予");
            }
        });

        // 电池优化白名单 - 请求加入白名单
        btnGrantBattery.setOnClickListener(v -> {
            Intent intent = PermissionHelper.getBatteryOptimizationIntent(this);
            startActivity(intent);
            LogCapture.getInstance().info("PermissionCenter",
                    "Request battery optimization whitelist");
        });

        // 无限制后台 - 打开应用详情设置
        btnUnrestrictedBg.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            LogCapture.getInstance().info("PermissionCenter",
                    "Open app detail settings for unrestricted background");
        });

        // Shizuku - 请求授权
        btnShizuku.setOnClickListener(v -> {
            if (!PermissionHelper.isShizukuAvailable()) {
                Snackbar.make(v, "Shizuku 未安装或未运行", Snackbar.LENGTH_SHORT).show();
                LogCapture.getInstance().warn("PermissionCenter",
                        "Shizuku not available");
                return;
            }
            PermissionHelper.requestShizukuPermission(SHIZUKU_REQUEST_CODE);
            LogCapture.getInstance().info("PermissionCenter",
                    "Request Shizuku permission");
        });

        // Root - 测试 Root 权限
        btnRoot.setOnClickListener(v -> {
            LogCapture.getInstance().info("PermissionCenter",
                    "Testing root permission");
            String result = PermissionHelper.executeViaRoot("id");
            if (result != null && result.contains("uid=0")) {
                updateCardStatus(cardRoot, tvRootStatus, true, "已授权 (root)");
                LogCapture.getInstance().info("PermissionCenter",
                        "Root test successful: " + result);
                Snackbar.make(v, "Root 权限可用", Snackbar.LENGTH_SHORT).show();
            } else {
                updateCardStatus(cardRoot, tvRootStatus, false, "未授权");
                LogCapture.getInstance().warn("PermissionCenter",
                        "Root test failed");
                Snackbar.make(v, "Root 权限不可用", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 刷新所有权限的显示状态
     */
    private void refreshAllPermissionStatus() {
        try {
            // 通知权限
            boolean hasNotification = PermissionHelper.hasNotificationPermission(this);
            updateCardStatus(cardNotification, tvNotificationStatus,
                    hasNotification,
                    hasNotification ? "已授予" : "未授予");

            // 电池优化白名单
            boolean isBatteryWhitelisted = PermissionHelper.isIgnoringBatteryOptimizations(this);
            updateCardStatus(cardBatteryOptimization, tvBatteryOptimizationStatus,
                    isBatteryWhitelisted,
                    isBatteryWhitelisted ? "已加入白名单" : "未加入白名单");

            // 无限制后台 - 通过 AppOpsManager 检查
            boolean isUnrestricted = isUnrestrictedBackground();
            updateCardStatus(cardUnrestrictedBg, tvUnrestrictedBgStatus,
                    isUnrestricted,
                    isUnrestricted ? "已设置" : "未设置");

            // Shizuku
            boolean shizukuAvailable = PermissionHelper.isShizukuAvailable();
            boolean shizukuAuthorized = PermissionHelper.isShizukuAuthorized();
            if (!shizukuAvailable) {
                updateCardStatus(cardShizuku, tvShizukuStatus,
                        false, "Shizuku 未安装");
            } else if (shizukuAuthorized) {
                updateCardStatus(cardShizuku, tvShizukuStatus,
                        true, "已授权");
            } else {
                updateCardStatus(cardShizuku, tvShizukuStatus,
                        false, "未授权");
            }

            // Root
            boolean rootAvailable = PermissionHelper.isRootAvailable();
            updateCardStatus(cardRoot, tvRootStatus,
                    rootAvailable,
                    rootAvailable ? "可用" : "不可用");
        } catch (Exception e) {
            LogCapture.getInstance().error("PermissionCenter",
                    "refreshAllPermissionStatus failed: " + e.getMessage());
        }
    }

    /**
     * 检查是否已设置无限制后台
     * 简化版本：直接通过电池优化白名单判断
     */
    private boolean isUnrestrictedBackground() {
        try {
            // 通过电池优化白名单来判断（更兼容）
            return PermissionHelper.isIgnoringBatteryOptimizations(this);
        } catch (Exception e) {
            LogCapture.getInstance().warn("PermissionCenter",
                    "isUnrestrictedBackground check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 更新权限卡片的视觉状态
     *
     * @param card       卡片视图
     * @param statusText 状态文本
     * @param granted    是否已授予
     * @param status     状态描述文字
     */
    private void updateCardStatus(MaterialCardView card, MaterialTextView statusText,
                                  boolean granted, String status) {
        try {
            statusText.setText(status);
            if (granted) {
                card.setStrokeColor(GRANTED_STROKE_COLOR);
                statusText.setTextColor(GRANTED_STROKE_COLOR);
            } else {
                // 使用主题默认 outline 颜色
                card.setStrokeColor(getThemeColor(com.google.android.material.R.attr.colorOutline));
                statusText.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            }
        } catch (Exception e) {
            LogCapture.getInstance().warn("PermissionCenter",
                    "updateCardStatus failed: " + e.getMessage());
        }
    }

    /**
     * 从当前主题获取颜色值
     */
    private int getThemeColor(int attr) {
        try {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        } catch (Exception e) {
            return Color.GRAY; // 默认灰色
        }
    }
}
