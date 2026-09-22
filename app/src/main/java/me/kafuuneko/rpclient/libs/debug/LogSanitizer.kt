package me.kafuuneko.rpclient.libs.debug

import java.util.Collections
import java.util.IdentityHashMap

/** 所有运行日志出口共享的脱敏规则；原始请求日志仍由独立的显式调试功能管理。 */
internal object LogSanitizer {
    private val authorization = Regex("""(?i)\b(Bearer|Basic)\s+[a-zA-Z0-9+/_.=\-]+""")
    private val assignment = Regex(
        """(?i)(?<![\w-])(["']?)((?:x-)?api[_-]?key|password|secret|access[_-]?token|refresh[_-]?token|token|authorization)\1(?![\w-])\s*[:=]\s*(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s,;&}\]]+)"""
    )
    private val url = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val userInfo = Regex("""(?i)(https?://)[^/@]+@""")
    private val apiKey = Regex("""\bsk-[a-zA-Z0-9_-]{8,}\b""")

    fun redact(value: String): String {
        // URL 查询和片段不属于诊断必需信息，整体丢弃而不是猜测每个供应商的密钥参数名。
        val urlsRemoved = url.replace(value) { match ->
            match.value.substringBefore('?').substringBefore('#').replace(userInfo, "$1***@")
        }
        val authorizationRemoved = authorization.replace(urlsRemoved) { "${it.groupValues[1]} ***" }
        val assignmentsRemoved = assignment.replace(authorizationRemoved) { match ->
            val quote = match.groupValues[1]
            "$quote${match.groupValues[2]}$quote: $quote***$quote"
        }
        return apiKey.replace(assignmentsRemoved, "***")
    }

    /** 只保留异常类型和调用位置，不复制可能包含服务响应、凭据或聊天正文的异常消息。 */
    fun throwableSummary(error: Throwable): String = buildString {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 8 && seen.add(current)) {
            if (depth > 0) append("Caused by: ")
            append(current.javaClass.name).append('\n')
            current.stackTrace.take(32).forEach { frame ->
                append("    at ").append(redact(frame.toString())).append('\n')
            }
            current = current.cause
            depth += 1
        }
    }
}
