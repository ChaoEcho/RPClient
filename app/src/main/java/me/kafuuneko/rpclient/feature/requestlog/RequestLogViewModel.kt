package me.kafuuneko.rpclient.feature.requestlog

import android.content.Context

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogDialogState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogViewEvent
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.model.LLMRequestLogOverview
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.utils.formatTimestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 请求日志页状态持有者。
 *
 * 核心职责：
 * - 从数据库分批拉取请求日志元数据和有限预览；
 * - 提供请求体与响应体 JSON 的剪贴板复制；
 * - 驱动 JSON 查看器（JsonViewer）全屏展开浏览；
 * - 支持清空全部历史请求日志并更新 UI 展示。
 */
class RequestLogViewModel : CoreViewModelWithEvent<RequestLogUiIntent, RequestLogUiState>(
    RequestLogUiState.None
), KoinComponent {
    private val mLLMRequestLogRepository by inject<LLMRequestLogRepository>()
    private val mContext by inject<Context>()

    /** 初始化日志列表，从数据库加载首批轻量摘要。 */
    @UiIntentObserver(RequestLogUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<RequestLogUiState.None>()) return
        val page = loadLogPage(offset = 0)
        RequestLogUiState.Normal(
            logs = page.items,
            canLoadMore = page.canLoadMore
        ).setup()
    }

    /** 滚动至列表末尾时继续加载下一批轻量日志摘要。 */
    @UiIntentObserver(RequestLogUiIntent.LoadMore::class)
    private suspend fun onLoadMore() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        if (!uiState.canLoadMore || uiState.isLoadingMore) return

        uiState.copy(isLoadingMore = true).setup()
        val page = loadLogPage(offset = uiState.logs.size)
        uiState.copy(
            logs = uiState.logs + page.items,
            canLoadMore = page.canLoadMore,
            isLoadingMore = false
        ).setup()
    }

    /** 触发系统文档创建器，选好位置后再真正读库写盘。 */
    @UiIntentObserver(RequestLogUiIntent.ExportLogsClick::class)
    private fun onExportLogsClick() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        if (uiState.logs.isEmpty()) return
        val timestamp = System.currentTimeMillis().formatTimestamp("yyyyMMdd-HHmmss")
        RequestLogViewEvent.OpenLogExporter("rpclient-request-log-$timestamp.ndjson").tryEmit()
    }

    /**
     * 以 NDJSON 逐行写出全部请求日志。
     *
     * 每条日志都带完整请求与响应体，全部拼成一个字符串足以撑爆内存，因此按页读、按行写。
     */
    @UiIntentObserver(RequestLogUiIntent.ExportLogsResult::class)
    private suspend fun onExportLogsResult(intent: RequestLogUiIntent.ExportLogsResult) {
        if (!isStateOf<RequestLogUiState.Normal>()) return
        val succeeded = withContext(Dispatchers.IO) {
            runCatching {
                val output = mContext.contentResolver.openOutputStream(intent.uri)
                    ?: error("Cannot open export target")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    var offset = 0
                    while (true) {
                        val page = mLLMRequestLogRepository.getLogs(PAGE_SIZE, offset)
                        if (page.isEmpty()) break
                        page.forEach { log ->
                            writer.write(EXPORT_GSON.toJson(log.toExportJson()))
                            writer.newLine()
                        }
                        offset += page.size
                    }
                }
            }.isSuccess
        }
        AppViewEvent.PopupToastMessageByResId(
            if (succeeded) R.string.log_export_success else R.string.log_export_failed
        ).tryEmit()
    }

    /** 处理返回操作，迁移至 Finished 状态。 */
    @UiIntentObserver(RequestLogUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<RequestLogUiState.Finished>()) return
        RequestLogUiState.finished(uiStateFlow.value).setup()
    }

    /** 复制指定日志的请求体 JSON 字符串至剪贴板。 */
    @UiIntentObserver(RequestLogUiIntent.CopyRequestJson::class)
    private suspend fun onCopyRequestJson(intent: RequestLogUiIntent.CopyRequestJson) {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        if (uiState.logs.none { it.id == intent.logId }) return
        val json = loadRequestJson(intent.logId) ?: return
        RequestLogViewEvent.CopyText(json).emit()
    }

    /** 复制指定日志的响应体 JSON 字符串至剪贴板。 */
    @UiIntentObserver(RequestLogUiIntent.CopyResponseJson::class)
    private suspend fun onCopyResponseJson(intent: RequestLogUiIntent.CopyResponseJson) {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        if (uiState.logs.none { it.id == intent.logId }) return
        val json = loadResponseJson(intent.logId) ?: return
        RequestLogViewEvent.CopyText(json).emit()
    }

    /** 打开 JSON 查看器展示指定日志的完整请求体。 */
    @UiIntentObserver(RequestLogUiIntent.OpenRequestJson::class)
    private suspend fun onOpenRequestJson(intent: RequestLogUiIntent.OpenRequestJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        val json = loadRequestJson(intent.logId) ?: return
        RequestLogViewEvent.OpenJson(title = "${log.title} / ${intent.title}", json = json).emit()
    }

    /** 打开 JSON 查看器展示指定日志的完整响应体。 */
    @UiIntentObserver(RequestLogUiIntent.OpenResponseJson::class)
    private suspend fun onOpenResponseJson(intent: RequestLogUiIntent.OpenResponseJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        val json = loadResponseJson(intent.logId) ?: return
        RequestLogViewEvent.OpenJson(title = "${log.title} / ${intent.title}", json = json).emit()
    }

    /** 弹出清空全部日志的二次确认弹窗。 */
    @UiIntentObserver(RequestLogUiIntent.ShowClearConfirmDialog::class)
    private fun onShowClearConfirmDialog() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        uiState.copy(dialogState = RequestLogDialogState.ClearConfirm).setup()
    }

    /** 用户确认清空日志，删除数据库全部记录并清空列表。 */
    @UiIntentObserver(RequestLogUiIntent.ConfirmClearLogs::class)
    private suspend fun onConfirmClearLogs() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.deleteAll()
        }
        uiState.copy(logs = emptyList(), dialogState = RequestLogDialogState.None).setup()
    }

    /** 关闭当前弹窗。 */
    @UiIntentObserver(RequestLogUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        uiState.copy(dialogState = RequestLogDialogState.None).setup()
    }

    /** 在 IO 线程读取一批轻量日志摘要，多取一条用于判断是否还有下一页。 */
    private suspend fun loadLogPage(offset: Int): RequestLogPage {
        return withContext(Dispatchers.IO) {
            val overviews = mLLMRequestLogRepository.getLogOverviews(
                previewLength = JSON_PREVIEW_LENGTH,
                limit = PAGE_SIZE + 1,
                offset = offset
            )
            RequestLogPage(
                items = overviews.take(PAGE_SIZE).map { it.toUiModel() },
                canLoadMore = overviews.size > PAGE_SIZE
            )
        }
    }

    /** 将数据库日志实体转换为列表展示项模型。 */
    private fun LLMRequestLogOverview.toUiModel(): RequestLogItem {
        val mode = if (isStreaming) "stream" else "once"
        return RequestLogItem(
            id = id,
            title = "$providerName / $model",
            subtitle = "${createTime.formatTimestamp("MM-dd HH:mm:ss")} · ${protocol.name} · $mode",
            requestPreview = requestPreview,
            responsePreview = responsePreview
        )
    }

    /** 从数据库异步加载指定日志的完整请求体 JSON。 */
    private suspend fun loadRequestJson(logId: Long): String? {
        return withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.getRequestJson(logId)
        }
    }

    /** 从数据库异步加载指定日志的完整响应体 JSON。 */
    private suspend fun loadResponseJson(logId: Long): String? {
        return withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.getResponseJson(logId)
        }
    }

    /**
     * 请求体与响应体本身就是 JSON 文本，能解析就内嵌成对象，解析不了再退回字符串，
     * 保证导出文件对 jq 之类的工具直接可用。
     */
    private fun LLMRequestLog.toExportJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("createTime", createTime)
        addProperty("providerName", providerName)
        addProperty("protocol", protocol.name)
        addProperty("model", model)
        addProperty("isStreaming", isStreaming)
        add("request", requestJson.toJsonElementOrString())
        add("response", responseJson.toJsonElementOrString())
    }

    private fun String.toJsonElementOrString(): JsonElement =
        runCatching { JsonParser.parseString(this) }
            .getOrNull()
            ?.takeIf { it.isJsonObject || it.isJsonArray }
            ?: JsonPrimitive(this)

    private companion object {
        val EXPORT_GSON: Gson = Gson()

        /** 单页日志数量。 */
        const val PAGE_SIZE = 50
        /** 列表展示时的 JSON 预览文本截断最大长度。 */
        const val JSON_PREVIEW_LENGTH = 2_000
    }

    private data class RequestLogPage(
        val items: List<RequestLogItem>,
        val canLoadMore: Boolean
    )
}
