package com.superisland.charging;

/**
 * Shizuku 用户服务 AIDL 接口
 * 通过 Shizuku 以 Shell (ADB) 权限执行命令
 */
interface IShellService {
    /**
     * 执行 Shell 命令并返回输出
     * @param command 要执行的命令
     * @return 命令的标准输出
     */
    String executeCommand(String command);
}
