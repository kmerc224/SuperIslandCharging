package com.superisland.charging;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.superisland.charging.log.LogCapture;

import java.util.List;

/**
 * 运行日志查看器
 * <p>
 * 实时显示 LogCapture 捕获的运行日志，支持：
 * - 实时接收新日志并自动滚动到底部
 * - 清空所有日志
 * - 分享日志文本
 */
public class LogViewerActivity extends AppCompatActivity implements LogCapture.LogListener {

    private static final String TAG = "LogViewer";

    private ScrollView scrollLog;
    private MaterialTextView tvLogContent;
    private MaterialTextView tvEmpty;
    private MaterialButton btnClear;
    private MaterialButton btnShare;

    private final LogCapture logCapture = LogCapture.getInstance();
    private final StringBuilder logBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        setupToolbar();
        initViews();
        setupButtons();
    }

    // ==================== 初始化 ====================

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        scrollLog = findViewById(R.id.scroll_log);
        tvLogContent = findViewById(R.id.tv_log_content);
        tvEmpty = findViewById(R.id.tv_empty);
        btnClear = findViewById(R.id.btn_clear);
        btnShare = findViewById(R.id.btn_share);
    }

    private void setupButtons() {
        // 清空按钮
        btnClear.setOnClickListener(v -> {
            logCapture.clear();
            logBuffer.setLength(0);
            tvLogContent.setText("");
            updateEmptyState();
            logCapture.info(TAG, "日志已清空");
        });

        // 分享按钮
        btnShare.setOnClickListener(v -> {
            String logText = logCapture.exportAsText();
            if (logText == null || logText.isEmpty()) {
                return;
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "超级岛充电监控 - 运行日志");
            shareIntent.putExtra(Intent.EXTRA_TEXT, logText);
            startActivity(Intent.createChooser(shareIntent, "分享日志"));
        });
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onResume() {
        super.onResume();

        // 加载已有日志
        loadExistingLogs();

        // 注册日志监听器
        logCapture.addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // 注销日志监听器
        logCapture.removeListener(this);
    }

    // ==================== LogListener ====================

    @Override
    public void onNewLog(LogCapture.LogEntry entry) {
        String formatted = entry.getFormatted();
        logBuffer.append(formatted).append("\n");
        tvLogContent.append(formatted + "\n");

        // 更新空状态
        updateEmptyState();

        // 自动滚动到底部
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    // ==================== 工具方法 ====================

    private void loadExistingLogs() {
        logBuffer.setLength(0);
        List<LogCapture.LogEntry> entries = logCapture.getEntries();

        if (entries.isEmpty()) {
            tvLogContent.setText("");
        } else {
            StringBuilder sb = new StringBuilder();
            for (LogCapture.LogEntry entry : entries) {
                sb.append(entry.getFormatted()).append("\n");
            }
            logBuffer.append(sb);
            tvLogContent.setText(sb.toString());

            // 滚动到底部
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = logCapture.getEntries().isEmpty();
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        scrollLog.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
}
