package com.superisland.charging;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

// Shizuku API 通过反射调用，避免编译时依赖问题

/**
 * 首次使用设置向导
 * <p>
 * 引导用户完成以下设置步骤：
 * 1. 欢迎介绍
 * 2. 授予通知权限
 * 3. 后台权限（电池优化白名单 + 无限制后台运行）
 * 4. 选择高级权限（Shizuku / Root / 跳过）
 * 5. 设置完成
 */
public class SetupWizardActivity extends AppCompatActivity {

    private static final int TOTAL_STEPS = 5;
    private static final int REQUEST_SHIZUKU_PERMISSION = 100;

    private int currentStep = 1;

    // UI 组件
    private TextView tvStepIndicator;
    private LinearProgressIndicator progressIndicator;
    private MaterialButton btnBack;
    private MaterialButton btnNext;

    // 各步骤容器
    private LinearLayout stepWelcome;
    private LinearLayout stepNotification;
    private LinearLayout stepBattery;
    private LinearLayout stepElevated;
    private LinearLayout stepComplete;

    // 步骤2 - 通知权限
    private ImageView ivNotificationIcon;
    private TextView tvNotificationStatus;
    private MaterialButton btnGrantNotification;

    // 步骤3 - 电池优化 + 无限制后台
    private ImageView ivBatteryIcon;
    private TextView tvBatteryStatus;
    private MaterialButton btnGrantBattery;
    private ImageView ivBackgroundIcon;
    private TextView tvBackgroundStatus;
    private MaterialButton btnGrantBackground;
    private MaterialCardView cardUnrestrictedBg;

    // 步骤4 - 高级权限
    private TextView tvShizukuStatus;
    private TextView tvRootStatus;
    private MaterialButton btnShizuku;
    private MaterialButton btnRoot;
    private MaterialButton btnSkipElevated;
    private MaterialCardView cardShizuku;
    private MaterialCardView cardRoot;

    // 步骤5 - 完成
    private TextView tvPermissionSummary;

    // 状态标记
    private boolean notificationGranted = false;
    private boolean batteryOptimizationIgnored = false;
    private String selectedElevatedMode = PermissionHelper.MODE_NONE;
    private boolean shizukuAvailable = false;
    private boolean rootAvailable = false;
    private boolean shizukuAuthorized = false;
    private boolean rootAuthorized = false;

    // Activity Result Launchers
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<Intent> batteryOptimizationLauncher;
    private ActivityResultLauncher<Intent> appDetailSettingsLauncher;

