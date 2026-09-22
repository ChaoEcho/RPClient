package me.kafuuneko.rpclient.feature.recovery

import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.recovery.presentation.RecoveryViewEvent
import me.kafuuneko.rpclient.feature.recovery.ui.RecoveryLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 恢复失败时保留用户数据，允许重试，不提供清库或跳过一致性检查的入口。 */
class RecoveryActivity : CoreActivityWithEvent() {
    override val managesDataRecovery: Boolean get() = true
    private val model by viewModels<RecoveryViewModel>()
    override fun getViewEventFlow() = model.viewEventFlow

    @Composable
    override fun ViewContent() {
        val state by model.uiStateFlow.collectAsState()
        RecoveryLayout(state, model::emit)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            RecoveryViewEvent.ReopenApp -> {
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
                finish()
            }
            RecoveryViewEvent.CloseApp -> finishAffinity()
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
