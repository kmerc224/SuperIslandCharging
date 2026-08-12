package com.superisland.charging.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 设置偏好管理
 * <p>
 * 管理所有用户设置项：
 * - 桌面图标显示
 * - 超级岛自定义（收起态/展开态）
 * - 其他应用设置
 */
public class SettingsPreferences {

    private static final String PREFS_NAME = "settings_prefs";

    // ==================== 桌面图标 ====================
    private static final String KEY_SHOW_LAUNCHER_ICON = "show_launcher_icon";

    // ==================== 超级岛 - 收起态 ====================
    private static final String KEY_COLLAPSED_LEFT_DATA = "collapsed_left_data";
    private static final String KEY_COLLAPSED_RIGHT_DATA = "collapsed_right_data";

    // ==================== 超级岛 - 展开态 ====================
    private static final String KEY_EXPANDED_ITEMS_ORDER = "expanded_items_order";

    // 数据选项常量
    public static final String DATA_POWER = "power";
    public static final String DATA_TEMPERATURE = "temperature";
    public static final String DATA_CURRENT = "current";
    public static final String DATA_BATTERY_LEVEL = "battery_level";
    public static final String DATA_VOLTAGE = "voltage";
    public static final String DATA_TIME = "time";

    // 默认值
    private static final String DEFAULT_COLLAPSED_LEFT = DATA_POWER;
    private static final String DEFAULT_COLLAPSED_RIGHT = DATA_TEMPERATURE;

    private static final List<String> DEFAULT_EXPANDED_ORDER = Arrays.asList(
            DATA_CURRENT, DATA_POWER, DATA_TIME, DATA_BATTERY_LEVEL
    );

    /**
     * 获取所有可自定义的数据选项（用于 Spinner / 列表显示）
     */
    public static List<String[]> getAllDataOptions() {
        return Arrays.asList(
                new String[]{DATA_POWER, "功率 (W)"},
                new String[]{DATA_TEMPERATURE, "温度 (°C)"},
                new String[]{DATA_CURRENT, "电流 (mA)"},
                new String[]{DATA_BATTERY_LEVEL, "电量 (%)"},
                new String[]{DATA_VOLTAGE, "电压 (V)"},
                new String[]{DATA_TIME, "预计时间"}
        );
    }

    /**
     * 根据 key 获取显示名称
     */
    public static String getDataLabel(String key) {
        for (String[] option : getAllDataOptions()) {
            if (option[0].equals(key)) return option[1];
        }
        return key;
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ==================== 桌面图标 ====================

    public static boolean isShowLauncherIcon(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_LAUNCHER_ICON, true);
    }

    public static void setShowLauncherIcon(Context context, boolean show) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_LAUNCHER_ICON, show).apply();
    }

    // ==================== 收起态 ====================

    public static String getCollapsedLeftData(Context context) {
        return getPrefs(context).getString(KEY_COLLAPSED_LEFT_DATA, DEFAULT_COLLAPSED_LEFT);
    }

    public static void setCollapsedLeftData(Context context, String dataKey) {
        getPrefs(context).edit().putString(KEY_COLLAPSED_LEFT_DATA, dataKey).apply();
    }

    public static String getCollapsedRightData(Context context) {
        return getPrefs(context).getString(KEY_COLLAPSED_RIGHT_DATA, DEFAULT_COLLAPSED_RIGHT);
    }

    public static void setCollapsedRightData(Context context, String dataKey) {
        getPrefs(context).edit().putString(KEY_COLLAPSED_RIGHT_DATA, dataKey).apply();
    }

    // ==================== 展开态 ====================

    public static List<String> getDefaultExpandedOrder() {
        return new ArrayList<>(DEFAULT_EXPANDED_ORDER);
    }

    public static List<String> getExpandedItemsOrder(Context context) {
        String stored = getPrefs(context).getString(KEY_EXPANDED_ITEMS_ORDER, null);
        if (stored != null && !stored.isEmpty()) {
            return new ArrayList<>(Arrays.asList(stored.split(",")));
        }
        return new ArrayList<>(DEFAULT_EXPANDED_ORDER);
    }

    public static void setExpandedItemsOrder(Context context, List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(order.get(i));
        }
        getPrefs(context).edit().putString(KEY_EXPANDED_ITEMS_ORDER, sb.toString()).apply();
    }
}
