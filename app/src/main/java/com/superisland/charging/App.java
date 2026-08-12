package com.superisland.charging;

import android.app.Application;
import android.util.Log;

import com.superisland.charging.log.LogCapture;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 自定义 Application
 * <p>
 * 功能：
 * 1. 初始化 LogCapture（最早时机）
 * 2. 设置全局崩溃捕获器，将崩溃日志写入文件
 * 3. 下次启动时可在「运行日志」中查看上次崩溃信息
 */
public class App extends Application {

    private static final String TAG = "App";
    private static final String CRASH_LOG_FILE = "crash_log.txt";

    @Override
    public void onCreate() {
        super.onCreate();

        // 最早初始化 LogCapture
        LogCapture capture = LogCapture.getInstance();
        capture.info(TAG, "Application onCreate started");

        // 检查并记录上次崩溃信息
        recordLastCrash();

        // 设置全局崩溃捕获器
        setupCrashHandler();

        capture.info(TAG, "Application onCreate completed");
    }

    /**
     * 检查是否存在上次崩溃的日志文件，如果有则读入 LogCapture
     */
    private void recordLastCrash() {
        try {
            File crashFile = new File(getFilesDir(), CRASH_LOG_FILE);
            if (crashFile.exists() && crashFile.length() > 0) {
                LogCapture.getInstance().error(TAG,
                        "========== 上次崩溃日志 ==========");

                // 读取崩溃文件内容
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(crashFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    LogCapture.getInstance().error(TAG, line);
                }
                reader.close();

                LogCapture.getInstance().error(TAG,
                        "========== 上次崩溃日志结束 ==========");

                // 删除已读取的崩溃文件
                crashFile.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read crash log: " + e.getMessage());
        }
    }

    /**
     * 设置全局崩溃捕获器
     * <p>
     * 当应用发生未捕获异常时：
     * 1. 将崩溃堆栈写入文件
     * 2. 下次启动时在「运行日志」页面可查看
     */
    private void setupCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                // 写入崩溃日志到文件
                File crashFile = new File(getFilesDir(), CRASH_LOG_FILE);
                PrintWriter writer = new PrintWriter(new FileWriter(crashFile, false));

                SimpleDateFormat fmt = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
                writer.println("崩溃时间: " + fmt.format(new Date()));
                writer.println("线程: " + thread.getName());
                writer.println();

                // 写入异常信息
                throwable.printStackTrace(writer);

                writer.flush();
                writer.close();

                Log.e(TAG, "Crash log saved to: " + crashFile.getAbsolutePath());

            } catch (Exception e) {
                Log.e(TAG, "Failed to save crash log: " + e.getMessage());
            }

            // 调用系统默认处理器（会终止进程）
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                // 如果没有默认处理器，直接终止进程
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
    }
}
