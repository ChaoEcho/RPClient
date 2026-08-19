package me.kafuuneko.rpclient.feature.llmproviderlist

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.LLMProviderEditActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.model.LLMProviderListItem
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListLoadState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListDialogState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiIntent
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 模型配置列表页状态持有者，负责配置导航和启停状态同步。 */
class LLMProviderListViewModel : CoreViewModelWithEvent<LLMProviderListUiIntent, LLMProviderListUiState>(
    LLMProviderListUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(LLMProviderListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<LLMProviderListUiState.None>()) return
        LLMProviderListUiState.Normal(
            providers = emptyList(),
            loadState = LLMProviderListLoadState.Loading
        ).setup()
        refreshProviders()
    }

    @UiIntentObserver(LLMProviderListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        refreshProviders()
    }

    @UiIntentObserver(LLMProviderListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<LLMProviderListUiState.Finished>()) return
        LLMProviderListUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(LLMProviderListUiIntent.CreateProvider::class)
    private fun onCreateProvider() {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        AppViewEvent.StartActivity(LLMProviderEditActivity::class.java).tryEmit()
    }

    @UiIntentObserver(LLMProviderListUiIntent.EditProvider::class)
    private fun onEditProvider(intent: LLMProviderListUiIntent.EditProvider) {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        val providerId = intent.providerId.toLongOrNull() ?: return
        AppViewEvent.StartActivity(
            activity = LLMProviderEditActivity::class.java,
            extras = Bundle().apply { putLong(LLMProviderEditActivity.EXTRA_PROVIDER_ID, providerId) }
        ).tryEmit()
    }

    @UiIntentObserver(LLMProviderListUiIntent.ToggleProviderEnabled::class)
    private suspend fun onToggleProviderEnabled(intent: LLMProviderListUiIntent.ToggleProviderEnabled) {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        val providerId = intent.providerId.toLongOrNull() ?: return
        mLLMRepository.updateProviderEnabled(providerId, intent.isEnabled)
        refreshProviders()
    }

    @UiIntentObserver(LLMProviderListUiIntent.ShowDeleteProviderDialog::class)
    private suspend fun onShowDeleteProviderDialog(
        intent: LLMProviderListUiIntent.ShowDeleteProviderDialog
    ) {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val providerId = intent.providerId.toLongOrNull() ?: return
        val provider = uiState.providers.firstOrNull { it.id == providerId } ?: return
        val associationCount = mLLMRepository.getCharacterAssociationCount(providerId)
        uiState.copy(
            dialogState = LLMProviderListDialogState.DeleteProvider(
                providerId = providerId,
                providerName = provider.name,
                associatedCharacterCount = associationCount
            )
        ).setup()
    }

    @UiIntentObserver(LLMProviderListUiIntent.ConfirmDeleteProvider::class)
    private suspend fun onConfirmDeleteProvider() {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? LLMProviderListDialogState.DeleteProvider
            ?: return
        if (dialogState.isDeleting) return
        uiState.copy(dialogState = dialogState.copy(isDeleting = true)).setup()
        try {
            mLLMRepository.deleteProvider(dialogState.providerId)
            val current = getOrNull<LLMProviderListUiState.Normal>() ?: return
            current.copy(dialogState = LLMProviderListDialogState.None).setup()
            refreshProviders()
            AppViewEvent.PopupToastMessageByResId(R.string.model_config_deleted).tryEmit()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.model_config_delete_failed).tryEmit()
            val current = getOrNull<LLMProviderListUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? LLMProviderListDialogState.DeleteProvider
                ?: return
            current.copy(dialogState = currentDialog.copy(isDeleting = false)).setup()
        }
    }

    @UiIntentObserver(LLMProviderListUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? LLMProviderListDialogState.DeleteProvider
            ?: return
        if (dialogState.isDeleting) return
        uiState.copy(dialogState = LLMProviderListDialogState.None).setup()
    }

    /**
     * 从数据库刷新完整模型列表。
     */
    private suspend fun refreshProviders() {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val providers = mLLMRepository.getAllProviders().map { provider ->
            LLMProviderListItem(
                id = provider.id,
                name = provider.name,
                providerType = provider.providerType,
                protocol = provider.protocol,
                baseUrl = provider.baseUrl,
                model = provider.model,
                isEnabled = provider.isEnabled
            )
        }
        uiState.copy(
            providers = providers,
            loadState = LLMProviderListLoadState.None
        ).setup()
    }
}
