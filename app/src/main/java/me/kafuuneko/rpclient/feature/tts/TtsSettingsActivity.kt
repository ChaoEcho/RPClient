package me.kafuuneko.rpclient.feature.tts

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiIntent
import me.kafuuneko.rpclient.feature.tts.presentation.TtsSettingsUiState
import me.kafuuneko.rpclient.feature.tts.ui.TtsSettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.core.ViewEventWrapper

/** Host activity for global text-to-speech settings and voice preview. */
class TtsSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<TtsSettingsViewModel>()

    override fun getViewEventFlow(): Flow<ViewEventWrapper> = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is TtsSettingsUiState.Finished) finish()
        }

        TtsSettingsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(TtsSettingsUiIntent.Init)
    }
}
