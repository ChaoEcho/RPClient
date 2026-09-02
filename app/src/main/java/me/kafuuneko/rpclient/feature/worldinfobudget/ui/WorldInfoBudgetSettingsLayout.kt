package me.kafuuneko.rpclient.feature.worldinfobudget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldinfobudget.presentation.WorldInfoBudgetSettingsUiIntent
import me.kafuuneko.rpclient.feature.worldinfobudget.presentation.WorldInfoBudgetSettingsUiState
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpNumberSettingRow
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPercentageSlider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile

@Composable
fun WorldInfoBudgetSettingsLayout(
    uiState: WorldInfoBudgetSettingsUiState,
    emit: WorldInfoBudgetSettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is WorldInfoBudgetSettingsUiState.Normal) {
        WorldInfoBudgetSettingsUiIntent.Back.emit()
    }

    when (uiState) {
        WorldInfoBudgetSettingsUiState.None -> Unit
        is WorldInfoBudgetSettingsUiState.Finished -> Unit
        is WorldInfoBudgetSettingsUiState.Normal -> WorldInfoBudgetSettingsNormal(uiState, emit)
    }
}

@Composable
private fun WorldInfoBudgetSettingsNormal(
    state: WorldInfoBudgetSettingsUiState.Normal,
    emit: WorldInfoBudgetSettingsUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.world_info_budget_title),
            onBack = { WorldInfoBudgetSettingsUiIntent.Back.emit() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.world_info_budget_title),
                    subtitle = stringResource(R.string.world_info_budget_subtitle)
                )
            }

            item {
                RpSettingsGroup {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.world_info_budget_section),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.world_info_budget_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                        RpPercentageSlider(
                            title = stringResource(R.string.world_info_context_percent),
                            value = state.budgetPercent,
                            helper = stringResource(R.string.world_info_context_percent_helper),
                            onValueChange = { WorldInfoBudgetSettingsUiIntent.ChangeBudgetPercent(it).emit() }
                        )
                        RpNumberSettingRow(
                            title = stringResource(R.string.world_info_budget_cap),
                            value = state.budgetCap.toString(),
                            helper = stringResource(R.string.world_info_budget_cap_helper),
                            onValueChange = { WorldInfoBudgetSettingsUiIntent.ChangeBudgetCap(it).emit() }
                        )
                    }
                    RpSettingsDivider(startIndent = false)
                    RpSettingsSwitchTile(
                        icon = Icons.Rounded.Book,
                        iconColor = Color(0xFF10B981),
                        iconContainerColor = Color(0xFF10B981).copy(alpha = 0.14f),
                        title = stringResource(R.string.world_info_overflow_alert),
                        subtitle = stringResource(R.string.world_info_overflow_alert_desc),
                        checked = state.overflowAlert,
                        onCheckedChange = { WorldInfoBudgetSettingsUiIntent.ToggleOverflowAlert(it).emit() }
                    )
                }
            }
        }
    }
}
