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
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection

/**
 * 应用进程内唯一的单聊生成任务持有者。
 *
 * 任务运行在 Application 级协程作用域，因此离开 ChatActivity 不会取消生成；但应用进程被系统
 * 终止后任务仍会结束，本类不提供跨进程恢复保证。
 */
class ChatGenerationCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableSnapshot = MutableStateFlow<ChatGenerationSnapshot?>(null)
    val snapshot: StateFlow<ChatGenerationSnapshot?> = mutableSnapshot.asStateFlow()

    @Volatile
    private var active: ActiveGeneration? = null
    private val promptInspections = mutableMapOf<Long, PromptInspection>()

    /** 全局只允许一个单聊生成任务。 */
    @Synchronized
    fun launch(sessionId: Long, block: suspend () -> Unit): ChatGenerationStartResult {
        val running = active
        if (running != null && !running.job.isCompleted) {
            return ChatGenerationStartResult.Busy(running.sessionId)
        }

        val token = Any()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                synchronized(this@ChatGenerationCoordinator) {
                    if (active?.token === token) active = null
                }
                val current = mutableSnapshot.value
                if (current?.sessionId == sessionId && current.state.isGenerating()) {
                    mutableSnapshot.value = ChatGenerationSnapshot(
                        sessionId = sessionId,
                        state = ChatGenerationState.Idle
                    )
                }
            }
        }
        active = ActiveGeneration(sessionId = sessionId, job = job, token = token)
        job.start()
        return ChatGenerationStartResult.Started(job)
    }

    /** 只停止与 [sessionId] 匹配的活跃任务，并等待 NonCancellable 持久化收尾完成。 */
    suspend fun stop(sessionId: Long): Boolean {
        val job = synchronized(this) {
            active?.takeIf { it.sessionId == sessionId && !it.job.isCompleted }?.job
        } ?: return false
        job.cancelAndJoin()
        return true
    }

    fun publish(sessionId: Long, state: ChatGenerationState) {
        mutableSnapshot.value = ChatGenerationSnapshot(sessionId, state)
    }

    @Synchronized
    fun recordPromptInspection(sessionId: Long, inspection: PromptInspection) {
        promptInspections[sessionId] = inspection
    }

    @Synchronized
    fun getPromptInspection(sessionId: Long): PromptInspection? = promptInspections[sessionId]

    @Synchronized
    fun activeSessionId(): Long? = active?.takeIf { !it.job.isCompleted }?.sessionId

    private data class ActiveGeneration(
        val sessionId: Long,
        val job: Job,
        val token: Any
    )
}

data class ChatGenerationSnapshot(
    val sessionId: Long,
    val state: ChatGenerationState
)

sealed interface ChatGenerationStartResult {
    data class Started(val job: Job) : ChatGenerationStartResult
    data class Busy(val sessionId: Long) : ChatGenerationStartResult
}

fun ChatGenerationState.isGenerating(): Boolean =
    this is ChatGenerationState.Requesting || this is ChatGenerationState.Streaming
