package me.kafuuneko.rpclient.feature.recovery

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.feature.recovery.presentation.RecoveryViewEvent
import me.kafuuneko.rpclient.feature.recovery.presentation.RecoveryUiIntent
import me.kafuuneko.rpclient.feature.recovery.presentation.RecoveryUiState
import me.kafuuneko.rpclient.libs.backup.BackupRepository
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.generation.DataMaintenance
import me.kafuuneko.rpclient.libs.upgrade.AppUpgradeManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 管理面恢复动作是唯一可以在普通数据操作被阻止时运行的页面任务。 */
class RecoveryViewModel : CoreViewModelWithEvent<RecoveryUiIntent, RecoveryUiState>(RecoveryUiState.Idle), KoinComponent {
    override val managesDataRecovery: Boolean get() = true
    private val repository by inject<BackupRepository>()
    private val upgrades by inject<AppUpgradeManager>()

    @UiIntentObserver(RecoveryUiIntent.Retry::class)
    private fun retry() {
        if (uiStateFlow.value == RecoveryUiState.Working) return
        RecoveryUiState.Working.setup()
        // 此协程发起独占恢复，不登记为会被恢复本身取消的普通业务任务。
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DataMaintenance.barrier.exclusive {
                    repository.recoverInterruptedRestore()
                    upgrades.upgrade()
                }
                RecoveryViewEvent.ReopenApp.emit()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DataMaintenance.barrier.requireRecovery()
                RecoveryUiState.Failed.setup()
            }
        }
    }

    @UiIntentObserver(RecoveryUiIntent.Close::class)
    private fun close() {
        if (uiStateFlow.value != RecoveryUiState.Working) RecoveryViewEvent.CloseApp.tryEmit()
    }
}
