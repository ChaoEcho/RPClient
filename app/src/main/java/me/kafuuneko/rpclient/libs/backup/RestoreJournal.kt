package me.kafuuneko.rpclient.libs.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 应用私有 noBackupFilesDir 下的恢复日志，不会进入云备份、导出或运行日志。
 *
 * rollback.zip 包含本机敏感数据，仅在恢复事务存续期间保留。pending 存在时必须先回滚；
 * committed 表示数据库和偏好均已持久化，此后中断只需继续清理，不能撤销成功恢复。
 */
internal class RestoreJournal(private val directory: File) {
    val snapshot = File(directory, "rollback.zip")
    private val pending = File(directory, "pending")
    private val committed = File(directory, "committed")
    val isPending: Boolean get() = pending.isFile && !committed.isFile
    val isCommitted: Boolean get() = committed.isFile

    suspend fun prepare(previousProvidersInitialized: Boolean, writeSnapshot: suspend (File) -> Unit) {
        check(!isPending) { "An interrupted restore must be recovered first" }
        clear()
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create restore journal")
        val temporary = File(directory, "rollback.tmp")
        try {
            writeSnapshot(temporary)
            FileOutputStream(temporary, true).use { it.fd.sync() }
            if (!temporary.renameTo(snapshot)) throw IOException("Unable to publish rollback snapshot")
            writeMarker(pending, previousProvidersInitialized.toString())
        } finally {
            temporary.delete()
        }
    }

    fun previousProvidersInitialized(): Boolean = when (pending.readText()) {
        "true" -> true
        "false" -> false
        else -> throw IOException("Invalid restore journal")
    }

    fun markCommitted() = writeMarker(committed, "complete")

    /** 先删除 pending，最后删除 committed；中断清理不会把已完成事务误判为待回滚。 */
    fun clear() {
        for (file in listOf(pending, snapshot, committed, File(directory, "rollback.tmp"), File(directory, "marker.tmp"))) {
            if (file.exists() && !file.delete()) throw IOException("Unable to clean restore journal")
        }
    }

    private fun writeMarker(target: File, value: String) {
        val temporary = File(directory, "marker.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(value.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(target)) throw IOException("Unable to commit restore journal")
    }
}
