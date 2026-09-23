package me.kafuuneko.rpclient.libs.generation

import android.content.Context
import me.kafuuneko.rpclient.service.AiGenerationForegroundService

/** 应用级生成任务持有前台服务租约；系统拒绝启动时不会遗留虚假的活跃计数。 */
class AiTaskForegroundController(context: Context) {
    private val appContext = context.applicationContext
    private val startGate = ForegroundStartGate(
        start = { count, serial -> AiGenerationForegroundService.start(appContext, count, serial) },
        stop = { AiGenerationForegroundService.stop(appContext) }
    )
    private val counter = TaskLeaseCounter(startGate::update)

    fun acquire(): AutoCloseable = counter.acquire()

    /** 所有待处理的前台启动请求都完成晋升后，才可以结束最后一条短任务。 */
    fun onServiceForeground(serial: Long) = startGate.onServiceForeground(serial)
}

/** stopService 不能抢在尚未交付的 startForegroundService 之前，否则系统仍会要求前台晋升。 */
internal class ForegroundStartGate(
    private val start: (Int, Long) -> Unit,
    private val stop: () -> Unit
) {
    private val lock = Any()
    private var lastStartSerial = 0L
    private var lastPromotedSerial = 0L
    private var lastStoppedSerial = 0L
    private var activeCount = 0

    fun update(count: Int) = synchronized(lock) {
        if (count > 0) {
            val serial = lastStartSerial + 1
            start(count, serial)
            lastStartSerial = serial
            activeCount = count
        } else {
            activeCount = 0
            stopIfReady()
        }
    }

    fun onServiceForeground(serial: Long) = synchronized(lock) {
        lastPromotedSerial = maxOf(lastPromotedSerial, serial)
        stopIfReady()
    }

    private fun stopIfReady() {
        if (activeCount != 0 || lastStartSerial <= lastStoppedSerial || lastPromotedSerial < lastStartSerial) return
        stop()
        lastStoppedSerial = lastStartSerial
    }
}
