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
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.libs.generation.AiTaskForegroundController
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection

/** Application-scoped single-chat generation owner, serialized per session. */
class ChatGenerationCoordinator(
    private val foregroundController: AiTaskForegroundController? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeBySession = mutableMapOf<Long, ActiveGeneration>()
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
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val foregroundHandle = foregroundController?.acquire()
            try {
                block()
            } finally {
                foregroundHandle?.close()
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

    /** Stops only the requested session and waits for its NonCancellable persistence cleanup. */
    suspend fun stop(sessionId: Long): Boolean {
        val job = synchronized(this) {
            activeBySession[sessionId]?.takeIf { !it.job.isCompleted }?.job
        } ?: return false
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
