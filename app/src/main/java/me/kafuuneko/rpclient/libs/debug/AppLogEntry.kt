package me.kafuuneko.rpclient.libs.debug

enum class AppLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class AppLogEntry(
    val id: Long,
    val timestamp: Long,
    val level: AppLogLevel,
    val module: String,
    val message: String,
    val throwableSummary: String? = null
)
