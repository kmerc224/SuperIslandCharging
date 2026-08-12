package com.superisland.charging.settings;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;
import com.superisland.charging.R;
import com.superisland.charging.log.LogCapture;

import java.util.ArrayList;
import java.util.List;

/**
 * 超级岛自定义页面
 * <p>
 * 允许用户配置：
 * - 收起态：左右两侧显示的数据类型
 * - 展开态：数据显示顺序（上下箭头调整）
 */
public class IslandCustomizationActivity extends AppCompatActivity {

    private static final String TAG = "IslandCustomization";

    private Spinner spinnerLeft;
    private Spinner spinnerRight;
    private LinearLayout containerExpandedItems;

    private List<String[]> dataOptions;
    private List<String> expandedOrder;

    private final LogCapture logCapture = LogCapture.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_island_customization);

        setupToolbar();
        initViews();
        setupCollapsedSpinners();
        setupExpandedItems();
    }

    // ==================== 初始化 ====================

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        spinnerLeft = findViewById(R.id.spinner_left);
        spinnerRight = findViewById(R.id.spinner_right);
        containerExpandedItems = findViewById(R.id.container_expanded_items);
    }

    // ==================== 收起态 Spinner ====================

    private void setupCollapsedSpinners() {
        dataOptions = SettingsPreferences.getAllDataOptions();

        // 构建显示标签列表
        List<String> labels = new ArrayList<>();
        for (String[] option : dataOptions) {
            labels.add(option[1]);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerLeft.setAdapter(adapter);
        spinnerRight.setAdapter(adapter);

        // 初始化左侧选中项
        String leftKey = SettingsPreferences.getCollapsedLeftData(this);
        int leftIndex = indexOfKey(leftKey);
        if (leftIndex >= 0) {
            spinnerLeft.setSelection(leftIndex);
        }

        // 初始化右侧选中项
        String rightKey = SettingsPreferences.getCollapsedRightData(this);
        int rightIndex = indexOfKey(rightKey);
        if (rightIndex >= 0) {
            spinnerRight.setSelection(rightIndex);
        }

        // 左侧选择监听
        spinnerLeft.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedKey = dataOptions.get(position)[0];
                SettingsPreferences.setCollapsedLeftData(IslandCustomizationActivity.this, selectedKey);
                logCapture.info(TAG, "收起态左侧数据更改为: " + selectedKey);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 忽略
            }
        });

        // 右侧选择监听
        spinnerRight.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedKey = dataOptions.get(position)[0];
                SettingsPreferences.setCollapsedRightData(IslandCustomizationActivity.this, selectedKey);
                logCapture.info(TAG, "收起态右侧数据更改为: " + selectedKey);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 忽略
            }
        });
    }

    // ==================== 展开态排序 ====================

    private void setupExpandedItems() {
        expandedOrder = SettingsPreferences.getExpandedItemsOrder(this);
        refreshExpandedUI();
    }

    private void refreshExpandedUI() {
        containerExpandedItems.removeAllViews();

        for (int i = 0; i < expandedOrder.size(); i++) {
            String key = expandedOrder.get(i);
            String label = SettingsPreferences.getDataLabel(key);
            boolean isFirst = (i == 0);
            boolean isLast = (i == expandedOrder.size() - 1);

            View row = createItemRow(label, key, i, isFirst, isLast);
            containerExpandedItems.addView(row);
        }
    }

    @NonNull
    private View createItemRow(String label, String key, int position, boolean isFirst, boolean isLast) {
        // 外层卡片
        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0f);
        card.setRadius(dpToPx(16));
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(getColorFromAttr(com.google.android.material.R.attr.colorOutline));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dpToPx(8);
        card.setLayoutParams(cardParams);

        // 水平布局
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = dpToPx(16);
        row.setPadding(padding, dpToPx(12), padding, dpToPx(12));

        // 标签文本
        MaterialTextView tvLabel = new MaterialTextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(14);
        tvLabel.setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurface));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        tvLabel.setLayoutParams(labelParams);

        // 上移按钮
        ImageButton btnUp = new ImageButton(this);
        btnUp.setImageResource(R.drawable.ic_arrow_up);
        btnUp.setContentDescription("上移");
        btnUp.setBackgroundColor(0x00000000);
        btnUp.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        LinearLayout.LayoutParams btnUpParams = new LinearLayout.LayoutParams(
                dpToPx(40), dpToPx(40)
        );
        btnUpParams.setMarginStart(dpToPx(8));
        btnUp.setLayoutParams(btnUpParams);

        if (isFirst) {
            btnUp.setVisibility(View.INVISIBLE);
        } else {
            btnUp.setOnClickListener(v -> {
                moveItem(position, position - 1);
            });
        }

        // 下移按钮
        ImageButton btnDown = new ImageButton(this);
        btnDown.setImageResource(R.drawable.ic_arrow_down);
        btnDown.setContentDescription("下移");
        btnDown.setBackgroundColor(0x00000000);
        btnDown.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        LinearLayout.LayoutParams btnDownParams = new LinearLayout.LayoutParams(
                dpToPx(40), dpToPx(40)
        );
        btnDownParams.setMarginStart(dpToPx(4));
        btnDown.setLayoutParams(btnDownParams);

        if (isLast) {
            btnDown.setVisibility(View.INVISIBLE);
        } else {
            btnDown.setOnClickListener(v -> {
                moveItem(position, position + 1);
            });
        }

        row.addView(tvLabel);
        row.addView(btnUp);
        row.addView(btnDown);
        card.addView(row);

        return card;
    }

    private void moveItem(int fromPosition, int toPosition) {
        if (toPosition < 0 || toPosition >= expandedOrder.size()) {
            return;
        }

        String item = expandedOrder.remove(fromPosition);
        expandedOrder.add(toPosition, item);

        // 保存新顺序
        SettingsPreferences.setExpandedItemsOrder(this, expandedOrder);
        logCapture.info(TAG, "展开态数据顺序更新: " + expandedOrder);

        // 刷新 UI
        refreshExpandedUI();
    }

    // ==================== 工具方法 ====================

    private int indexOfKey(String key) {
        for (int i = 0; i < dataOptions.size(); i++) {
            if (dataOptions.get(i)[0].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private int getColorFromAttr(int attrId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }
}
