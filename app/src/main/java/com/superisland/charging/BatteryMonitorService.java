package com.superisland.charging;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.superisland.charging.data.BatteryDatabase;
import com.superisland.charging.settings.SettingsPreferences;

/**
 * 电池监控前台服务
 * <p>
 * 持续监控电池状态（电流、功率、温度、电量），
 * 实时更新超级岛通知。
 * <p>
 * 更新策略：
 * 1. 监听系统 ACTION_BATTERY_CHANGED 广播获取即时变化
 * 2. 每2秒定时轮询确保数据刷新频率
 */
public class BatteryMonitorService extends Service {

    private static final String TAG = "BatteryMonitor";
    private static final long DB_RECORD_INTERVAL_MS = 30000;   // 数据库记录间隔 30秒

    public static final String ACTION_STOP = "com.superisland.charging.STOP";

    private Handler handler;
    private Runnable updateRunnable;
    private BroadcastReceiver batteryReceiver;
    private boolean isRunning = false;

    // 数据库
    private BatteryDatabase database;
    private String sessionId;
    private long lastDbRecordTime = 0;

    // 电池数据缓存
    private float currentMa = 0f;
    private float powerW = 0f;
    private float voltageV = 0f;
    private float temperature = 0f;
    private int batteryLevel = 0;
    private int estimatedMinutes = -1;
    private boolean isCharging = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        // 初始化数据库和会话
        database = BatteryDatabase.getInstance(this);
        sessionId = "session_" + System.currentTimeMillis();

