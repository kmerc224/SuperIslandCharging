package com.superisland.charging.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.superisland.charging.R;
import com.superisland.charging.data.BatteryDatabase;
import com.superisland.charging.data.ChargeRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 充电记录图表 Fragment
 * <p>
 * 使用 MPAndroidChart 绘制三个折线图：
 * - 电量变化 (%)
 * - 充电功率 (W)
 * - 电池温度 (°C)
 */
public class ChargingRecordsFragment extends Fragment {

    private LineChart chartBatteryLevel;
    private LineChart chartPower;
    private LineChart chartTemperature;
    private MaterialButton btnTimeRange;
    private LinearLayout layoutEmpty;

    private BatteryDatabase database;
    private int selectedHours = 24; // 默认显示今日(24h)
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_charging_records, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        database = BatteryDatabase.getInstance(requireContext());

        initViews(view);
        setupCharts();
        setupTimeRangeButton();
        loadAndDisplayData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAndDisplayData();
    }

    private void initViews(View view) {
        chartBatteryLevel = view.findViewById(R.id.chart_battery_level);
        chartPower = view.findViewById(R.id.chart_power);
        chartTemperature = view.findViewById(R.id.chart_temperature);
        btnTimeRange = view.findViewById(R.id.btn_time_range);
        layoutEmpty = view.findViewById(R.id.layout_empty);
    }

    // ==================== 图表配置 ====================

    private void setupCharts() {
        setupChart(chartBatteryLevel, "%", Color.parseColor("#4CAF50"));
        setupChart(chartPower, "W", Color.parseColor("#FF9800"));
        setupChart(chartTemperature, "°C", Color.parseColor("#F44336"));
    }

    private void setupChart(LineChart chart, String unit, int color) {
        chart.setDrawGridBackground(false);
        chart.setDescription(null);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setNoDataText("暂无数据");
        chart.getLegend().setEnabled(false);

        // X轴 - 时间
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.GRAY);
        xAxis.setTextSize(10f);
        xAxis.setValueFormatter(new TimeValueFormatter());

        // Y轴
        chart.getAxisLeft().setTextColor(Color.GRAY);
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.parseColor("#E0E0E0"));
        chart.getAxisRight().setEnabled(false);
    }

    // ==================== 时间范围选择 ====================

    private void setupTimeRangeButton() {
        btnTimeRange.setOnClickListener(v -> {
            // 循环切换时间范围
            if (selectedHours == 24) {
                selectedHours = 6;
                btnTimeRange.setText("6小时");
            } else if (selectedHours == 6) {
                selectedHours = 1;
                btnTimeRange.setText("1小时");
            } else {
                selectedHours = 24;
                btnTimeRange.setText("今日");
            }
            loadAndDisplayData();
        });
    }

    // ==================== 数据加载与显示 ====================

    private void loadAndDisplayData() {
        List<ChargeRecord> records = database.queryRecentRecords(selectedHours);

        if (records.isEmpty()) {
            chartBatteryLevel.setVisibility(View.GONE);
            chartPower.setVisibility(View.GONE);
            chartTemperature.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }

        chartBatteryLevel.setVisibility(View.VISIBLE);
        chartPower.setVisibility(View.VISIBLE);
        chartTemperature.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        // 转换为图表数据
        List<Entry> levelEntries = new ArrayList<>();
        List<Entry> powerEntries = new ArrayList<>();
        List<Entry> tempEntries = new ArrayList<>();

        long baseTime = records.get(0).timestamp;

        for (ChargeRecord record : records) {
            float x = (record.timestamp - baseTime) / (1000f * 60); // 分钟为X轴单位
            levelEntries.add(new Entry(x, record.batteryLevel));
            powerEntries.add(new Entry(x, record.powerW));
            tempEntries.add(new Entry(x, record.temperature));
        }

        // 电量图
        updateChart(chartBatteryLevel, levelEntries,
                Color.parseColor("#4CAF50"), "电量");

        // 功率图
        updateChart(chartPower, powerEntries,
                Color.parseColor("#FF9800"), "功率");

        // 温度图
        updateChart(chartTemperature, tempEntries,
                Color.parseColor("#F44336"), "温度");
    }

    private void updateChart(LineChart chart, List<Entry> entries, int color, String label) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(2f);
        dataSet.setCircleColor(color);
        dataSet.setDrawFilled(true);
        dataSet.setFillAlpha(30);
        dataSet.setFillColor(color);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.15f);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(color);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.animateX(500);
        chart.invalidate();
    }

    // ==================== 时间格式化 ====================

    /**
     * X轴时间格式化器
     * 将分钟偏移量转换为 HH:mm 格式
     */
    private class TimeValueFormatter extends ValueFormatter {
        private long baseTime;

        TimeValueFormatter() {
            List<ChargeRecord> records = database.queryRecentRecords(selectedHours);
            if (!records.isEmpty()) {
                baseTime = records.get(0).timestamp;
            } else {
                baseTime = System.currentTimeMillis();
            }
        }

        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            long timestamp = baseTime + (long) (value * 60 * 1000);
            return timeFormat.format(new Date(timestamp));
        }
    }
}
