package com.superisland.charging.data;

/**
 * 充电记录数据模型
 */
public class ChargeRecord {
    public long id;
    public long timestamp;       // 时间戳 (ms)
    public String sessionId;     // 充电会话ID
    public int batteryLevel;     // 电量百分比 (0-100)
    public float currentMa;      // 电流 (mA)
    public float powerW;         // 功率 (W)
    public float voltageV;       // 电压 (V)
    public float temperature;    // 温度 (°C)
    public boolean isCharging;   // 是否充电中
}
