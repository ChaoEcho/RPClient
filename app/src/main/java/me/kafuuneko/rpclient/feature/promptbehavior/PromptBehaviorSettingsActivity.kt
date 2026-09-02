package me.kafuuneko.rpclient.feature.promptbehavior

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiIntent
import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiState
import me.kafuuneko.rpclient.feature.promptbehavior.ui.PromptBehaviorSettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class PromptBehaviorSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<PromptBehaviorSettingsViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is PromptBehaviorSettingsUiState.Finished) finish()
        }

        PromptBehaviorSettingsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(PromptBehaviorSettingsUiIntent.Init)
    }
}
