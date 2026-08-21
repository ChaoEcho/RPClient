package me.kafuuneko.rpclient.feature.requestlog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogDialogState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.utils.formatTimestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 请求日志页状态持有者。
 *
 * 核心职责：
 * - 从数据库拉取所有大模型调用请求与响应日志；
 * - 提供请求体与响应体 JSON 的剪贴板复制；
 * - 驱动 JSON 查看器（JsonViewer）全屏展开浏览；
 * - 支持清空全部历史请求日志并更新 UI 展示。
 */
class RequestLogViewModel : CoreViewModelWithEvent<RequestLogUiIntent, RequestLogUiState>(
    RequestLogUiState.None
), KoinComponent {
    private val mLLMRequestLogRepository by inject<LLMRequestLogRepository>()

    /** 初始化日志列表，从数据库加载全部调用记录。 */
    @UiIntentObserver(RequestLogUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<RequestLogUiState.None>()) return
        RequestLogUiState.Normal(logs = loadLogs()).setup()
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
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.CopyText(log.requestJson).emit()
    }

    /** 复制指定日志的响应体 JSON 字符串至剪贴板。 */
    @UiIntentObserver(RequestLogUiIntent.CopyResponseJson::class)
    private suspend fun onCopyResponseJson(intent: RequestLogUiIntent.CopyResponseJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.CopyText(log.responseJson).emit()
    }

    /** 打开 JSON 查看器展示指定日志的完整请求体。 */
    @UiIntentObserver(RequestLogUiIntent.OpenRequestJson::class)
    private suspend fun onOpenRequestJson(intent: RequestLogUiIntent.OpenRequestJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.OpenJson(title = "${log.title} / ${intent.title}", json = log.requestJson).emit()
    }

    /** 打开 JSON 查看器展示指定日志的完整响应体。 */
    @UiIntentObserver(RequestLogUiIntent.OpenResponseJson::class)
    private suspend fun onOpenResponseJson(intent: RequestLogUiIntent.OpenResponseJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.OpenJson(title = "${log.title} / ${intent.title}", json = log.responseJson).emit()
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

    /** 在 IO 线程从数据库拉取全部日志并映射为列表 UI 实体。 */
    private suspend fun loadLogs(): List<RequestLogItem> {
        return withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.getAllLogs().map { it.toUiModel() }
        }
    }

    /** 将数据库日志实体转换为列表展示项模型。 */
    private fun LLMRequestLog.toUiModel(): RequestLogItem {
        val mode = if (isStreaming) "stream" else "once"
        return RequestLogItem(
            id = id,
            title = "$providerName / $model",
            subtitle = "${createTime.formatTimestamp("MM-dd HH:mm:ss")} · ${protocol.name} · $mode",
            requestJson = requestJson,
            responseJson = responseJson
        )
    }
}
