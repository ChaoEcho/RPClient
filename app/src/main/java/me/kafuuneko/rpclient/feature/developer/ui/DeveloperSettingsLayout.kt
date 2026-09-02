package me.kafuuneko.rpclient.feature.developer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiIntent
import me.kafuuneko.rpclient.feature.developer.presentation.DeveloperSettingsUiState
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpNavigationChevron
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile

@Composable
fun DeveloperSettingsLayout(
    uiState: DeveloperSettingsUiState,
    emit: DeveloperSettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is DeveloperSettingsUiState.Normal) {
        DeveloperSettingsUiIntent.Back.emit()
    }

    when (uiState) {
        DeveloperSettingsUiState.None -> Unit
        is DeveloperSettingsUiState.Finished -> Unit
        is DeveloperSettingsUiState.Normal -> DeveloperSettingsNormal(uiState, emit)
    }
}

@Composable
private fun DeveloperSettingsNormal(
    state: DeveloperSettingsUiState.Normal,
    emit: DeveloperSettingsUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.developer_tools),
            onBack = { DeveloperSettingsUiIntent.Back.emit() }
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
                    title = stringResource(R.string.developer_tools),
                    subtitle = stringResource(R.string.developer_mode_subtitle)
                )
            }

            item {
                RpSectionHeader(title = stringResource(R.string.developer_logging_title))
            }

            item {
                RpSettingsGroup {
                    RpSettingsSwitchTile(
                        icon = Icons.Rounded.BugReport,
                        iconColor = Color(0xFF6366F1),
                        iconContainerColor = Color(0xFF6366F1).copy(alpha = 0.14f),
                        title = stringResource(R.string.developer_logging_title),
                        subtitle = stringResource(R.string.developer_logging_desc),
                        checked = state.developerLoggingEnabled,
                        onCheckedChange = { DeveloperSettingsUiIntent.ToggleDeveloperLogging(it).emit() }
                    )
                    RpSettingsDivider()
                    RpSettingsSwitchTile(
                        icon = Icons.Rounded.DataObject,
                        iconColor = Color(0xFFF59E0B),
                        iconContainerColor = Color(0xFFF59E0B).copy(alpha = 0.14f),
                        title = stringResource(R.string.record_ai_raw_requests_title),
                        subtitle = stringResource(R.string.record_ai_raw_requests_desc),
                        checked = state.debugModeEnabled,
                        onCheckedChange = { DeveloperSettingsUiIntent.ToggleDebugMode(it).emit() }
                    )
                }
            }

            item {
                RpSectionHeader(title = stringResource(R.string.app_logs))
            }

            item {
                RpSettingsGroup {
                    RpSettingsTile(
                        icon = Icons.Rounded.BugReport,
                        iconColor = Color(0xFF6366F1),
                        iconContainerColor = Color(0xFF6366F1).copy(alpha = 0.14f),
                        title = stringResource(R.string.app_logs),
                        subtitle = stringResource(R.string.developer_logging_desc),
                        onClick = { DeveloperSettingsUiIntent.OpenAppLogs.emit() },
                        trailing = {
                            RpNavigationChevron()
                        }
                    )
                    RpSettingsDivider()
                    RpSettingsTile(
                        icon = Icons.Rounded.DataObject,
                        iconColor = Color(0xFFF59E0B),
                        iconContainerColor = Color(0xFFF59E0B).copy(alpha = 0.14f),
                        title = stringResource(R.string.ai_request_logs),
                        subtitle = stringResource(R.string.record_ai_raw_requests_desc),
                        onClick = { DeveloperSettingsUiIntent.OpenRequestLogs.emit() },
                        trailing = {
                            RpNavigationChevron()
                        }
                    )
                }
            }
        }
    }
}
