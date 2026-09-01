package me.kafuuneko.rpclient.feature.imagegeneration

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiIntent
import me.kafuuneko.rpclient.feature.imagegeneration.presentation.ImageGenerationSettingsUiState
import me.kafuuneko.rpclient.feature.imagegeneration.ui.ImageGenerationSettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.ViewEventWrapper

/** Host activity for OpenAI-compatible image-generation settings. */
class ImageGenerationSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ImageGenerationSettingsViewModel>()

    override fun getViewEventFlow(): Flow<ViewEventWrapper> = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is ImageGenerationSettingsUiState.Finished) finish()
        }

        ImageGenerationSettingsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(ImageGenerationSettingsUiIntent.Init)
    }
}
