package me.kafuuneko.rpclient.libs.generation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** 完整替换数据期间的任务准入与界面代次，恢复失败也必须丢弃旧页面的延迟操作。 */
data class DataMaintenanceState(
    val epoch: Long = 0,
    val running: Boolean = false,
    val recoveryRequired: Boolean = false
)

internal class DataMaintenanceCancellation : CancellationException("Application data is being replaced")

/**
 * 先关闭任务准入，再取消并等待在途任务的持久化收尾，最后才允许替换数据。
 *
 * 不接管页面状态、协议请求或数据库；调用方仍使用原有作用域与 Repository。
 */
class DataOperationBarrier(private val quiesceTimeoutMillis: Long = 30_000) {
    private val lock = Any()
    private val maintenanceMutex = Mutex()
    private val operations = mutableSetOf<Job>()
    private val mutableState = MutableStateFlow(DataMaintenanceState())
    val state: StateFlow<DataMaintenanceState> = mutableState.asStateFlow()
    val epoch: Long get() = state.value.epoch

    fun launch(
        scope: CoroutineScope,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        expectedEpoch: Long? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        require(start == CoroutineStart.DEFAULT || start == CoroutineStart.LAZY)
        // LAZY 保证登记发生在首条业务指令之前，也使恢复能取消尚未开始的任务。
        val job = scope.launch(context, CoroutineStart.LAZY, block)
        if (!register(job, expectedEpoch)) job.cancel(DataMaintenanceCancellation())
        if (start != CoroutineStart.LAZY) job.start()
        return job
    }

    suspend fun <T> operation(expectedEpoch: Long? = null, block: suspend CoroutineScope.() -> T): T = coroutineScope {
        val job = currentCoroutineContext().job
        if (!register(job, expectedEpoch)) throw DataMaintenanceCancellation()
        // 完成回调在所有结构化子任务结束后才注销，不能在 block 返回时提前注销。
        currentCoroutineContext().ensureActive()
        block()
    }

    suspend fun <T> exclusive(block: suspend () -> T): T = maintenanceMutex.withLock {
        val caller = currentCoroutineContext().job
        val pending = synchronized(lock) {
            mutableState.value = mutableState.value.copy(epoch = epoch + 1, running = true)
            // 恢复可以从已登记的页面任务发起，不能取消或等待它自己的祖先任务。
            operations.filterNot { it === caller || it.containsDescendant(caller) }.toList()
        }
        try {
            pending.forEach { it.cancel(DataMaintenanceCancellation()) }
            try {
                withTimeout(quiesceTimeoutMillis) { pending.forEach { it.join() } }
            } catch (timeout: TimeoutCancellationException) {
                throw IllegalStateException("Unable to finish active operations before restore", timeout)
            }
            currentCoroutineContext().ensureActive()
            block()
        } finally {
            synchronized(lock) { mutableState.value = mutableState.value.copy(running = false) }
        }
    }

    fun requireRecovery() = synchronized(lock) {
        mutableState.value = mutableState.value.copy(recoveryRequired = true)
    }

    fun recovered() = synchronized(lock) {
        mutableState.value = mutableState.value.copy(recoveryRequired = false)
    }

    private fun register(job: Job, expectedEpoch: Long? = null): Boolean = synchronized(lock) {
        if (expectedEpoch != null && expectedEpoch != epoch) return@synchronized false
        if (mutableState.value.running || mutableState.value.recoveryRequired) return@synchronized false
        operations.add(job)
        job.invokeOnCompletion { synchronized(lock) { operations.remove(job) } }
        true
    }

    private fun Job.containsDescendant(target: Job): Boolean =
        children.any { it === target || it.containsDescendant(target) }
}

/** 应用只有一个业务数据替换边界，测试可独立实例化 [DataOperationBarrier]。 */
object DataMaintenance {
    val barrier = DataOperationBarrier()
    val epoch: Long get() = barrier.epoch
    val state: StateFlow<DataMaintenanceState> get() = barrier.state

    fun isInterruption(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(16).any { it is DataMaintenanceCancellation }
}

/** 与普通 launch 相同的业务作用域，附加完整恢复所需的任务登记。 */
fun CoroutineScope.launchDataTask(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job = DataMaintenance.barrier.launch(this, context, start, block = block)
