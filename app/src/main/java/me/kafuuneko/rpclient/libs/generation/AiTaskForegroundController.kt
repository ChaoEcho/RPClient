package me.kafuuneko.rpclient.libs.generation

import android.content.Context
import me.kafuuneko.rpclient.service.AiGenerationForegroundService

/** 应用级生成任务持有前台服务租约；系统拒绝启动时不会遗留虚假的活跃计数。 */
class AiTaskForegroundController(context: Context) {
    private val appContext = context.applicationContext
    private val counter = TaskLeaseCounter { count ->
        AiGenerationForegroundService.update(appContext, count)
    }

    fun acquire(): AutoCloseable = counter.acquire()
}
