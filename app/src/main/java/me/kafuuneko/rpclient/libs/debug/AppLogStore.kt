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
 */
object AppLogStore {
    private const val MAX_MEMORY_LOGS = 2000
    private const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L // 2MB
    private const val MAX_BACKUP_FILES = 2

    private val idGenerator = AtomicLong(1L)
    private val memoryBuffer = ArrayDeque<AppLogEntry>(MAX_MEMORY_LOGS)
    private val _logsFlow = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<AppLogEntry>> = _logsFlow.asStateFlow()

    private var appContext: Context? = null
    private val lock = Any()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val sensitivePatterns = listOf(
        Regex("""(?i)bearer\s+[a-zA-Z0-9_\-\.]+"""),
        Regex("""(?i)(api[_-]?key|password|secret|token|authorization)\s*[:=]\s*["']?([^"',\s]+)["']?""")
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun addLog(
        level: AppLogLevel,
        module: String,
        rawMessage: String,
        throwable: Throwable? = null
    ) {
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

        var currentList: List<AppLogEntry>
        synchronized(lock) {
            if (memoryBuffer.size >= MAX_MEMORY_LOGS) {
                memoryBuffer.removeFirst()
            }
            memoryBuffer.addLast(entry)
            currentList = memoryBuffer.toList()
        }
        _logsFlow.value = currentList

        // 如果开启了开发者运行日志，异步或安全追加到本地滚动日志文件
        if (runCatching { AppModel.developerLoggingEnabled }.getOrDefault(false)) {
            writeToFile(entry)
        }
    }

    private fun sanitize(input: String): String {
        var text = input
        text = text.replace(Regex("""(?i)bearer\s+[a-zA-Z0-9_\-\.]+"""), "Bearer ***")
        text = text.replace(
            Regex("""(?i)(api[_-]?key|password|secret|authorization)\s*[:=]\s*["']?([^"',\s]+)["']?""")
        ) { matchResult ->
            "${matchResult.groupValues[1]}: ***"
        }
        return text
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

            val timestampStr = synchronized(dateFormat) {
                dateFormat.format(Date(entry.timestamp))
            }
            val line = buildString {
                append(timestampStr)
                append(" [")
                append(entry.level.name)
                append("] [")
                append(entry.module)
                append("] ")
                append(entry.message)
                if (!entry.throwableSummary.isNullOrBlank()) {
                    append("\n")
                    append(entry.throwableSummary)
                }
                append("\n")
            }

            FileWriter(logFile, true).use { writer ->
                writer.write(line)
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
        _logsFlow.value = emptyList()

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
        val list = synchronized(lock) { memoryBuffer.toList() }
        if (list.isEmpty()) {
            // 如果内存为空但存在日志文件，尝试读取文件
            val context = appContext
            if (context != null) {
                val logFile = File(File(context.filesDir, "debug"), "app.log")
                if (logFile.exists()) {
                    return runCatching { logFile.readText() }.getOrDefault("")
                }
            }
            return ""
        }

        return buildString {
            list.forEach { entry ->
                val timeStr = synchronized(dateFormat) {
                    dateFormat.format(Date(entry.timestamp))
                }
                append(timeStr)
                append(" [")
                append(entry.level.name)
                append("] [")
                append(entry.module)
                append("] ")
                append(entry.message)
                if (!entry.throwableSummary.isNullOrBlank()) {
                    append("\n")
                    append(entry.throwableSummary)
                }
                append("\n")
            }
        }
    }
}
