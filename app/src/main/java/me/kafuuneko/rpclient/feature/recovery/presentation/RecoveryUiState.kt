package me.kafuuneko.rpclient.feature.recovery.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 恢复页不访问普通业务状态，只展示回滚和重试进度。 */
enum class RecoveryUiState { Idle, Working, Failed }
sealed class RecoveryUiIntent {
    data object Retry : RecoveryUiIntent()
    data object Close : RecoveryUiIntent()
}
sealed class RecoveryViewEvent : IViewEvent {
    data object ReopenApp : RecoveryViewEvent()
    data object CloseApp : RecoveryViewEvent()
}
