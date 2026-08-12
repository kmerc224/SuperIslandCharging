package com.superisland.charging;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.superisland.charging.log.LogCapture;
import com.superisland.charging.settings.SettingsActivity;
import com.superisland.charging.ui.BatteryInfoFragment;
import com.superisland.charging.ui.ChargingRecordsFragment;

/**
 * 主界面
 * <p>
 * 顶部工具栏含设置按钮（右上角），
 * 底部导航栏包含两个板块：
 * - 电池信息：实时电池状态 + 电池健康信息
 * - 充电记录：电量/功率/温度折线图
 */
public class MainActivity extends AppCompatActivity {

    private BatteryInfoFragment batteryInfoFragment;
    private ChargingRecordsFragment chargingRecordsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 首次使用检测：未完成向导则跳转到设置向导
        if (!PermissionHelper.isWizardCompleted(this)) {
            Intent wizardIntent = new Intent(this, SetupWizardActivity.class);
            startActivity(wizardIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        LogCapture.getInstance().info("MainActivity", "App opened");

        initFragments();
        setupBottomNavigation();
        setupToolbar();
    }

    private void setupToolbar() {
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            LogCapture.getInstance().info("MainActivity", "Settings button clicked");
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    private void initFragments() {
        batteryInfoFragment = new BatteryInfoFragment();
        chargingRecordsFragment = new ChargingRecordsFragment();

        // 默认显示电池信息
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, chargingRecordsFragment, "records")
                .hide(chargingRecordsFragment)
                .add(R.id.fragment_container, batteryInfoFragment, "info")
                .commit();

        activeFragment = batteryInfoFragment;
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_battery_info) {
                switchFragment(batteryInfoFragment);
                return true;
            } else if (itemId == R.id.nav_charging_records) {
                switchFragment(chargingRecordsFragment);
                return true;
            }
            return false;
        });
    }

    private void switchFragment(Fragment target) {
        if (target != activeFragment) {
            getSupportFragmentManager().beginTransaction()
                    .hide(activeFragment)
                    .show(target)
                    .commit();
            activeFragment = target;
        }
    }
}
