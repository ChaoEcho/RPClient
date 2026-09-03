package me.kafuuneko.rpclient.feature.ttsprovideredit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.TtsProviderEditUiIntent
import me.kafuuneko.rpclient.feature.ttsprovideredit.presentation.TtsProviderEditUiState
import me.kafuuneko.rpclient.feature.ttsprovideredit.ui.TtsProviderEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.tts.TtsProviderType

/** 单个语音服务详情页宿主。 */
class TtsProviderEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<TtsProviderEditViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is TtsProviderEditUiState.Finished) finish()
        }

        TtsProviderEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = TtsProviderType.fromPersistedValue(
            intent.getStringExtra(EXTRA_PROVIDER_TYPE).orEmpty()
        )
        mViewModel.emit(TtsProviderEditUiIntent.Init(provider))
    }

    companion object {
        const val EXTRA_PROVIDER_TYPE = "extra_provider_type"
    }
}
