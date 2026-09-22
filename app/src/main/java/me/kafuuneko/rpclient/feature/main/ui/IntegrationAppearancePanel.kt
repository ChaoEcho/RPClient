package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.presentation.MainAppearanceSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup

/** 复用设置页密度与 Material 配色，不影响 fork 原有设置导航。 */
@Composable
internal fun IntegrationAppearancePanel(state: MainAppearanceSettingsState, emit: MainUiIntent.() -> Unit) {
    RpSettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.appearance_theme), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { MainUiIntent.SelectThemeMode(mode).emit() },
                        label = { Text(stringResource(when (mode) {
                            AppThemeMode.FollowSystem -> R.string.theme_follow_system
                            AppThemeMode.Light -> R.string.theme_light
                            AppThemeMode.Dark -> R.string.theme_dark
                        })) }
                    )
                }
            }
        }
    }
}
