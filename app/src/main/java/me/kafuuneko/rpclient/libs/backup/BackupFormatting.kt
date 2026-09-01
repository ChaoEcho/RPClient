package me.kafuuneko.rpclient.libs.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 完整备份大小与时间戳格式化工具。 */
object BackupFormatting {
    private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

    /**
     * 格式化文件字节大小。
     *
     * 字节小于 1024 时直接显示整数 B；KB 及以上保留一位小数。
     */
    fun formatBackupSize(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < UNITS.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, UNITS[index])
    }

    /** 格式化备份创建或修改时间戳。 */
    fun formatBackupTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
