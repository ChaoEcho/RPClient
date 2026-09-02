package me.kafuuneko.rpclient.libs.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kafuuneko.rpclient.libs.AppModel
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 集中管理应用运行日志的内存环形缓冲区与滚动文件持久化。
 *
 * 两条重要约束：
 * - 开发者日志关闭时只保留 ERROR，其余级别连内存都不写。此前开关只控制写文件，
 *   release 版本对所有用户都在做脱敏、拼栈和 StateFlow 广播。
 * - 缓冲区不再对每条日志做全量拷贝。此前每写一条就 `toList()` 复制最多 2000 个元素
 *   并推给 StateFlow，每个 HTTP 请求都会触发一次 O(n)。现在只在查看器打开时快照，
 *   写入路径仅递增一个版本号。
 */
object AppLogStore {
    private const val MAX_MEMORY_LOGS = 2000
    private const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L // 2MB
    private const val MAX_BACKUP_FILES = 2

    private val idGenerator = AtomicLong(1L)
    private val memoryBuffer = ArrayDeque<AppLogEntry>(MAX_MEMORY_LOGS)

    /**
     * 缓冲区变更信号。
     *
     * 只携带一个单调递增的版本号，订阅方据此按需调用 [snapshot]，
     * 因此高频写入不会在写入线程上产生列表拷贝。
     */
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    private var appContext: Context? = null
    private val lock = Any()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // 预编译一次；此前每条日志都在 sanitize 内重新编译正则，且漏掉了裸 token 的场景。
    private val bearerPattern = Regex("""(?i)bearer\s+[a-zA-Z0-9_\-.]+""")
    private val secretAssignmentPattern =
        Regex("""(?i)(api[_-]?key|password|secret|token|authorization)\s*[:=]\s*["']?([^"',\s]+)["']?""")

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 开发者日志是否开启；读取偏好失败时按关闭处理。 */
    private val isEnabled: Boolean
        get() = runCatching { AppModel.developerLoggingEnabled }.getOrDefault(false)

    fun addLog(
        level: AppLogLevel,
        module: String,
        rawMessage: String,
        throwable: Throwable? = null
    ) {
        // 关闭时仍保留 ERROR，崩溃后用户打开开发者模式还能看到导致问题的那几条。
        val enabled = isEnabled
        if (!enabled && level != AppLogLevel.ERROR) return

        val sanitizedMessage = sanitize(rawMessage)
        val throwableSummary = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sanitize(sw.toString())
        }

        val entry = AppLogEntry(
            id = idGenerator.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            module = module,
            message = sanitizedMessage,
            throwableSummary = throwableSummary
        )

        synchronized(lock) {
            if (memoryBuffer.size >= MAX_MEMORY_LOGS) {
                memoryBuffer.removeFirst()
            }
            memoryBuffer.addLast(entry)
        }
        mutableRevision.value = mutableRevision.value + 1

        if (enabled) {
            writeToFile(entry)
        }
    }

    /** 读取当前缓冲区快照；仅供查看器在需要渲染时调用。 */
    fun snapshot(): List<AppLogEntry> = synchronized(lock) { memoryBuffer.toList() }

    private fun sanitize(input: String): String {
        val withoutBearer = input.replace(bearerPattern, "Bearer ***")
        return withoutBearer.replace(secretAssignmentPattern) { matchResult ->
            "${matchResult.groupValues[1]}: ***"
        }
    }

    private fun writeToFile(entry: AppLogEntry) {
        val context = appContext ?: return
        try {
            val dir = File(context.filesDir, "debug")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val logFile = File(dir, "app.log")
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                rotateFiles(dir)
            }

            FileWriter(logFile, true).use { writer ->
                writer.write(entry.formatLine())
            }
        } catch (e: Exception) {
            runCatching { Log.w("AppLogStore", "Failed to write log to file", e) }
        }
    }

    private fun rotateFiles(dir: File) {
        val oldest = File(dir, "app.$MAX_BACKUP_FILES.log")
        if (oldest.exists()) {
            oldest.delete()
        }
        for (i in MAX_BACKUP_FILES - 1 downTo 1) {
            val src = File(dir, "app.$i.log")
            if (src.exists()) {
                val dest = File(dir, "app.${i + 1}.log")
                src.renameTo(dest)
            }
        }
        val mainFile = File(dir, "app.log")
        if (mainFile.exists()) {
            mainFile.renameTo(File(dir, "app.1.log"))
        }
    }

    fun clear() {
        synchronized(lock) {
            memoryBuffer.clear()
        }
        mutableRevision.value = mutableRevision.value + 1

        val context = appContext ?: return
        try {
            val dir = File(context.filesDir, "debug")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            runCatching { Log.w("AppLogStore", "Failed to clear log files", e) }
        }
    }

    fun exportFormattedLogs(): String {
        val list = snapshot()
        if (list.isEmpty()) {
            // 内存为空但磁盘上可能还有上一次会话的滚动日志。
            val context = appContext ?: return ""
            val logFile = File(File(context.filesDir, "debug"), "app.log")
            if (!logFile.exists()) return ""
            return runCatching { logFile.readText() }.getOrDefault("")
        }
        return buildString { list.forEach { append(it.formatLine()) } }
    }

    private fun AppLogEntry.formatLine(): String {
        val timeText = synchronized(dateFormat) { dateFormat.format(Date(timestamp)) }
        return buildString {
            append(timeText)
            append(" [")
            append(level.name)
            append("] [")
            append(module)
            append("] ")
            append(message)
            if (!throwableSummary.isNullOrBlank()) {
                append("\n")
                append(throwableSummary)
            }
            append("\n")
        }
    }
}
