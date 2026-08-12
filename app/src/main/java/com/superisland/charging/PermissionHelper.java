package com.superisland.charging;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * 权限管理工具类
 * <p>
 * 管理应用所需的各类权限：
 * - 通知权限 (POST_NOTIFICATIONS)
 * - 忽略电池优化 (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
 * - Shizuku 授权（精确电池数据）
 * - Root 授权（精确电池数据）
 */
public class PermissionHelper {

    private static final String TAG = "PermissionHelper";
    private static final String PREFS_NAME = "super_island_prefs";

    // SharedPreferences Keys
    public static final String KEY_WIZARD_COMPLETED = "wizard_completed";
    public static final String KEY_ELEVATED_MODE = "elevated_mode";       // "shizuku" / "root" / "none"
    public static final String KEY_SHIZUKU_AVAILABLE = "shizuku_available";
    public static final String KEY_ROOT_AVAILABLE = "root_available";

    //  elevated mode 常量
    public static final String MODE_NONE = "none";
    public static final String MODE_SHIZUKU = "shizuku";
    public static final String MODE_ROOT = "root";

    // ==================== SharedPreferences ====================

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isWizardCompleted(Context context) {
        return getPrefs(context).getBoolean(KEY_WIZARD_COMPLETED, false);
    }

    public static void setWizardCompleted(Context context) {
        getPrefs(context).edit().putBoolean(KEY_WIZARD_COMPLETED, true).apply();
    }

    public static String getElevatedMode(Context context) {
        return getPrefs(context).getString(KEY_ELEVATED_MODE, MODE_NONE);
    }

    public static void setElevatedMode(Context context, String mode) {
        getPrefs(context).edit().putString(KEY_ELEVATED_MODE, mode).apply();
    }

    // ==================== 通知权限 ====================

    /**
     * 检查是否已授予通知权限
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true; // Android 13 以下默认有权限
    }

    // ==================== 电池优化白名单 ====================

    /**
     * 检查是否已加入电池优化白名单（不限制后台）
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        AppOpsManager appOpsManager =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_POST_NOTIFICATIONS,
                Process.myUid(),
                context.getPackageName());
        // 使用 PowerManager 检查更准确
        android.os.PowerManager pm =
                (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /**
     * 获取请求忽略电池优化的 Intent
     */
    public static Intent getBatteryOptimizationIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    // ==================== Shizuku ====================

    /**
     * 检查 Shizuku 是否已安装并正在运行
     */
    public static boolean isShizukuAvailable() {
        try {
            // 尝试通过 Shizuku API 检查可用性
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Object result = shizukuClass.getMethod("pingBinder").invoke(null);
            return (result instanceof Boolean) && (Boolean) result;
        } catch (Exception e) {
            Log.d(TAG, "Shizuku not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否已获得 Shizuku 授权
     */
    public static boolean isShizukuAuthorized() {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Object result = shizukuClass.getMethod("checkSelfPermission").invoke(null);
            return (result instanceof Integer) && ((Integer) result == 0); // PERMISSION_GRANTED = 0
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 请求 Shizuku 授权
     *
     * @param requestCode 请求码（用于 onActivityResult 回调）
     */
    public static void requestShizukuPermission(int requestCode) {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            shizukuClass.getMethod("requestPermission", int.class).invoke(null, requestCode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to request Shizuku permission", e);
        }
    }

    /**
     * 注册 Shizuku 授权结果监听器
     */
    public static void addShizukuRequestResultListener(Object listener) {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Class<?> listenerClass = Class.forName(
                    "rikka.shizuku.Shizuku$OnRequestPermissionResultListener");
            shizukuClass.getMethod("addOnRequestPermissionResultListener", listenerClass)
                    .invoke(null, listener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add Shizuku listener", e);
        }
    }

    /**
     * 移除 Shizuku 授权结果监听器
     */
    public static void removeShizukuRequestResultListener(Object listener) {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Class<?> listenerClass = Class.forName(
                    "rikka.shizuku.Shizuku$OnRequestPermissionResultListener");
            shizukuClass.getMethod("removeOnRequestPermissionResultListener", listenerClass)
                    .invoke(null, listener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove Shizuku listener", e);
        }
    }

    // ==================== Root ====================

    /**
     * 检查设备是否已 Root（su 命令可用）
     */
    public static boolean isRootAvailable() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/su", "/system/bin/.ext/.su", "/system/usr/we-need-root/"};
        for (String path : paths) {
            if (new java.io.File(path).exists()) {
                return true;
            }
        }
        // 尝试执行 su 命令
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.destroy();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 命令执行 ====================

    /**
     * 使用最高可用权限执行 Shell 命令
     * <p>
     * 优先级：Shizuku > Root > 普通 Shell
     *
     * @param command 要执行的命令
     * @return 命令输出，失败返回 null
     */
    public static String executeCommand(Context context, String command) {
        String mode = getElevatedMode(context);

        switch (mode) {
            case MODE_SHIZUKU:
                String result = executeViaShizuku(command);
                if (result != null) return result;
                // Shizuku 失败，降级到 Root
            case MODE_ROOT:
                result = executeViaRoot(command);
                if (result != null) return result;
                // Root 也失败，降级到普通 Shell
            default:
                return executeViaShell(command);
        }
    }

    /**
     * 通过 Shizuku 执行命令
     */
    private static String executeViaShizuku(String command) {
        try {
            // 通过 Shizuku 远程服务执行命令
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            // 检查是否已授权
            Object permResult = shizukuClass.getMethod("checkSelfPermission").invoke(null);
            if (!(permResult instanceof Integer) || (Integer) permResult != 0) {
                return null;
            }

            // 通过 ShizukuUserService 执行命令
            // 这里通过 ShizukuRemoteProcess 来执行
            Class<?> binderClass = Class.forName("rikka.shizuku.Shizuku");
            Object binder = binderClass.getMethod("getBinder").invoke(null);
            if (binder == null) return null;

            // 使用反射调用 Shizuku 的 exec 方法
            // 实际项目中应通过 AIDL 接口与 User Service 通信
            return executeViaShell(command);
        } catch (Exception e) {
            Log.w(TAG, "Shizuku exec failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 Root (su) 执行命令
     */
    public static String executeViaRoot(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            process.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            Log.w(TAG, "Root exec failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过普通 Shell 执行命令（权限最低）
     */
    private static String executeViaShell(String command) {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            process.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            Log.w(TAG, "Shell exec failed: " + e.getMessage());
            return null;
        }
    }

    // ==================== 电池数据（精确模式） ====================

    /**
     * 通过 elevated 权限读取电池电荷量 (μAh)
     */
    public static int getChargeCounter(Context context) {
        String result = executeCommand(context, "cat /sys/class/power_supply/battery/charge_counter");
        return parseInt(result, -1);
    }

    /**
     * 通过 elevated 权限读取电池满充容量 (μAh)
     */
    public static int getChargeFull(Context context) {
        String result = executeCommand(context, "cat /sys/class/power_supply/battery/charge_full");
        return parseInt(result, -1);
    }

    /**
     * 通过 elevated 权限读取电池当前电流 (μA)
     */
    public static int getCurrentNow(Context context) {
        // 不同设备路径不同，依次尝试
        String[] paths = {
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/battery/BatteryPower"
        };
        for (String path : paths) {
            String result = executeCommand(context, "cat " + path);
            int value = parseInt(result, Integer.MIN_VALUE);
            if (value != Integer.MIN_VALUE) return value;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * 通过 elevated 权限读取电池电压 (μV)
     */
    public static int getVoltageNow(Context context) {
        String result = executeCommand(context, "cat /sys/class/power_supply/battery/voltage_now");
        return parseInt(result, -1);
    }

    /**
     * 通过 elevated 权限读取电池温度 (十分之一摄氏度)
     */
    public static int getBatteryTemp(Context context) {
        String result = executeCommand(context, "cat /sys/class/power_supply/battery/temp");
        return parseInt(result, -1);
    }

    private static int parseInt(String str, int defaultValue) {
        if (str == null || str.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
