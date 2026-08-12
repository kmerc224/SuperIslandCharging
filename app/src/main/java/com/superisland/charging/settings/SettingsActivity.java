package com.superisland.charging.settings;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.superisland.charging.PermissionCenterActivity;
import com.superisland.charging.R;
import com.superisland.charging.log.LogCapture;

/**
 * 设置页面
 * <p>
 * 提供以下设置项：
 * - 桌面图标显示开关
 * - 权限中心入口
 * - 超级岛自定义入口
 * - 运行日志入口
 * - 关于页面入口
 */
public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchLauncherIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        LogCapture.getInstance().info("Settings", "Settings opened");

        setupToolbar();
        setupLauncherIconSwitch();
        setupNavigationRows();
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
     * 初始化桌面图标开关
     * 从 SettingsPreferences 读取当前状态，并在切换时通过 PackageManager 启用/禁用 LauncherAlias
     */
    private void setupLauncherIconSwitch() {
        switchLauncherIcon = findViewById(R.id.switch_launcher_icon);

        // 读取当前设置
        boolean showIcon = SettingsPreferences.isShowLauncherIcon(this);
        switchLauncherIcon.setChecked(showIcon);

        switchLauncherIcon.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存设置
            SettingsPreferences.setShowLauncherIcon(this, isChecked);

            // 通过 PackageManager 启用/禁用桌面图标组件
            ComponentName launcherAlias = new ComponentName(
                    this,
                    getPackageName() + ".LauncherAlias"
            );

            int newState = isChecked
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

            getPackageManager().setComponentEnabledSetting(
                    launcherAlias,
                    newState,
                    PackageManager.DONT_KILL_APP
            );

            LogCapture.getInstance().info("Settings",
                    "Launcher icon " + (isChecked ? "shown" : "hidden"));
        });
    }

    /**
     * 设置各设置项的点击导航
     */
    private void setupNavigationRows() {
        // 权限中心
        findViewById(R.id.row_permission_center).setOnClickListener(v -> {
            LogCapture.getInstance().info("Settings", "Navigate to PermissionCenter");
            Intent intent = new Intent(this, PermissionCenterActivity.class);
            startActivity(intent);
        });

        // 超级岛自定义
        findViewById(R.id.row_island_custom).setOnClickListener(v -> {
            LogCapture.getInstance().info("Settings", "Navigate to IslandCustomization");
            Intent intent = new Intent(this, IslandCustomizationActivity.class);
            startActivity(intent);
        });

        // 运行日志
        findViewById(R.id.row_log_viewer).setOnClickListener(v -> {
            LogCapture.getInstance().info("Settings", "Navigate to LogViewer");
            Intent intent = new Intent(this, com.superisland.charging.LogViewerActivity.class);
            startActivity(intent);
        });

        // 关于
        findViewById(R.id.row_about).setOnClickListener(v -> {
            LogCapture.getInstance().info("Settings", "Navigate to About");
            Intent intent = new Intent(this, com.superisland.charging.AboutActivity.class);
            startActivity(intent);
        });
    }
}