    // Shizuku 权限结果监听器（通过反射代理实现）
    private Object shizukuPermissionListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 禁用边缘到边缘显示
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_setup_wizard);

        initLaunchers();
        initViews();
        detectElevatedPermissions();
        showStep(1);

        // 注册 Shizuku 权限结果监听器（反射方式）
        try {
            Class<?> listenerClass = Class.forName("rikka.shizuku.Shizuku$OnRequestPermissionResultListener");
            shizukuPermissionListener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onRequestPermissionResult".equals(method.getName()) && args != null && args.length == 2) {
                            int requestCode = (int) args[0];
                            int grantResult = (int) args[1];
                            if (requestCode == REQUEST_SHIZUKU_PERMISSION) {
                                shizukuAuthorized = (grantResult == PackageManager.PERMISSION_GRANTED);
                                if (shizukuAuthorized) {
                                    selectedElevatedMode = PermissionHelper.MODE_SHIZUKU;
                                    PermissionHelper.setElevatedMode(SetupWizardActivity.this,
                                            PermissionHelper.MODE_SHIZUKU);
                                    showStep(5);
                                }
                                refreshElevatedUI();
                            }
                        }
                        return null;
                    });
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            shizukuClass.getMethod("addOnRequestPermissionResultListener", listenerClass)
                    .invoke(null, shizukuPermissionListener);
        } catch (Exception e) {
            // Shizuku 不可用时忽略
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (shizukuPermissionListener != null) {
                Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                Class<?> listenerClass = Class.forName("rikka.shizuku.Shizuku$OnRequestPermissionResultListener");
                shizukuClass.getMethod("removeOnRequestPermissionResultListener", listenerClass)
                        .invoke(null, shizukuPermissionListener);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ==================== 初始化 ====================

    private void initLaunchers() {
        // 通知权限请求
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    notificationGranted = granted;
                    refreshNotificationUI();
                });

        // 电池优化白名单请求
        batteryOptimizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 返回后检查状态
                    refreshBatteryUI();
                });

        // 应用详情页（无限制后台权限）
        appDetailSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    refreshBatteryUI();
                });
    }

    private void initViews() {
        tvStepIndicator = findViewById(R.id.tv_step_indicator);
        progressIndicator = findViewById(R.id.progress_indicator);
        btnBack = findViewById(R.id.btn_back);
        btnNext = findViewById(R.id.btn_next);

        stepWelcome = findViewById(R.id.step_welcome);
        stepNotification = findViewById(R.id.step_notification);
        stepBattery = findViewById(R.id.step_battery);
        stepElevated = findViewById(R.id.step_elevated);
        stepComplete = findViewById(R.id.step_complete);

        // 步骤2
        ivNotificationIcon = findViewById(R.id.iv_notification_icon);
        tvNotificationStatus = findViewById(R.id.tv_notification_status);
        btnGrantNotification = findViewById(R.id.btn_grant_notification);
        btnGrantNotification.setOnClickListener(v -> requestNotificationPermission());

        // 步骤3 - 电池优化
        ivBatteryIcon = findViewById(R.id.iv_battery_icon);
        tvBatteryStatus = findViewById(R.id.tv_battery_status);
        btnGrantBattery = findViewById(R.id.btn_grant_battery);
        btnGrantBattery.setOnClickListener(v -> requestBatteryOptimization());

        // 步骤3 - 无限制后台
        ivBackgroundIcon = findViewById(R.id.iv_background_icon);
        tvBackgroundStatus = findViewById(R.id.tv_background_status);
        btnGrantBackground = findViewById(R.id.btn_grant_background);
        cardUnrestrictedBg = findViewById(R.id.card_unrestricted_bg);
        btnGrantBackground.setOnClickListener(v -> requestUnrestrictedBackground());

        // 步骤4
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status);
        tvRootStatus = findViewById(R.id.tv_root_status);
        btnShizuku = findViewById(R.id.btn_shizuku);
        btnRoot = findViewById(R.id.btn_root);
        btnSkipElevated = findViewById(R.id.btn_skip_elevated);
        cardShizuku = findViewById(R.id.card_shizuku);
        cardRoot = findViewById(R.id.card_root);

        btnShizuku.setOnClickListener(v -> onShizukuClicked());
        btnRoot.setOnClickListener(v -> onRootClicked());
        btnSkipElevated.setOnClickListener(v -> onSkipElevated());

        // 步骤5
        tvPermissionSummary = findViewById(R.id.tv_permission_summary);

        // 导航按钮
        btnBack.setOnClickListener(v -> goToPreviousStep());
        btnNext.setOnClickListener(v -> goToNextStep());
    }

    // ==================== 步骤导航 ====================

    private void showStep(int step) {
        currentStep = step;

        // 隐藏所有步骤
        stepWelcome.setVisibility(View.GONE);
        stepNotification.setVisibility(View.GONE);
        stepBattery.setVisibility(View.GONE);
        stepElevated.setVisibility(View.GONE);
        stepComplete.setVisibility(View.GONE);

        // 显示当前步骤
        switch (step) {
            case 1:
                stepWelcome.setVisibility(View.VISIBLE);
                break;
            case 2:
                stepNotification.setVisibility(View.VISIBLE);
                refreshNotificationUI();
                break;
            case 3:
                stepBattery.setVisibility(View.VISIBLE);
                refreshBatteryUI();
                break;
            case 4:
                stepElevated.setVisibility(View.VISIBLE);
                refreshElevatedUI();
                break;
            case 5:
                stepComplete.setVisibility(View.VISIBLE);
                buildSummary();
                break;
        }

        // 更新进度
        tvStepIndicator.setText(String.format("步骤 %d / %d", step, TOTAL_STEPS));
        progressIndicator.setProgress(step);

        // 更新导航按钮
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        // 返回按钮
        btnBack.setVisibility(currentStep > 1 && currentStep < 5 ? View.VISIBLE : View.GONE);

        // 下一步按钮
        switch (currentStep) {
            case 1:
                btnNext.setText("开始设置");
                btnNext.setVisibility(View.VISIBLE);
                break;
            case 2:
            case 3:
                btnNext.setText("下一步");
                btnNext.setVisibility(View.VISIBLE);
                break;
            case 4:
                // 高级权限步骤不显示下一步，由卡片按钮控制
                btnNext.setVisibility(View.GONE);
                break;
            case 5:
                btnNext.setText("进入应用");
                btnNext.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void goToNextStep() {
        if (currentStep < TOTAL_STEPS) {
            showStep(currentStep + 1);
        } else {
            // 最后一步 → 进入主界面
            finishWizard();
        }
    }

    private void goToPreviousStep() {
        if (currentStep > 1) {
            showStep(currentStep - 1);
        }
    }

    // ==================== 步骤2: 通知权限 ====================

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void refreshNotificationUI() {
        notificationGranted = PermissionHelper.hasNotificationPermission(this);

        if (notificationGranted) {
            ivNotificationIcon.setImageResource(R.drawable.ic_notification_on);
            tvNotificationStatus.setText("通知权限已授予");
            btnGrantNotification.setText("已授予");
            btnGrantNotification.setEnabled(false);
        } else {
            ivNotificationIcon.setImageResource(R.drawable.ic_notification_off);
            tvNotificationStatus.setText("通知权限未授予");
            btnGrantNotification.setText("授予通知权限");
            btnGrantNotification.setEnabled(true);
        }
    }

    // ==================== 步骤3: 电池优化 + 无限制后台 ====================

    private void requestBatteryOptimization() {
        Intent intent = PermissionHelper.getBatteryOptimizationIntent(this);
        batteryOptimizationLauncher.launch(intent);
    }

    private void requestUnrestrictedBackground() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        appDetailSettingsLauncher.launch(intent);
    }

    private void refreshBatteryUI() {
        // 电池优化白名单状态
        batteryOptimizationIgnored = PermissionHelper.isIgnoringBatteryOptimizations(this);

        if (batteryOptimizationIgnored) {
            ivBatteryIcon.setImageResource(R.drawable.ic_battery_ok);
            tvBatteryStatus.setText("已加入电池优化白名单");
            btnGrantBattery.setText("已设置");
            btnGrantBattery.setEnabled(false);
        } else {
            ivBatteryIcon.setImageResource(R.drawable.ic_battery_alert);
            tvBatteryStatus.setText("未加入电池优化白名单");
            btnGrantBattery.setText("加入白名单");
            btnGrantBattery.setEnabled(true);
        }

        // 无限制后台状态
        // 注：Android 无公开 API 直接检测"无限制"模式，以电池优化白名单作为参考指标
        if (batteryOptimizationIgnored) {
            ivBackgroundIcon.setImageResource(R.drawable.ic_battery_ok);
            tvBackgroundStatus.setText("已允许后台运行（建议在应用设置中确认电池用量为「无限制」）");
            btnGrantBackground.setText("前往确认");
            cardUnrestrictedBg.setStrokeColor(ContextCompat.getColor(this, R.color.charging_green));
        } else {
            ivBackgroundIcon.setImageResource(R.drawable.ic_background);
            tvBackgroundStatus.setText("请在应用设置中将电池用量设为「无限制」");
            btnGrantBackground.setText("前往设置");
            cardUnrestrictedBg.setStrokeColor(ContextCompat.getColor(this, R.color.md_on_surface_variant));
        }
    }

    // ==================== 步骤4: 高级权限 ====================

    private void detectElevatedPermissions() {
        // 检测 Shizuku
        new Thread(() -> {
            try {
                Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                Object pingResult = shizukuClass.getMethod("pingBinder").invoke(null);
                shizukuAvailable = (pingResult instanceof Boolean) && (Boolean) pingResult;
                if (shizukuAvailable) {
                    Object permResult = shizukuClass.getMethod("checkSelfPermission").invoke(null);
                    shizukuAuthorized = (permResult instanceof Integer) && ((Integer) permResult == 0);
                }
            } catch (Exception e) {
                shizukuAvailable = false;
            }
            runOnUiThread(this::refreshElevatedUI);
        }).start();

        // 检测 Root
        new Thread(() -> {
            rootAvailable = PermissionHelper.isRootAvailable();
            runOnUiThread(this::refreshElevatedUI);
        }).start();
    }

    private void refreshElevatedUI() {
        // Shizuku 状态
        if (shizukuAuthorized) {
            tvShizukuStatus.setText("已授权");
            btnShizuku.setText("已授权");
            btnShizuku.setEnabled(false);
            cardShizuku.setStrokeColor(ContextCompat.getColor(this, R.color.charging_green));
        } else if (shizukuAvailable) {
            tvShizukuStatus.setText("已安装，点击授权");
            btnShizuku.setText("授权");
            btnShizuku.setEnabled(true);
        } else {
            tvShizukuStatus.setText("未安装 Shizuku");
            btnShizuku.setText("不可用");
            btnShizuku.setEnabled(false);
        }

        // Root 状态
        if (rootAuthorized) {
            tvRootStatus.setText("已授权");
            btnRoot.setText("已授权");
            btnRoot.setEnabled(false);
            cardRoot.setStrokeColor(ContextCompat.getColor(this, R.color.charging_green));
        } else if (rootAvailable) {
            tvRootStatus.setText("已检测到 Root，点击授权");
            btnRoot.setText("授权");
            btnRoot.setEnabled(true);
        } else {
            tvRootStatus.setText("未检测到 Root");
            btnRoot.setText("不可用");
            btnRoot.setEnabled(false);
        }
    }

    private void onShizukuClicked() {
        if (!shizukuAvailable) {
            // 引导安装 Shizuku
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://shizuku.rikka.app/")));
            } catch (Exception e) {
                // 无法打开浏览器
            }
            return;
        }

        // 检查是否已授权
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Object permResult = shizukuClass.getMethod("checkSelfPermission").invoke(null);
            if ((permResult instanceof Integer) && ((Integer) permResult == 0)) {
                shizukuAuthorized = true;
                selectedElevatedMode = PermissionHelper.MODE_SHIZUKU;
                PermissionHelper.setElevatedMode(this, PermissionHelper.MODE_SHIZUKU);
                refreshElevatedUI();
                showStep(5);
                return;
            }

            Object rationaleResult = shizukuClass.getMethod("shouldShowRequestPermissionRationale").invoke(null);
            if ((rationaleResult instanceof Boolean) && (Boolean) rationaleResult) {
                // 用户之前拒绝过，显示说明
                tvShizukuStatus.setText("请在 Shizuku 中允许授权请求");
            }

            // 请求 Shizuku 授权
            shizukuClass.getMethod("requestPermission", int.class).invoke(null, REQUEST_SHIZUKU_PERMISSION);
        } catch (Exception e) {
            tvShizukuStatus.setText("授权请求失败: " + e.getMessage());
        }
    }

    private void onRootClicked() {
        if (!rootAvailable) {
            return;
        }

        // 测试 Root 权限
        new Thread(() -> {
            String result = PermissionHelper.executeViaRoot("id");
            boolean success = result != null && result.contains("uid=0");
            rootAuthorized = success;
            if (success) {
                selectedElevatedMode = PermissionHelper.MODE_ROOT;
                PermissionHelper.setElevatedMode(this, PermissionHelper.MODE_ROOT);
            }
            runOnUiThread(() -> {
                refreshElevatedUI();
                // Root 授权成功后自动进入下一步
                if (rootAuthorized) {
                    showStep(5);
                }
            });
        }).start();
    }

    private void onSkipElevated() {
        selectedElevatedMode = PermissionHelper.MODE_NONE;
        PermissionHelper.setElevatedMode(this, PermissionHelper.MODE_NONE);
        showStep(5);
    }

    // ==================== 步骤5: 完成 ====================

    private void buildSummary() {
        StringBuilder sb = new StringBuilder();

        // 通知权限
        sb.append(notificationGranted ? "✓" : "✗");
        sb.append(" 通知权限：");
        sb.append(notificationGranted ? "已授予" : "未授予");
        sb.append("\n\n");

        // 电池优化
        sb.append(batteryOptimizationIgnored ? "✓" : "✗");
        sb.append(" 电池优化白名单：");
        sb.append(batteryOptimizationIgnored ? "已加入" : "未加入");
        sb.append("\n\n");

        // 无限制后台
        sb.append(batteryOptimizationIgnored ? "✓" : "✗");
        sb.append(" 无限制后台：");
        sb.append(batteryOptimizationIgnored ? "已设置" : "未设置");
        sb.append("\n\n");

        // 高级权限
        sb.append("⚡ 高级权限：");
        switch (selectedElevatedMode) {
            case PermissionHelper.MODE_SHIZUKU:
                sb.append("Shizuku (ADB权限)");
                break;
            case PermissionHelper.MODE_ROOT:
                sb.append("Root (超级用户)");
                break;
            default:
                sb.append("标准权限（基础电池数据）");
                break;
        }

        tvPermissionSummary.setText(sb.toString());
    }

    private void finishWizard() {
        // 保存向导完成状态
        PermissionHelper.setWizardCompleted(this);
        PermissionHelper.setElevatedMode(this, selectedElevatedMode);

        // 跳转到主界面
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ==================== 权限状态刷新 ====================

    private void refreshPermissionStatus() {
        if (currentStep == 2) {
            refreshNotificationUI();
        } else if (currentStep == 3) {
            refreshBatteryUI();
        }
    }
}
