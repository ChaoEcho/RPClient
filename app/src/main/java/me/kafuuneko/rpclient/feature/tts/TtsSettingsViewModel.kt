package me.kafuuneko.rpclient.feature.tts

import android.os.Bundle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.tts.presentation.TtsPreviewState
import me.kafuuneko.rpclient.feature.tts.presentation.TtsProviderListItem
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiIntent
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiState
import me.kafuuneko.rpclient.feature.ttsprovideredit.TtsProviderEditActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.tts.TtsProviderType
import me.kafuuneko.rpclient.libs.tts.TtsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 语音服务列表页：选择当前服务、进入详情、跨服务通用的试听。 */
class TtsSettingsViewModel : CoreViewModelWithEvent<TtsSettingsUiIntent, TtsSettingsUiState>(
    TtsSettingsUiState.None
), KoinComponent {
    private val mTtsService by inject<TtsService>()
    private var mPreviewJob: Job? = null
    private var mPreviewGeneration = 0L

    @UiIntentObserver(TtsSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<TtsSettingsUiState.None>()) return
        TtsSettingsUiState.Normal(providers = readProviders()).setup()
    }

    /** 详情页里改过的配置状态要在返回后立刻反映到卡片上。 */
    @UiIntentObserver(TtsSettingsUiIntent.Resume::class)
    private fun onResume() {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        state.copy(providers = readProviders()).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.Back::class)
    private fun onBack() {
        stopPreviewInternal()
        val currentState = uiStateFlow.value
        if (currentState is TtsSettingsUiState.Finished) return
        TtsSettingsUiState.Finished(currentState).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.SelectProvider::class)
    private fun onSelectProvider(intent: TtsSettingsUiIntent.SelectProvider) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        AppModel.ttsProvider = intent.provider.persistedValue
        state.copy(providers = readProviders()).setup()
    }

    @UiIntentObserver(TtsSettingsUiIntent.OpenProviderEdit::class)
    private fun onOpenProviderEdit(intent: TtsSettingsUiIntent.OpenProviderEdit) {
        if (!isStateOf<TtsSettingsUiState.Normal>()) return
        stopPreviewInternal()
        AppViewEvent.StartActivity(
            activity = TtsProviderEditActivity::class.java,
            extras = Bundle().apply {
                putString(
                    TtsProviderEditActivity.EXTRA_PROVIDER_TYPE,
                    intent.provider.persistedValue
                )
            }
        ).tryEmit()
    }

    @UiIntentObserver(TtsSettingsUiIntent.PreviewSpeech::class)
    private fun onPreviewSpeech(intent: TtsSettingsUiIntent.PreviewSpeech) {
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        if (intent.text.isBlank()) return

        stopPreviewInternal()
        mPreviewGeneration += 1L
        val generation = mPreviewGeneration
        val job = viewModelScope.launch {
            state.copy(previewState = TtsPreviewState.Loading).setup()
            try {
                mTtsService.speak(intent.text) {
                    if (mPreviewGeneration == generation) {
                        val current = getOrNull<TtsSettingsUiState.Normal>() ?: return@speak
                        current.copy(previewState = TtsPreviewState.Playing).setup()
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (error: Throwable) {
                if (mPreviewGeneration == generation) {
                    val message = error.message?.takeIf { it.isNotBlank() }
                    if (message != null) {
                        AppViewEvent.PopupToastMessage(message).tryEmit()
                    } else {
                        AppViewEvent.PopupToastMessageByResId(R.string.tts_preview_failed).tryEmit()
                    }
                }
            } finally {
                if (mPreviewGeneration == generation) {
                    val current = getOrNull<TtsSettingsUiState.Normal>()
                    current?.copy(previewState = TtsPreviewState.Idle)?.setup()
                }
            }
        }
        mPreviewJob = job
    }

    @UiIntentObserver(TtsSettingsUiIntent.StopPreview::class)
    private fun onStopPreview() {
        stopPreviewInternal()
    }

    override fun onCleared() {
        stopPreviewInternal()
        super.onCleared()
    }

    private fun stopPreviewInternal() {
        mPreviewGeneration += 1L
        mPreviewJob?.cancel()
        mPreviewJob = null
        mTtsService.stop()
        val state = getOrNull<TtsSettingsUiState.Normal>() ?: return
        if (state.previewState != TtsPreviewState.Idle) {
            state.copy(previewState = TtsPreviewState.Idle).setup()
        }
    }

    private fun readProviders(): List<TtsProviderListItem> {
        val current = TtsProviderType.fromPersistedValue(AppModel.ttsProvider)
        return TtsProviderType.entries.map { provider ->
            TtsProviderListItem(
                provider = provider,
                isCurrent = provider == current,
                isConfigured = when (provider) {
                    // 系统朗读走设备自带引擎，没有需要填的凭据。
                    TtsProviderType.System -> true
                    TtsProviderType.Mimo ->
                        AppModel.ttsMimoBaseUrl.isNotBlank() && AppModel.ttsMimoApiKey.isNotBlank()
                    TtsProviderType.Azure ->
                        AppModel.ttsAzureApiKey.isNotBlank() && AppModel.ttsAzureRegion.isNotBlank()
                }
            )
        }
    }
}
