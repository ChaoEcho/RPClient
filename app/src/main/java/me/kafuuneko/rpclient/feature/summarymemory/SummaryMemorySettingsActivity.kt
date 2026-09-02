package me.kafuuneko.rpclient.feature.summarymemory

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryMemorySettingsUiIntent
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryMemorySettingsUiState
import me.kafuuneko.rpclient.feature.summarymemory.ui.SummaryMemorySettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class SummaryMemorySettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<SummaryMemorySettingsViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is SummaryMemorySettingsUiState.Finished) finish()
        }

        SummaryMemorySettingsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(SummaryMemorySettingsUiIntent.Init)
    }
}