        registerBatteryReceiver();
        startForegroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            isRunning = true;
            startPeriodicUpdate();
        }

        // 如果被系统杀死，尝试重启
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            Log.w(TAG, "unregisterReceiver failed: " + e.getMessage());
        }
        super.onDestroy();
    }

    /**
     * 注册电池状态广播接收器
     */
    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    updateBatteryData(intent);
                    postNotification();
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
    }

    /**
     * 启动定时更新
     * <p>
     * 更新间隔从 SettingsPreferences 读取，支持用户自定义
     */
    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                // 主动获取最新电池数据
                updateBatteryDataFromSystem();
                postNotification();

                // 从设置读取更新间隔
                long interval = SettingsPreferences.getUpdateInterval(BatteryMonitorService.this);
                handler.postDelayed(this, interval);
            }
        };
        handler.post(updateRunnable);
    }

    /**
     * 从系统获取电池数据（定时轮询用）
     * <p>
     * 优先使用 Shizuku/Root 精确数据，降级到 BatteryManager API。
     * 电压从广播的EXTRA_VOLTAGE获取并缓存在voltageV字段中。
     * 温度同样从广播获取。
     */
    private void updateBatteryDataFromSystem() {
        String elevatedMode = PermissionHelper.getElevatedMode(this);
        boolean elevatedSuccess = false;

        // 尝试使用高级权限获取精确数据
        if (!PermissionHelper.MODE_NONE.equals(elevatedMode)) {
            elevatedSuccess = updateFromElevatedPermission();
        }

        // 如果高级权限失败或未启用，使用 BatteryManager API
        if (!elevatedSuccess) {
            updateFromBatteryManager();
        }
    }

    /**
     * 通过 Shizuku/Root 获取精确电池数据
     *
     * @return true 如果成功获取到至少部分数据
     */
    private boolean updateFromElevatedPermission() {
        boolean hasData = false;

        try {
            // 电流 (μA → mA)
            int currentUA = PermissionHelper.getCurrentNow(this);
            if (currentUA != Integer.MIN_VALUE) {
                currentMa = Math.abs(currentUA) / 1000f;
                hasData = true;
            }

            // 电压 (μV → V)
            int voltageUV = PermissionHelper.getVoltageNow(this);
            if (voltageUV > 0) {
                voltageV = voltageUV / 1000000f;
            }

            // 功率 = 电压 × 电流
            if (hasData) {
                powerW = voltageV * currentMa / 1000f;
            }

            // 电量百分比（通过电荷量精确计算）
            int chargeCounter = PermissionHelper.getChargeCounter(this);
            int chargeFull = PermissionHelper.getChargeFull(this);
            if (chargeFull > 0 && chargeCounter >= 0) {
                batteryLevel = (int) ((chargeCounter * 100L) / chargeFull);
                batteryLevel = Math.max(0, Math.min(100, batteryLevel));
                hasData = true;
            }

            // 温度 (十分之一摄氏度)
            int temp = PermissionHelper.getBatteryTemp(this);
            if (temp != -1) {
                temperature = temp / 10f;
            }

            // 预计充满时间
            if (isCharging && currentMa > 0 && chargeFull > 0 && chargeCounter >= 0) {
                int remainingUAh = chargeFull - chargeCounter;
                if (remainingUAh > 0) {
                    int currentUAbs = Math.abs(currentUA);
                    if (currentUAbs > 0) {
                        estimatedMinutes = (int) ((remainingUAh / (float) currentUAbs) * 60);
                    }
                } else {
                    estimatedMinutes = 0;
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Elevated permission data read failed: " + e.getMessage());
        }

        return hasData;
    }

    /**
     * 通过 BatteryManager API 获取电池数据（标准模式）
     */
    private void updateFromBatteryManager() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm == null) return;

        // 电流 (微安转毫安)
        int currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        currentMa = Math.abs(currentNow) / 1000f;

        // 功率 = 电压 × 电流（电压从广播缓存）
        powerW = voltageV * currentMa / 1000f;

        // 电量百分比
        int chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        // BATTERY_PROPERTY_CHARGE_FULL = 4 (API 21+)
        int chargeFull = bm.getIntProperty(4);
        if (chargeFull > 0 && chargeCounter >= 0) {
            batteryLevel = (int) ((chargeCounter * 100L) / chargeFull);
            batteryLevel = Math.max(0, Math.min(100, batteryLevel));
        } else {
            batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }

        // 充电状态
        int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
        isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);

        // 预计充满时间
        if (isCharging && currentMa > 0 && chargeFull > 0 && chargeCounter >= 0) {
            int remainingMah = chargeFull - chargeCounter;
            if (remainingMah > 0) {
                float remainingHours = remainingMah / currentMa;
                estimatedMinutes = (int) (remainingHours * 60);
            } else {
                estimatedMinutes = 0;
            }
        } else {
            estimatedMinutes = -1;
        }
    }

    /**
     * 从广播Intent更新电池数据（温度等广播独有数据）
     */
    private void updateBatteryData(Intent batteryStatus) {
        // 温度 (十分之一摄氏度转摄氏度)
        int tempExtra = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (tempExtra != -1) {
            temperature = tempExtra / 10f;
        }

        // 充电状态
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);

        // 电流 (EXTRA_CURRENT = "current" API 21+)
        int currentNow = batteryStatus.getIntExtra("current", 0);
        currentMa = Math.abs(currentNow) / 1000f;

        // 电压 (毫伏转伏) — 缓存到类字段供定时轮询使用
        int voltageNow = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        if (voltageNow > 0) {
            voltageV = voltageNow / 1000f;
        }

        // 功率 = 电压 × 电流
        powerW = voltageV * currentMa / 1000f;

        // 电量
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level >= 0 && scale > 0) {
            batteryLevel = (int) ((level * 100L) / scale);
        }

        // 预计充满时间
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null && isCharging && currentMa > 0) {
            int capacity = bm.getIntProperty(4); // BATTERY_PROPERTY_CHARGE_FULL
            int chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (capacity > 0 && chargeCounter >= 0) {
                int remainingMah = capacity - chargeCounter;
                if (remainingMah > 0) {
                    estimatedMinutes = (int) ((remainingMah / currentMa) * 60);
                } else {
                    estimatedMinutes = 0;
                }
            }
        } else {
            estimatedMinutes = -1;
        }
    }

    /**
     * 发送/更新通知
     */
    private void postNotification() {
        Notification notification;

        // 检测是否支持超级岛
        int protocolVersion = IslandNotificationHelper.getFocusProtocolVersion(this);
        if (protocolVersion >= 3 && IslandNotificationHelper.isSupportIsland(this)) {
            notification = IslandNotificationHelper.buildIslandNotification(
                    this, currentMa, powerW, temperature,
                    batteryLevel, estimatedMinutes, isCharging);
        } else {
            notification = IslandNotificationHelper.buildFallbackNotification(
                    this, currentMa, powerW, temperature,
                    batteryLevel, estimatedMinutes, isCharging);
        }

        startForeground(IslandNotificationHelper.NOTIFICATION_ID, notification);

        // 定时写入数据库（每30秒记录一次，避免数据过多）
        long now = System.currentTimeMillis();
        if (now - lastDbRecordTime >= DB_RECORD_INTERVAL_MS) {
            lastDbRecordTime = now;
            recordToDatabase();
        }
    }

    /**
     * 将当前电池数据写入数据库
     */
    private void recordToDatabase() {
        try {
            database.insertChargeRecord(
                    sessionId,
                    batteryLevel,
                    currentMa,
                    powerW,
                    voltageV,
                    temperature,
                    isCharging
            );
        } catch (Exception e) {
            Log.w(TAG, "Failed to record to database: " + e.getMessage());
        }
    }

    /**
     * 启动前台服务通知（初始）
     */
    private void startForegroundNotification() {
        Notification notification = IslandNotificationHelper.buildFallbackNotification(
                this, 0, 0, 0, 0, -1, false);
        startForeground(IslandNotificationHelper.NOTIFICATION_ID, notification);
    }
}
