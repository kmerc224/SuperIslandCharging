package com.superisland.charging;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shizuku 用户服务实现
 * <p>
 * 通过 Shizuku 以 Shell (uid=2000, ADB权限) 运行命令，
 * 可以访问普通应用无法读取的系统文件（如电池详细信息）。
 * <p>
 * 此服务在 Shizuku 的进程空间中运行，拥有 Shell 权限。
 * 在 AndroidManifest.xml 中通过 moe.shizuku.bind action 注册。
 */
public class ShizukuShellService extends Service {

    private static final String TAG = "ShizukuShellService";

    private final IShellService.Stub binder = new IShellService.Stub() {
        @Override
        public String executeCommand(String command) {
            // 安全检查：验证调用者UID
            int callingUid = Binder.getCallingUid();
            // Shizuku 运行在 shell uid (2000) 或 root uid (0) 下
            if (callingUid != Process.myUid()
                    && callingUid != 2000  // shell
                    && callingUid != 0) {  // root
                Log.w(TAG, "Rejected call from uid: " + callingUid);
                return null;
            }

            try {
                Process process = Runtime.getRuntime().exec(
                        new String[]{"sh", "-c", command});

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append("\n");
                    output.append(line);
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return output.toString().trim();
                } else {
                    Log.w(TAG, "Command failed (exit " + exitCode + "): " + command);
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "executeCommand error: " + e.getMessage());
                return null;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
