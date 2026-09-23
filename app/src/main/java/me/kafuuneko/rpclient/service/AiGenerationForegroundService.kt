package me.kafuuneko.rpclient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import me.kafuuneko.rpclient.libs.chat.generation.ChatGenerationCoordinator
import me.kafuuneko.rpclient.libs.chat.generation.ChatImageGenerationCoordinator
import me.kafuuneko.rpclient.libs.generation.AiTaskForegroundController
import org.koin.android.ext.android.inject
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.MainActivity

/** Notification-only foreground service for already submitted single-chat text and image tasks. */
class AiGenerationForegroundService : Service() {
    private val foregroundController by inject<AiTaskForegroundController>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_TASK_COUNT, 0) ?: 0
        // 即使系统交付空 Intent，也先履行前台启动约定再退出，不能留下未完成的晋升。
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(count.coerceAtLeast(1)))
        val serial = intent?.getLongExtra(EXTRA_START_SERIAL, 0L) ?: 0L
        if (count <= 0 || serial <= 0L) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        foregroundController.onServiceForeground(serial)
        return START_NOT_STICKY
    }

    /** Android 的前台服务时限到达后立即停止服务，并请求任务保存 partial 后退出。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val chat by inject<ChatGenerationCoordinator>()
        val images by inject<ChatImageGenerationCoordinator>()
        chat.cancelAll()
        images.cancelAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ai_generation_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(count: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.ai_generation_notification_title))
            .setContentText(getString(R.string.ai_generation_notification_content, count))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ai_generation"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_TASK_COUNT = "task_count"

        private const val EXTRA_START_SERIAL = "start_serial"

        fun start(context: Context, taskCount: Int, serial: Long) {
            require(taskCount > 0)
            val intent = Intent(context, AiGenerationForegroundService::class.java)
                .putExtra(EXTRA_TASK_COUNT, taskCount)
                .putExtra(EXTRA_START_SERIAL, serial)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AiGenerationForegroundService::class.java))
        }
    }
}
