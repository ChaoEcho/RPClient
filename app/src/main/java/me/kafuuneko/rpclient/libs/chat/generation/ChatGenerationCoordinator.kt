package me.kafuuneko.rpclient.libs.chat.generation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.libs.debug.AppLogger
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.libs.generation.AiTaskForegroundController
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection

/**
 * Application-scoped single-chat generation owner, serialized per session.
 *
 * Summary tasks are tracked in a second, independent map so that a running reply never blocks a
 * manual summary and so that auto-triggered summaries stay cancellable from the UI.
 */
class ChatGenerationCoordinator(
    private val foregroundController: AiTaskForegroundController? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeBySession = mutableMapOf<Long, ActiveGeneration>()

    /**
     * 摘要任务表，键由调用方命名（`chat:{id}` / `group:{id}`）。
     * 单聊与群聊的会话 ID 来自不同的表，会互相碰撞，因此这里不能直接用 Long 会话 ID。
     */
    private val summaryByKey = mutableMapOf<String, ActiveGeneration>()
    private val mutableSnapshotBySession =
        MutableStateFlow<Map<Long, ChatGenerationState>>(emptyMap())
    val snapshotBySession: StateFlow<Map<Long, ChatGenerationState>> =
        mutableSnapshotBySession.asStateFlow()
    private val promptInspections = mutableMapOf<Long, PromptInspection>()

    /** Starts one task per session; other sessions remain independent. */
    @Synchronized
    fun launch(sessionId: Long, block: suspend () -> Unit): ChatGenerationStartResult {
        activeBySession[sessionId]
            ?.takeIf { !it.job.isCompleted }
            ?.let { return ChatGenerationStartResult.Busy(sessionId) }

        val token = Any()
        AppLogger.i("Chat", "Generation task scheduled for session: $sessionId")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            // acquire() must stay inside try: a foreground-service start rejection would otherwise
            // skip the whole finally and leave the session permanently marked as generating.
            var foregroundHandle: AutoCloseable? = null
            try {
                foregroundHandle = acquireForegroundOrNull()
                AppLogger.i("Chat", "Generation task started for session: $sessionId")
                block()
                AppLogger.i("Chat", "Generation task finished for session: $sessionId")
            } catch (e: Throwable) {
                AppLogger.e("Chat", "Generation task failed for session $sessionId: ${e.message}", e)
                throw e
            } finally {
                runCatching { foregroundHandle?.close() }
                synchronized(this@ChatGenerationCoordinator) {
                    if (activeBySession[sessionId]?.token === token) {
                        activeBySession.remove(sessionId)
                    }
                    if (mutableSnapshotBySession.value[sessionId]?.isGenerating() == true) {
                        mutableSnapshotBySession.value =
                            mutableSnapshotBySession.value - sessionId
                    }
                }
            }
        }
        activeBySession[sessionId] = ActiveGeneration(job, token)
        job.start()
        return ChatGenerationStartResult.Started(job)
    }

    /**
     * Starts the summary task for [key], refusing a second one instead of cancelling the first.
     *
     * Manual and automatic summaries share this single entry point, so the UI always has a handle
     * to cancel and never leaves an untracked task running in the background.
     */
    @Synchronized
    fun launchSummary(key: String, block: suspend () -> Unit): Boolean {
        summaryByKey[key]
            ?.takeIf { !it.job.isCompleted }
            ?.let { return false }

        val token = Any()
        AppLogger.i("Summary", "Summary task scheduled for $key")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var foregroundHandle: AutoCloseable? = null
            try {
                foregroundHandle = acquireForegroundOrNull()
                AppLogger.i("Summary", "Summary task started for $key")
                block()
                AppLogger.i("Summary", "Summary task finished for $key")
            } catch (e: Throwable) {
                AppLogger.e("Summary", "Summary task failed for $key: ${e.message}", e)
                throw e
            } finally {
                runCatching { foregroundHandle?.close() }
                synchronized(this@ChatGenerationCoordinator) {
                    if (summaryByKey[key]?.token === token) {
                        summaryByKey.remove(key)
                    }
                }
            }
        }
        summaryByKey[key] = ActiveGeneration(job, token)
        job.start()
        return true
    }

    /**
     * Cancels the summary task for [key] without waiting for it.
     *
     * Callers run on the single sequential UI-intent collector, so joining here would freeze every
     * other intent until the in-flight HTTP read times out.
     */
    @Synchronized
    fun stopSummary(key: String): Boolean {
        val job = summaryByKey[key]?.takeIf { !it.job.isCompleted }?.job ?: return false
        AppLogger.i("Summary", "Cancelling summary for $key")
        job.cancel()
        return true
    }

    @Synchronized
    fun isSummaryActive(key: String): Boolean =
        summaryByKey[key]?.job?.isCompleted == false

    private fun acquireForegroundOrNull(): AutoCloseable? =
        runCatching { foregroundController?.acquire() }
            .onFailure { AppLogger.w("Chat", "Foreground service unavailable: ${it.message}") }
            .getOrNull()

    /** Stops only the requested session and waits for its NonCancellable persistence cleanup. */
    suspend fun stop(sessionId: Long): Boolean {
        val job = synchronized(this) {
            activeBySession[sessionId]?.takeIf { !it.job.isCompleted }?.job
        } ?: return false
        AppLogger.i("Chat", "Cancelling generation for session: $sessionId")
        job.cancelAndJoin()
        return true
    }

    @Synchronized
    fun publish(sessionId: Long, state: ChatGenerationState) {
        mutableSnapshotBySession.value = if (state == ChatGenerationState.Idle) {
            mutableSnapshotBySession.value - sessionId
        } else {
            mutableSnapshotBySession.value + (sessionId to state)
        }
    }

    @Synchronized
    fun isActive(sessionId: Long): Boolean =
        activeBySession[sessionId]?.job?.isCompleted == false

    @Synchronized
    fun activeSessionIds(): Set<Long> = activeBySession
        .filterValues { !it.job.isCompleted }
        .keys
        .toSet()

    fun stateFor(sessionId: Long): ChatGenerationState? = snapshotBySession.value[sessionId]

    @Synchronized
    fun recordPromptInspection(sessionId: Long, inspection: PromptInspection) {
        promptInspections[sessionId] = inspection
    }

    @Synchronized
    fun getPromptInspection(sessionId: Long): PromptInspection? = promptInspections[sessionId]

    private data class ActiveGeneration(
        val job: Job,
        val token: Any
    )
}

sealed interface ChatGenerationStartResult {
    data class Started(val job: Job) : ChatGenerationStartResult
    data class Busy(val sessionId: Long) : ChatGenerationStartResult
}

fun ChatGenerationState.isGenerating(): Boolean =
    this is ChatGenerationState.Requesting || this is ChatGenerationState.Streaming

/** 单聊会话的摘要任务键。 */
fun chatSummaryKey(sessionId: Long): String = "chat:$sessionId"

/** 群聊会话的摘要任务键；与单聊 ID 空间独立，不能共用同一个数字键。 */
fun groupChatSummaryKey(sessionId: Long): String = "group:$sessionId"
