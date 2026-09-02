package me.kafuuneko.rpclient.feature.promptbehavior.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiIntent
import me.kafuuneko.rpclient.feature.promptbehavior.presentation.PromptBehaviorSettingsUiState
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpCollapsibleSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile

@Composable
fun PromptBehaviorSettingsLayout(
    uiState: PromptBehaviorSettingsUiState,
    emit: PromptBehaviorSettingsUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is PromptBehaviorSettingsUiState.Normal) {
        PromptBehaviorSettingsUiIntent.Back.emit()
    }

    when (uiState) {
        PromptBehaviorSettingsUiState.None -> Unit
        is PromptBehaviorSettingsUiState.Finished -> Unit
        is PromptBehaviorSettingsUiState.Normal -> PromptBehaviorSettingsNormal(uiState, emit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptBehaviorSettingsNormal(
    state: PromptBehaviorSettingsUiState.Normal,
    emit: PromptBehaviorSettingsUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.prompt_behavior_title),
            onBack = { PromptBehaviorSettingsUiIntent.Back.emit() }
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
                    title = stringResource(R.string.prompt_behavior_title),
                    subtitle = stringResource(R.string.prompt_behavior_subtitle)
                )
            }

            // 1. 提示词后处理
            item {
                RpCollapsibleSettingsGroup(
                    title = stringResource(R.string.prompt_post_processing_title),
                    subtitle = stringResource(R.string.prompt_post_processing_desc),
                    summary = stringResource(state.postProcessingMode.titleRes()),
                    initiallyExpanded = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PromptPostProcessingMode.entries.forEach { mode ->
                            PromptPostProcessingModeRow(
                                mode = mode,
                                selected = mode == state.postProcessingMode,
                                onClick = { PromptBehaviorSettingsUiIntent.SelectPostProcessingMode(mode).emit() }
                            )
                        }
                    }
                }
            }

            // 2. 示例对话
            item {
                RpCollapsibleSettingsGroup(
                    title = stringResource(R.string.example_dialogue_title),
                    subtitle = stringResource(R.string.prompt_example_behavior_desc),
                    summary = stringResource(state.exampleDialogueBehavior.titleRes()),
                    initiallyExpanded = true
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExampleDialogueBehavior.entries.forEach { behavior ->
                            FilterChip(
                                selected = behavior == state.exampleDialogueBehavior,
                                onClick = {
                                    PromptBehaviorSettingsUiIntent.SelectExampleDialogueBehavior(behavior).emit()
                                },
                                label = { Text(stringResource(behavior.titleRes())) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // 3. 上下文行为
            item {
                RpCollapsibleSettingsGroup(
                    title = stringResource(R.string.context_behavior_title),
                    subtitle = null,
                    initiallyExpanded = true
                ) {
                    RpSettingsSwitchTile(
                        icon = Icons.Rounded.Psychology,
                        iconColor = Color(0xFF6366F1),
                        iconContainerColor = Color(0xFF6366F1).copy(alpha = 0.14f),
                        title = stringResource(R.string.prompt_include_think_context_title),
                        subtitle = stringResource(R.string.prompt_include_think_context_desc),
                        checked = state.includeThinkInContext,
                        onCheckedChange = { PromptBehaviorSettingsUiIntent.ToggleIncludeThinkInContext(it).emit() }
                    )
                    RpSettingsDivider()
                    RpSettingsSwitchTile(
                        icon = Icons.Rounded.NotificationsActive,
                        iconColor = Color(0xFFEC4899),
                        iconContainerColor = Color(0xFFEC4899).copy(alpha = 0.14f),
                        title = stringResource(R.string.context_trimming_alert),
                        subtitle = stringResource(R.string.context_trimming_alert_desc),
                        checked = state.contextTrimmingAlert,
                        onCheckedChange = { PromptBehaviorSettingsUiIntent.ToggleContextTrimmingAlert(it).emit() }
                    )
                }
            }

            // 4. 响应行为
            item {
                RpCollapsibleSettingsGroup(
                    title = stringResource(R.string.response_behavior_title),
                    subtitle = null,
                    summary = if (state.streamEnabled) "Streaming" else null,
                    initiallyExpanded = true
                ) {
                    RpSettingsSwitchTile(
                        icon = Icons.Rounded.Stream,
                        iconColor = Color(0xFF0EA5E9),
                        iconContainerColor = Color(0xFF0EA5E9).copy(alpha = 0.14f),
                        title = stringResource(R.string.streaming_response),
                        subtitle = stringResource(R.string.streaming_response_desc),
                        checked = state.streamEnabled,
                        onCheckedChange = { PromptBehaviorSettingsUiIntent.ToggleStreamEnabled(it).emit() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptPostProcessingModeRow(
    mode: PromptPostProcessingMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
            }
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal fun PromptPostProcessingMode.titleRes(): Int {
    return when (this) {
        PromptPostProcessingMode.None -> R.string.prompt_post_processing_none
        PromptPostProcessingMode.Merge -> R.string.prompt_post_processing_merge
        PromptPostProcessingMode.SemiStrict -> R.string.prompt_post_processing_semi_strict
        PromptPostProcessingMode.Strict -> R.string.prompt_post_processing_strict
        PromptPostProcessingMode.SingleUserMessage -> R.string.prompt_post_processing_single_user
    }
}

private fun PromptPostProcessingMode.descriptionRes(): Int {
    return when (this) {
        PromptPostProcessingMode.None -> R.string.prompt_post_processing_none_desc
        PromptPostProcessingMode.Merge -> R.string.prompt_post_processing_merge_desc
        PromptPostProcessingMode.SemiStrict -> R.string.prompt_post_processing_semi_strict_desc
        PromptPostProcessingMode.Strict -> R.string.prompt_post_processing_strict_desc
        PromptPostProcessingMode.SingleUserMessage -> R.string.prompt_post_processing_single_user_desc
    }
}

internal fun ExampleDialogueBehavior.titleRes(): Int {
    return when (this) {
        ExampleDialogueBehavior.Normal -> R.string.prompt_example_behavior_normal
        ExampleDialogueBehavior.Pinned -> R.string.prompt_example_behavior_pinned
        ExampleDialogueBehavior.Disabled -> R.string.prompt_example_behavior_disabled
    }
}
