package me.kafuuneko.rpclient.feature.worldinfobudget

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.worldinfobudget.presentation.WorldInfoBudgetSettingsUiIntent
import me.kafuuneko.rpclient.feature.worldinfobudget.presentation.WorldInfoBudgetSettingsUiState
import me.kafuuneko.rpclient.feature.worldinfobudget.ui.WorldInfoBudgetSettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class WorldInfoBudgetSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<WorldInfoBudgetSettingsViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is WorldInfoBudgetSettingsUiState.Finished) finish()
        }

        WorldInfoBudgetSettingsLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(WorldInfoBudgetSettingsUiIntent.Init)
    }
}
