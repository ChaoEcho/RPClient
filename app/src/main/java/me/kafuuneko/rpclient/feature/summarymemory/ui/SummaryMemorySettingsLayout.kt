package me.kafuuneko.rpclient.feature.summarymemory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryMemorySettingsUiIntent
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryMemorySettingsUiState
import me.kafuuneko.rpclient.feature.summarymemory.presentation.SummaryProviderItem
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpCollapsibleSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpNumberSettingRow
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpGroupedTilePadding
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile

@Composable
fun SummaryMemorySettingsLayout(
    uiState: SummaryMemorySettingsUiState,
    emit: SummaryMemorySettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is SummaryMemorySettingsUiState.Normal) {
        SummaryMemorySettingsUiIntent.Back.emit()
    }

    when (uiState) {
        SummaryMemorySettingsUiState.None -> Unit
        is SummaryMemorySettingsUiState.Finished -> Unit
        is SummaryMemorySettingsUiState.Normal -> SummaryMemorySettingsNormal(uiState, emit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryMemorySettingsNormal(
    state: SummaryMemorySettingsUiState.Normal,
    emit: SummaryMemorySettingsUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.summary_memory_title),
            onBack = { SummaryMemorySettingsUiIntent.Back.emit() }
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
                    title = stringResource(R.string.summary_memory_title),
                    subtitle = stringResource(R.string.summary_memory_subtitle)
                )
            }

            // 1. 摘要模型配置
            item {
                RpCollapsibleSettingsGroup(
                    title = stringResource(R.string.summary_model_section),
                    subtitle = stringResource(R.string.summary_model_config_helper),
                    initiallyExpanded = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryProviderSelector(
                            selectedProviderId = state.selectedProviderId,
                            providers = state.providers,
                            onSelect = { SummaryMemorySettingsUiIntent.SelectProvider(it).emit() }
                        )

                        RpNumberSettingRow(
                            title = stringResource(R.string.summary_target_words),
                            value = state.wordsLimit.toString(),
                            onValueChange = { SummaryMemorySettingsUiIntent.ChangeWordsLimit(it).emit() }
                        )

                        RpNumberSettingRow(
                            title = stringResource(R.string.summary_response_tokens),
                            value = state.responseTokens.toString(),
                            onValueChange = { SummaryMemorySettingsUiIntent.ChangeResponseTokens(it).emit() }
                        )

                        RpNumberSettingRow(
                            title = stringResource(R.string.summary_max_messages_per_request),
                            value = state.maxMessagesPerRequest.toString(),
                            helper = stringResource(R.string.summary_max_messages_helper),
                            onValueChange = { SummaryMemorySettingsUiIntent.ChangeMaxMessagesPerRequest(it).emit() }
                        )
                    }
                }
            }

            // 2. 自动摘要与注入策略
            item {
                val autoSummarySummary = if (state.autoSummaryEnabled) {
                    stringResource(R.string.auto_summary_enabled_summary, state.triggerMessageCount)
                } else {
                    stringResource(R.string.auto_summary_disabled_summary)
                }

                RpCollapsibleSettingsGroup(
                    title = stringResource(R.string.auto_summary_section),
                    subtitle = stringResource(R.string.auto_summarize_desc),
                    summary = autoSummarySummary,
                    initiallyExpanded = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        RpSettingsSwitchTile(
                            icon = Icons.Rounded.AutoAwesome,
                            iconColor = Color(0xFF8B5CF6),
                            iconContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.14f),
                            title = stringResource(R.string.auto_summarize),
                            subtitle = stringResource(R.string.auto_summarize_desc),
                            checked = state.autoSummaryEnabled,
                            onCheckedChange = { SummaryMemorySettingsUiIntent.ToggleAutoSummary(it).emit() },
                            contentPadding = RpGroupedTilePadding
                        )

                        RpNumberSettingRow(
                            title = stringResource(R.string.summary_update_every_messages),
                            value = state.triggerMessageCount.toString(),
                            onValueChange = { SummaryMemorySettingsUiIntent.ChangeTriggerMessageCount(it).emit() }
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.summary_injection_position),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SummaryInjectionPosition.entries.forEach { position ->
                                    FilterChip(
                                        selected = position == state.injectionPosition,
                                        onClick = {
                                            SummaryMemorySettingsUiIntent.SelectInjectionPosition(position).emit()
                                        },
                                        label = { Text(stringResource(position.titleRes())) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        if (state.injectionPosition == SummaryInjectionPosition.InChat) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RpNumberSettingRow(
                                    title = stringResource(R.string.summary_injection_depth),
                                    value = state.injectionDepth.toString(),
                                    onValueChange = {
                                        SummaryMemorySettingsUiIntent.ChangeInjectionDepth(it).emit()
                                    }
                                )

                                Text(
                                    text = stringResource(R.string.summary_injection_role),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SummaryInjectionRole.entries.forEach { role ->
                                        FilterChip(
                                            selected = role == state.injectionRole,
                                            onClick = {
                                                SummaryMemorySettingsUiIntent.SelectInjectionRole(role).emit()
                                            },
                                            label = { Text(stringResource(role.titleRes())) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryProviderSelector(
    selectedProviderId: Long,
    providers: List<SummaryProviderItem>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedName = selectedProvider?.let {
        val name = it.name.ifBlank { stringResource(R.string.unnamed_model_config) }
        if (it.isEnabled) name else stringResource(R.string.disabled_model_config_format, name)
    } ?: stringResource(R.string.follow_global_model)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.summary_model_config)) },
            supportingText = { Text(stringResource(R.string.summary_model_config_helper)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.follow_global_model)) },
                onClick = {
                    onSelect(0L)
                    expanded = false
                }
            )
            providers.forEach { provider ->
                val name = provider.name.ifBlank { stringResource(R.string.unnamed_model_config) }
                val displayName = if (provider.isEnabled) {
                    name
                } else {
                    stringResource(R.string.disabled_model_config_format, name)
                }
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onSelect(provider.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun SummaryInjectionPosition.titleRes(): Int {
    return when (this) {
        SummaryInjectionPosition.None -> R.string.summary_position_none
        SummaryInjectionPosition.BeforeMain -> R.string.summary_position_before_main
        SummaryInjectionPosition.AfterMain -> R.string.summary_position_after_main
        SummaryInjectionPosition.InChat -> R.string.summary_position_in_chat
    }
}

private fun SummaryInjectionRole.titleRes(): Int {
    return when (this) {
        SummaryInjectionRole.System -> R.string.summary_role_system
        SummaryInjectionRole.User -> R.string.summary_role_user
        SummaryInjectionRole.Assistant -> R.string.summary_role_assistant
    }
}
