package com.superisland.charging.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 充电记录数据库
 * <p>
 * 存储充电过程中的实时数据快照，用于：
 * - 绘制充电曲线（电量、功率、温度）
 * - 计算实际电池容量
 * - 评估电池健康度
 */
public class BatteryDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "battery_monitor.db";
    private static final int DB_VERSION = 1;

    // 充电记录表
    private static final String TABLE_CHARGE_LOG = "charge_log";
    private static final String COL_ID = "_id";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_SESSION_ID = "session_id";
    private static final String COL_BATTERY_LEVEL = "battery_level";
    private static final String COL_CURRENT_MA = "current_ma";
    private static final String COL_POWER_W = "power_w";
    private static final String COL_VOLTAGE_V = "voltage_v";
    private static final String COL_TEMPERATURE = "temperature";
    private static final String COL_IS_CHARGING = "is_charging";

    // 电池配置表
    private static final String TABLE_BATTERY_CONFIG = "battery_config";
    private static final String COL_FACTORY_CAPACITY = "factory_capacity";  // 出厂容量 mAh
    private static final String COL_DESIGN_VOLTAGE = "design_voltage";      // 设计电压 mV

    private static BatteryDatabase instance;

    public static synchronized BatteryDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new BatteryDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private BatteryDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 充电记录表
        db.execSQL("CREATE TABLE " + TABLE_CHARGE_LOG + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_SESSION_ID + " TEXT NOT NULL, "
                + COL_BATTERY_LEVEL + " INTEGER, "
                + COL_CURRENT_MA + " REAL, "
                + COL_POWER_W + " REAL, "
                + COL_VOLTAGE_V + " REAL, "
                + COL_TEMPERATURE + " REAL, "
                + COL_IS_CHARGING + " INTEGER DEFAULT 0)");

        // 索引：按时间查询
        db.execSQL("CREATE INDEX idx_charge_time ON " + TABLE_CHARGE_LOG
                + " (" + COL_TIMESTAMP + ")");
        // 索引：按会话查询
        db.execSQL("CREATE INDEX idx_charge_session ON " + TABLE_CHARGE_LOG
                + " (" + COL_SESSION_ID + ")");

        // 电池配置表
        db.execSQL("CREATE TABLE " + TABLE_BATTERY_CONFIG + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_FACTORY_CAPACITY + " REAL DEFAULT 0, "
                + COL_DESIGN_VOLTAGE + " REAL DEFAULT 0)");

        // 插入默认配置
        ContentValues cv = new ContentValues();
        cv.put(COL_FACTORY_CAPACITY, 0);
        cv.put(COL_DESIGN_VOLTAGE, 0);
        db.insert(TABLE_BATTERY_CONFIG, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 未来版本升级逻辑
    }

    // ==================== 充电记录 CRUD ====================

    /**
     * 插入一条充电记录
     */
    public long insertChargeRecord(String sessionId, int batteryLevel,
                                   float currentMa, float powerW,
                                   float voltageV, float temperature,
                                   boolean isCharging) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_SESSION_ID, sessionId);
        values.put(COL_BATTERY_LEVEL, batteryLevel);
        values.put(COL_CURRENT_MA, currentMa);
        values.put(COL_POWER_W, powerW);
        values.put(COL_VOLTAGE_V, voltageV);
        values.put(COL_TEMPERATURE, temperature);
        values.put(COL_IS_CHARGING, isCharging ? 1 : 0);
        return db.insert(TABLE_CHARGE_LOG, null, values);
    }

    /**
     * 查询指定时间范围内的充电记录
     *
     * @param startTime 开始时间戳 (ms)
     * @param endTime   结束时间戳 (ms)
     * @return 记录列表
     */
    public List<ChargeRecord> queryRecords(long startTime, long endTime) {
        List<ChargeRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(TABLE_CHARGE_LOG,
                null,
                COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(startTime), String.valueOf(endTime)},
                null, null,
                COL_TIMESTAMP + " ASC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                ChargeRecord record = new ChargeRecord();
                record.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                record.sessionId = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_ID));
                record.batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BATTERY_LEVEL));
                record.currentMa = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_CURRENT_MA));
                record.powerW = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_POWER_W));
                record.voltageV = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_VOLTAGE_V));
                record.temperature = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_TEMPERATURE));
                record.isCharging = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_CHARGING)) == 1;
                records.add(record);
            }
            cursor.close();
        }
        return records;
    }

    /**
     * 查询最近N小时的记录
     */
    public List<ChargeRecord> queryRecentRecords(int hours) {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (long) hours * 60 * 60 * 1000;
        return queryRecords(startTime, endTime);
    }

    /**
     * 查询今天的记录
     */
    public List<ChargeRecord> queryTodayRecords() {
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return queryRecords(cal.getTimeInMillis(), now);
    }

    /**
     * 获取所有充电会话ID列表
     */
    public List<String> getSessionIds() {
        List<String> sessions = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT " + COL_SESSION_ID + " FROM " + TABLE_CHARGE_LOG
                        + " ORDER BY " + COL_TIMESTAMP + " DESC", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                sessions.add(cursor.getString(0));
            }
            cursor.close();
        }
        return sessions;
    }

    /**
     * 查询指定会话的记录
     */
    public List<ChargeRecord> querySessionRecords(String sessionId) {
        List<ChargeRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CHARGE_LOG,
                null,
                COL_SESSION_ID + " = ?",
                new String[]{sessionId},
                null, null,
                COL_TIMESTAMP + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ChargeRecord record = new ChargeRecord();
                record.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                record.sessionId = cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_ID));
                record.batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BATTERY_LEVEL));
                record.currentMa = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_CURRENT_MA));
                record.powerW = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_POWER_W));
                record.voltageV = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_VOLTAGE_V));
                record.temperature = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_TEMPERATURE));
                record.isCharging = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_CHARGING)) == 1;
                records.add(record);
            }
            cursor.close();
        }
        return records;
    }

    /**
     * 清理超过指定天数的旧记录
     */
    public int cleanOldRecords(int keepDays) {
        SQLiteDatabase db = getWritableDatabase();
        long cutoff = System.currentTimeMillis() - (long) keepDays * 24 * 60 * 60 * 1000;
        return db.delete(TABLE_CHARGE_LOG,
                COL_TIMESTAMP + " < ?",
                new String[]{String.valueOf(cutoff)});
    }

    // ==================== 电池配置 ====================

    /**
     * 获取出厂容量 (mAh)
     */
    public float getFactoryCapacity() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_BATTERY_CONFIG,
                new String[]{COL_FACTORY_CAPACITY},
                null, null, null, null, null, "1");
        float capacity = 0;
        if (cursor != null && cursor.moveToFirst()) {
            capacity = cursor.getFloat(0);
            cursor.close();
        }
        return capacity;
    }

    /**
     * 设置出厂容量 (mAh)
     */
    public void setFactoryCapacity(float capacityMah) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_FACTORY_CAPACITY, capacityMah);
        db.update(TABLE_BATTERY_CONFIG, values, null, null);
    }

    /**
     * 根据充电数据估算实际电池容量 (mAh)
     * <p>
     * 原理：统计一次完整充电过程中充入的总电荷量
     * 实际容量 ≈ Σ(电流 × 时间间隔) / 1000
     */
    public float estimateActualCapacity() {
        // 查找最近一次完整充电会话（从低电量到高电量）
        List<String> sessions = getSessionIds();
        for (String sessionId : sessions) {
            List<ChargeRecord> records = querySessionRecords(sessionId);
            if (records.size() < 10) continue; // 至少10条记录

            ChargeRecord first = records.get(0);
            ChargeRecord last = records.get(records.size() - 1);

            // 只统计充电会话，且电量跨度大于50%
            if (!first.isCharging || !last.isCharging) continue;
            if (last.batteryLevel - first.batteryLevel < 50) continue;

            // 计算充入总电荷量 (mAh)
            float totalMah = 0;
            for (int i = 1; i < records.size(); i++) {
                ChargeRecord prev = records.get(i - 1);
                ChargeRecord curr = records.get(i);
                float intervalHours = (curr.timestamp - prev.timestamp) / (1000f * 60 * 60);
                totalMah += curr.currentMa * intervalHours;
            }

            // 根据电量跨度推算满充容量
            float levelDelta = last.batteryLevel - first.batteryLevel;
            if (levelDelta > 0) {
                return totalMah * 100f / levelDelta;
            }
        }
        return 0;
    }

    /**
     * 计算电池健康度 (%)
     *
     * @return 健康度百分比，0表示无法计算
     */
    public float calculateBatteryHealth() {
        float factoryCapacity = getFactoryCapacity();
        if (factoryCapacity <= 0) return 0;

        float actualCapacity = estimateActualCapacity();
        if (actualCapacity <= 0) return 0;

        return (actualCapacity / factoryCapacity) * 100f;
    }
}
