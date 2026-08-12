package com.superisland.charging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import com.superisland.charging.settings.SettingsPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 超级岛通知构建助手
 * <p>
 * 负责构建小米澎湃OS3超级岛（焦点通知）的JSON参数，
 * 并组装完整的Notification对象。
 * <p>
 * 收起态（摘要态/大岛）：左侧显示功率(W)，右侧显示温度
 * 展开态（焦点通知）：显示电流(mA)、功率(W)、预计充电时间(hh:mm) + 底部电量进度条
 */
public class IslandNotificationHelper {

    private static final String TAG = "IslandHelper";

    // 岛通知扩展参数 Key
    public static final String EXTRA_MIUI_FOCUS_PARAM = "miui.focus.param";
    public static final String EXTRA_MIUI_FOCUS_PICS = "miui.focus.pics";
    public static final String EXTRA_MIUI_FOCUS_ACTIONS = "miui.focus.actions";

    // 通知相关常量
    public static final String CHANNEL_ID = "super_island_charging";
    public static final String CHANNEL_NAME = "充电监控";
    public static final int NOTIFICATION_ID = 10001;

    /**
     * 查询当前系统是否支持岛功能
     */
    public static boolean isSupportIsland(Context context) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getDeclaredMethod("getBoolean", String.class, boolean.class);
            Object result = method.invoke(null, "persist.sys.feature.island", false);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception e) {
            Log.w(TAG, "isSupportIsland check failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * 查询当前OS是否支持焦点通知及版本
     *
     * @return 0=不支持, 1=OS1, 2=OS2, 3=OS3(超级岛)
     */
    public static int getFocusProtocolVersion(Context context) {
        try {
            return Settings.System.getInt(
                    context.getContentResolver(),
                    "notification_focus_protocol", 0);
        } catch (Exception e) {
            Log.w(TAG, "getFocusProtocolVersion failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 查询当前应用是否开启焦点通知权限
     */
    public static boolean hasFocusPermission(Context context) {
        try {
            android.net.Uri uri = android.net.Uri.parse(
                    "content://miui.statusbar.notification.public");
            Bundle extras = new Bundle();
            extras.putString("package", context.getPackageName());
            Bundle bundle = context.getContentResolver().call(uri, "canShowFocus", null, extras);
            if (bundle != null) {
                return bundle.getBoolean("canShowFocus", false);
            }
        } catch (Exception e) {
            Log.w(TAG, "hasFocusPermission check failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * 构建超级岛通知
     *
     * @param context          上下文
     * @param currentMa        电流 (mA)
     * @param powerW           功率 (W)
     * @param temperature      温度 (°C)
     * @param batteryLevel     电池电量百分比 (0-100)
     * @param estimatedMinutes 预计充满时间 (分钟, -1表示未知)
     * @param isCharging       是否正在充电
     * @return 构建好的Notification对象
     */
    public static Notification buildIslandNotification(
            Context context,
            float currentMa,
            float powerW,
            float temperature,
            int batteryLevel,
            int estimatedMinutes,
            boolean isCharging) {

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 创建通知渠道
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("充电监控超级岛通知");
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);

        // 构建岛通知JSON参数
        String islandParams = buildIslandJson(
                context, currentMa, powerW, temperature, batteryLevel, estimatedMinutes, isCharging);

        // 构建基础通知
        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(isCharging ? "充电中" : "电池监控")
                .setContentText(String.format("电量 %d%% | %.1fW", batteryLevel, powerW))
                .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_charging))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false);

        // 构建图片Bundle
        Bundle pics = new Bundle();
        pics.putParcelable("miui.focus.pic_island_icon",
                Icon.createWithResource(context, R.drawable.ic_charging));
        pics.putParcelable("miui.focus.pic_aod",
                Icon.createWithResource(context, R.drawable.ic_charging));

        // 构建Action Bundle
        Bundle actions = new Bundle();
        Intent stopIntent = new Intent(context, BatteryMonitorService.class);
        stopIntent.setAction(BatteryMonitorService.ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                context, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_stop),
                "停止", stopPendingIntent).build();
        actions.putParcelable("miui.focus.action_stop", stopAction);

        // 添加超级岛扩展参数
        // 按照小米官方文档：
        // 1. 使用 addExtras 添加图片和Action的Bundle（Parcelable数据）
        Bundle extras = new Bundle();
        extras.putBundle(EXTRA_MIUI_FOCUS_PICS, pics);
        extras.putBundle(EXTRA_MIUI_FOCUS_ACTIONS, actions);
        builder.addExtras(extras);

        Notification notification = builder.build();

        // 2. 直接在 notification.extras 中放入JSON字符串参数
        notification.extras.putString(EXTRA_MIUI_FOCUS_PARAM, islandParams);

        return notification;
    }

    /**
     * 构建超级岛JSON参数
     * <p>
     * 结构说明：
     * - param_island.bigIslandArea: 摘要态大岛（收起时左侧功率、右侧温度）
     * - param_island.smallIslandArea: 摘要态小岛（充电图标+环形进度）
     * - baseInfo: 焦点通知/展开态（电流、功率、预计时间）
     * - progressInfo: 展开态底部进度条（电池电量）
     */
    private static String buildIslandJson(
            Context context,
            float currentMa, float powerW, float temperature,
            int batteryLevel, int estimatedMinutes, boolean isCharging) {

        try {
            JSONObject root = new JSONObject();
            JSONObject paramV2 = new JSONObject();

            // === 基本属性 ===
            paramV2.put("protocol", 1);
            paramV2.put("business", "charging_monitor");
            paramV2.put("updatable", true);
            paramV2.put("enableFloat", true);
            paramV2.put("islandFirstFloat", true);

            // === 状态栏焦点信息 (OS2兼容) ===
            paramV2.put("ticker", String.format("充电中 %.1fW %d%%", powerW, batteryLevel));
            paramV2.put("tickerPic", "miui.focus.pic_island_icon");

            // === 息屏AOD数据 ===
            paramV2.put("aodTitle", String.format("%d%% %.1fW", batteryLevel, powerW));
            paramV2.put("aodPic", "miui.focus.pic_island_icon");

            // === 岛属性数据 (摘要态) ===
            JSONObject paramIsland = new JSONObject();
            paramIsland.put("islandProperty", 1);
            paramIsland.put("islandTimeout", 86400);

            // --- 大岛区域（收起态 - 支持自定义） ---
            JSONObject bigIslandArea = new JSONObject();

            // 读取用户自定义的收起态左右数据
            String leftDataKey = SettingsPreferences.getCollapsedLeftData(context);
            String rightDataKey = SettingsPreferences.getCollapsedRightData(context);

            // 左侧数据
            String[] leftFormatted = formatDataValue(leftDataKey, currentMa, powerW,
                    temperature, batteryLevel, estimatedMinutes, isCharging);
            JSONObject imageTextInfoLeft = new JSONObject();
            imageTextInfoLeft.put("type", 5);
            JSONObject aPicInfo = new JSONObject();
            aPicInfo.put("type", 1);
            aPicInfo.put("pic", "miui.focus.pic_island_icon");
            imageTextInfoLeft.put("picInfo", aPicInfo);
            JSONObject aTextInfo = new JSONObject();
            aTextInfo.put("frontTitle", leftFormatted[0]); // 标签
            aTextInfo.put("title", leftFormatted[1]);      // 数值
            aTextInfo.put("content", leftFormatted[2]);     // 单位
            aTextInfo.put("showHighlightColor", true);
            imageTextInfoLeft.put("textInfo", aTextInfo);
            bigIslandArea.put("imageTextInfoLeft", imageTextInfoLeft);

            // 右侧数据
            String[] rightFormatted = formatDataValue(rightDataKey, currentMa, powerW,
                    temperature, batteryLevel, estimatedMinutes, isCharging);
            JSONObject imageTextInfoRight = new JSONObject();
            imageTextInfoRight.put("type", 2);
            JSONObject bPicInfo = new JSONObject();
            bPicInfo.put("type", 1);
            bPicInfo.put("pic", "miui.focus.pic_island_icon");
            imageTextInfoRight.put("picInfo", bPicInfo);
            JSONObject bTextInfo = new JSONObject();
            bTextInfo.put("frontTitle", rightFormatted[0]);
            bTextInfo.put("title", rightFormatted[1]);
            bTextInfo.put("content", rightFormatted[2]);
            bTextInfo.put("showHighlightColor", false);
            imageTextInfoRight.put("textInfo", bTextInfo);
            bigIslandArea.put("imageTextInfoRight", imageTextInfoRight);

            paramIsland.put("bigIslandArea", bigIslandArea);

            // --- 小岛区域 ---
            JSONObject smallIslandArea = new JSONObject();
            JSONObject combinePicInfo = new JSONObject();
            JSONObject sPicInfo = new JSONObject();
            sPicInfo.put("type", 1);
            sPicInfo.put("pic", "miui.focus.pic_island_icon");
            combinePicInfo.put("picInfo", sPicInfo);
            JSONObject sProgressInfo = new JSONObject();
            sProgressInfo.put("progress", batteryLevel);
            sProgressInfo.put("colorReach", "#4CAF50");
            sProgressInfo.put("colorUnReach", "#424242");
            sProgressInfo.put("isCCW", false);
            combinePicInfo.put("progressInfo", sProgressInfo);
            smallIslandArea.put("combinePicInfo", combinePicInfo);
            paramIsland.put("smallIslandArea", smallIslandArea);

            // --- 分享数据 ---
            JSONObject shareData = new JSONObject();
            shareData.put("pic", "miui.focus.pic_island_icon");
            shareData.put("title", "充电监控");
            shareData.put("content", String.format("电量%d%% 功率%.1fW", batteryLevel, powerW));
            shareData.put("shareContent", String.format(
                    "正在充电：电量%d%%，功率%.1fW，温度%.0f°C",
                    batteryLevel, powerW, temperature));
            paramIsland.put("shareData", shareData);

            paramV2.put("param_island", paramIsland);

            // === 焦点通知/展开态数据（支持自定义顺序） ===
            List<String> expandedOrder = SettingsPreferences.getExpandedItemsOrder(context);

            JSONObject baseInfo = new JSONObject();
            baseInfo.put("type", 2);

            // 按用户设定的顺序映射到 title / subTitle / content / extraTitle
            String[] textFields = {"title", "subTitle", "content", "extraTitle"};
            for (int i = 0; i < Math.min(expandedOrder.size(), textFields.length); i++) {
                String key = expandedOrder.get(i);
                String[] formatted = formatDataValue(key, currentMa, powerW,
                        temperature, batteryLevel, estimatedMinutes, isCharging);
                // formatted: [label, value, unit]
                String displayValue = formatted[1] + formatted[2];
                baseInfo.put(textFields[i], displayValue);
                if (i == 0) {
                    baseInfo.put("colorTitle", "#4CAF50");
                }
            }

            baseInfo.put("showDivider", true);
            baseInfo.put("showContentDivider", true);
            paramV2.put("baseInfo", baseInfo);

            // === 展开态底部进度条 ===
            JSONObject progressInfo = new JSONObject();
            progressInfo.put("progress", batteryLevel);
            paramV2.put("progressInfo", progressInfo);

            // === 底部提示栏 ===
            JSONObject hintInfo = new JSONObject();
            hintInfo.put("type", 2);
            hintInfo.put("title", isCharging ? "充电中" : "电池监控");
            hintInfo.put("content", String.format("%d%%", batteryLevel));
            paramV2.put("hintInfo", hintInfo);

            root.put("param_v2", paramV2);
            return root.toString();

        } catch (JSONException e) {
            Log.e(TAG, "buildIslandJson failed", e);
            return "{}";
        }
    }

    /**
     * 根据数据键格式化显示值
     *
     * @return [标签, 数值, 单位]
     */
    private static String[] formatDataValue(String dataKey,
                                            float currentMa, float powerW,
                                            float temperature, int batteryLevel,
                                            int estimatedMinutes, boolean isCharging) {
        switch (dataKey) {
            case SettingsPreferences.DATA_POWER:
                return new String[]{isCharging ? "充电" : "放电",
                        String.format("%.1f", powerW), "W"};
            case SettingsPreferences.DATA_TEMPERATURE:
                return new String[]{"温度",
                        String.format("%.0f", temperature), "°C"};
            case SettingsPreferences.DATA_CURRENT:
                return new String[]{"电流",
                        String.format("%.0f", currentMa), "mA"};
            case SettingsPreferences.DATA_BATTERY_LEVEL:
                return new String[]{"电量",
                        String.valueOf(batteryLevel), "%"};
            case SettingsPreferences.DATA_VOLTAGE:
                return new String[]{"电压",
                        String.format("%.2f", powerW > 0 && currentMa > 0
                                ? powerW / (currentMa / 1000f) : 0), "V"};
            case SettingsPreferences.DATA_TIME:
                if (estimatedMinutes > 0) {
                    int hours = estimatedMinutes / 60;
                    int mins = estimatedMinutes % 60;
                    return new String[]{"预计充满",
                            String.format("%02d:%02d", hours, mins), ""};
                } else {
                    return new String[]{"预计充满", "--:--", ""};
                }
            default:
                return new String[]{"--", "--", ""};
        }
    }

    /**
     * 构建普通通知（非超级岛设备降级方案）
     */
    public static Notification buildFallbackNotification(
            Context context,
            float currentMa, float powerW, float temperature,
            int batteryLevel, int estimatedMinutes, boolean isCharging) {

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("充电监控通知");
        notificationManager.createNotificationChannel(channel);

        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String timeStr;
        if (estimatedMinutes > 0) {
            int hours = estimatedMinutes / 60;
            int mins = estimatedMinutes % 60;
            timeStr = String.format("%02d:%02d", hours, mins);
        } else {
            timeStr = "--:--";
        }

        return new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(isCharging ? "充电中" : "电池监控")
                .setContentText(String.format(
                        "电量%d%% | %.1fW | %.0fmA | %.0f°C | 预计%s",
                        batteryLevel, powerW, currentMa, temperature, timeStr))
                .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_charging))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }
}
