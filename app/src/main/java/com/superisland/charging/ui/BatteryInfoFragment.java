package com.superisland.charging.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.superisland.charging.PermissionHelper;
import com.superisland.charging.R;
import com.superisland.charging.data.BatteryDatabase;

/**
 * 电池信息面板 Fragment
 * <p>
 * 展示实时电池状态和电池健康信息：
 * - 实时：电量、电流、功率、温度、电压
 * - 健康：出厂容量、测算实际容量、健康度百分比
 * - 充电：预计充满时间、数据模式
 */
public class BatteryInfoFragment extends Fragment {

    // 实时状态
    private MaterialTextView tvBatteryLevel;
    private Chip chipStatus;
    private MaterialTextView tvCurrent;
    private MaterialTextView tvPower;
    private MaterialTextView tvTemperature;
    private MaterialTextView tvVoltage;

    // 电池健康
    private MaterialTextView tvHealthPercent;
    private Chip chipHealthLevel;
    private LinearProgressIndicator progressHealth;
    private MaterialTextView tvFactoryCapacity;
    private MaterialTextView tvActualCapacity;

    // 充电信息
    private MaterialTextView tvEstimatedTime;
    private MaterialTextView tvDataMode;

    private Handler handler;
    private Runnable refreshRunnable;
    private BroadcastReceiver batteryReceiver;
    private BatteryDatabase database;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battery_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = BatteryDatabase.getInstance(requireContext());
        handler = new Handler(Looper.getMainLooper());

        initViews(view);
        setupListeners();
        registerBatteryReceiver();
        startAutoRefresh();
    }

    private void initViews(View view) {
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        chipStatus = view.findViewById(R.id.chip_status);
        tvCurrent = view.findViewById(R.id.tv_current);
        tvPower = view.findViewById(R.id.tv_power);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvVoltage = view.findViewById(R.id.tv_voltage);

        tvHealthPercent = view.findViewById(R.id.tv_health_percent);
        chipHealthLevel = view.findViewById(R.id.chip_health_level);
        progressHealth = view.findViewById(R.id.progress_health);
        tvFactoryCapacity = view.findViewById(R.id.tv_factory_capacity);
        tvActualCapacity = view.findViewById(R.id.tv_actual_capacity);

        tvEstimatedTime = view.findViewById(R.id.tv_estimated_time);
        tvDataMode = view.findViewById(R.id.tv_data_mode);
    }

    private void setupListeners() {
        requireView().findViewById(R.id.btn_set_factory_capacity).setOnClickListener(v -> showSetCapacityDialog());
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    updateRealtimeData(intent);
                }
            }
        };
        requireContext().registerReceiver(batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                updateHealthInfo();
                updateChargingInfo();
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(refreshRunnable);
    }

    // ==================== 实时数据更新 ====================

    private void updateRealtimeData(Intent batteryStatus) {
        // 电量
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = level >= 0 && scale > 0 ? (level * 100 / scale) : 0;
        tvBatteryLevel.setText(String.valueOf(percent));

        // 充电状态
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        if (status == BatteryManager.BATTERY_STATUS_FULL) {
            chipStatus.setText("已充满");
        } else if (isCharging) {
            chipStatus.setText("充电中");
        } else {
            chipStatus.setText("放电中");
        }

        // 电流
        int currentNow = batteryStatus.getIntExtra(BatteryManager.EXTRA_CURRENT, 0);
        tvCurrent.setText(String.format("%.0f", Math.abs(currentNow) / 1000f));

        // 电压
        int voltageNow = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        float voltageV = voltageNow / 1000f;
        tvVoltage.setText(String.format("%.2f", voltageV));

        // 功率
        float powerW = voltageV * Math.abs(currentNow) / 1000000f;
        tvPower.setText(String.format("%.1f", powerW));

        // 温度
        int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (temp != -1) {
            tvTemperature.setText(String.format("%.0f", temp / 10f));
        }
    }

    // ==================== 电池健康信息 ====================

    private void updateHealthInfo() {
        // 出厂容量
        float factoryCapacity = database.getFactoryCapacity();
        if (factoryCapacity > 0) {
            tvFactoryCapacity.setText(String.format("%.0f mAh", factoryCapacity));
        } else {
            tvFactoryCapacity.setText("未设置");
        }

        // 测算实际容量
        float actualCapacity = database.estimateActualCapacity();
        if (actualCapacity > 0) {
            tvActualCapacity.setText(String.format("%.0f mAh", actualCapacity));
        } else {
            tvActualCapacity.setText("测算中...");
        }

        // 健康度
        float health = database.calculateBatteryHealth();
        if (health > 0) {
            tvHealthPercent.setText(String.format("%.1f%%", health));
            progressHealth.setProgress((int) health);

            if (health >= 90) {
                chipHealthLevel.setText("优秀");
            } else if (health >= 80) {
                chipHealthLevel.setText("良好");
            } else if (health >= 70) {
                chipHealthLevel.setText("一般");
            } else {
                chipHealthLevel.setText("建议更换");
            }
        } else {
            tvHealthPercent.setText("--%");
            progressHealth.setProgress(0);
            chipHealthLevel.setText("待测算");
        }
    }

    // ==================== 充电信息 ====================

    private void updateChargingInfo() {
        // 数据模式
        String mode = PermissionHelper.getElevatedMode(requireContext());
        switch (mode) {
            case PermissionHelper.MODE_SHIZUKU:
                tvDataMode.setText("Shizuku");
                break;
            case PermissionHelper.MODE_ROOT:
                tvDataMode.setText("Root");
                break;
            default:
                tvDataMode.setText("标准");
                break;
        }

        // 预计充满时间（从 BatteryManager 获取）
        BatteryManager bm = (BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int chargeTime = -1;
            // Android 15+ 可能有 BATTERY_PROPERTY_CHARGE_TIME 相关属性
            // 暂时使用简单估算
            int currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            int chargeFull = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_FULL);
            int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);

            if (status == BatteryManager.BATTERY_STATUS_CHARGING
                    && currentNow > 0 && chargeFull > chargeCounter) {
                float remainingHours = (chargeFull - chargeCounter) / (float) currentNow;
                int minutes = (int) (remainingHours * 60);
                tvEstimatedTime.setText(String.format("%02d:%02d", minutes / 60, minutes % 60));
            } else {
                tvEstimatedTime.setText("--:--");
            }
        }
    }

    // ==================== 设置出厂容量对话框 ====================

    private void showSetCapacityDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("例如: 5000");

        float current = database.getFactoryCapacity();
        if (current > 0) {
            input.setText(String.valueOf((int) current));
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("设置出厂容量")
                .setMessage("请输入电池出厂容量 (mAh)\n可在手机参数或电池标签上找到")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            float capacity = Float.parseFloat(text);
                            if (capacity > 0) {
                                database.setFactoryCapacity(capacity);
                                updateHealthInfo();
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
        if (batteryReceiver != null) {
            try {
                requireContext().unregisterReceiver(batteryReceiver);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
