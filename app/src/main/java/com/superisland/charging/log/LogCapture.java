package com.superisland.charging.log;

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * 运行时日志捕获
 * <p>
 * 将应用运行期间的关键日志记录到内存中的环形缓冲区，
 * 供用户在「运行日志」页面查看和分享。
 */
public class LogCapture {

    private static final int MAX_ENTRIES = 500;

    private static LogCapture instance;
    private final LinkedList<LogEntry> entries = new LinkedList<>();
    private final List<LogListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public static synchronized LogCapture getInstance() {
        if (instance == null) {
            instance = new LogCapture();
        }
        return instance;
    }

    private LogCapture() {}

    /**
     * 记录一条日志
     */
    public void log(String tag, String message, int level) {
        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                tag,
                message,
                level
        );

        synchronized (entries) {
            entries.addLast(entry);
            if (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }

        // 通知监听器（在主线程）
        mainHandler.post(() -> {
            for (LogListener listener : listeners) {
                listener.onNewLog(entry);
            }
        });
    }

    public void info(String tag, String message) {
        log(tag, message, LogEntry.LEVEL_INFO);
    }

    public void warn(String tag, String message) {
        log(tag, message, LogEntry.LEVEL_WARN);
    }

    public void error(String tag, String message) {
        log(tag, message, LogEntry.LEVEL_ERROR);
    }

    public void debug(String tag, String message) {
        log(tag, message, LogEntry.LEVEL_DEBUG);
    }

    /**
     * 获取所有日志条目的快照
     */
    public List<LogEntry> getEntries() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    /**
     * 清空日志
     */
    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    /**
     * 导出所有日志为文本
     */
    public String exportAsText() {
        StringBuilder sb = new StringBuilder();
        List<LogEntry> snapshot = getEntries();
        for (LogEntry entry : snapshot) {
            sb.append(entry.getFormatted()).append("\n");
        }
        return sb.toString();
    }

    // ==================== 数据类 ====================

    public static class LogEntry {
        public static final int LEVEL_DEBUG = 0;
        public static final int LEVEL_INFO = 1;
        public static final int LEVEL_WARN = 2;
        public static final int LEVEL_ERROR = 3;

        public final long timestamp;
        public final String tag;
        public final String message;
        public final int level;

        public LogEntry(long timestamp, String tag, String message, int level) {
            this.timestamp = timestamp;
            this.tag = tag;
            this.message = message;
            this.level = level;
        }

        public String getLevelString() {
            switch (level) {
                case LEVEL_DEBUG: return "D";
                case LEVEL_INFO:  return "I";
                case LEVEL_WARN:  return "W";
                case LEVEL_ERROR: return "E";
                default: return "?";
            }
        }

        public String getFormatted() {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
            return String.format("%s %s/%s: %s",
                    fmt.format(new Date(timestamp)),
                    getLevelString(), tag, message);
        }
    }

    // ==================== 监听器接口 ====================

    public interface LogListener {
        void onNewLog(LogEntry entry);
    }
}
