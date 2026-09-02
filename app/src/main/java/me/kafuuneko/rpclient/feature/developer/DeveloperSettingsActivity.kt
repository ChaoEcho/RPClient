package me.kafuuneko.rpclient.feature.developer

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiIntent
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiState
import me.kafuuneko.rpclient.feature.developer.ui.DeveloperSettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class DeveloperSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<DeveloperSettingsViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is DeveloperSettingsUiState.Finished) finish()
        }

        DeveloperSettingsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(DeveloperSettingsUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(DeveloperSettingsUiIntent.Resume)
    }
}
