package me.kafuuneko.rpclient.feature.worldbookentryedit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditDialogState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditLoadState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditMode
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiState
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpChipInputField
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpJsonCodeEditorField
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel as Panel
import me.kafuuneko.rpclient.ui.widgets.RpPercentageSlider
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.utils.rememberJsonSyntaxVisualTransformation
import me.kafuuneko.rpclient.utils.rememberPromptMacroVisualTransformation

/** 世界书条目完整兼容字段编辑页 Compose 入口。 */
@Composable
fun WorldBookEntryEditLayout(
    uiState: WorldBookEntryEditUiState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is WorldBookEntryEditUiState.Normal) { WorldBookEntryEditUiIntent.Back.emit() }
    when (uiState) {
        WorldBookEntryEditUiState.None -> Unit
        is WorldBookEntryEditUiState.Finished -> WorldBookEntryEditLayout(uiState.previous) {}
        is WorldBookEntryEditUiState.Normal -> {
            WorldBookEntryEditNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun WorldBookEntryEditNormal(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = if (state.mode == WorldBookEntryEditMode.Create) stringResource(R.string.create_world_book_entry) else stringResource(R.string.edit_world_book_entry),
            onBack = { WorldBookEntryEditUiIntent.Back.emit() },
            actions = { TopBarSaveButton(state, emit) }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = state.form.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    subtitle = stringResource(R.string.world_book_entry_editor_subtitle)
                )
            }
            if (state.loadState == WorldBookEntryEditLoadState.Loading) {
                item { LoadingPanel() }
            } else {
                item { BasicPanel(state.form, state.loadState, emit) }
                item { ContentPanel(state.form, emit) }
                item { KeywordsPanel(state.form, emit) }
                item { AdvancedPanel(state.form, state.loadState, emit) }
                item { ActionPanel(state, emit) }
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading))
        }
    }
}

@Composable
private fun BasicPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(Icons.Rounded.Description)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = form.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.entry_order_depth, form.order.toIntOrNull() ?: 0, form.depth.toIntOrNull() ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            IconButton(
                enabled = loadState == WorldBookEntryEditLoadState.None,
                onClick = { WorldBookEntryEditUiIntent.DeleteEntryClick.emit() }
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
            }
        }

        FormTextField(
            label = stringResource(R.string.entry_name),
            value = form.name,
            onChange = { WorldBookEntryEditUiIntent.ChangeName(it).emit() }
        )

        RpSettingsGroup {
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_constant),
                subtitle = stringResource(R.string.entry_constant_desc),
                checked = form.constant,
                enabled = loadState == WorldBookEntryEditLoadState.None,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeConstant(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_disabled),
                subtitle = stringResource(R.string.entry_disabled_desc),
                checked = form.disabled,
                enabled = loadState == WorldBookEntryEditLoadState.None,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeDisabled(it).emit() }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_order),
                value = form.order,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeOrder(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_depth),
                value = form.depth,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeDepth(it).emit() }
            )
        }
    }
}

@Composable
private fun ContentPanel(
    form: WorldBookEntryEditForm,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.entry_content))
        FormTextField(
            label = stringResource(R.string.entry_content),
            value = form.content,
            minLines = 6,
            onChange = { WorldBookEntryEditUiIntent.ChangeContent(it).emit() }
        )
    }
}

@Composable
private fun KeywordsPanel(
    form: WorldBookEntryEditForm,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RpChipInputField(
            title = stringResource(R.string.primary_keywords),
            chips = form.keywords,
            icon = Icons.Rounded.Tag,
            onChipsChanged = { WorldBookEntryEditUiIntent.SetKeywords(it).emit() },
            addLabel = stringResource(R.string.add_keyword),
            placeholder = stringResource(R.string.keyword_input_placeholder),
            editDialogTitle = stringResource(R.string.edit_keyword_title)
        )

        RpChipInputField(
            title = stringResource(R.string.secondary_keywords),
            chips = form.secondaryKeywords,
            icon = Icons.Rounded.FilterList,
            onChipsChanged = { WorldBookEntryEditUiIntent.SetSecondaryKeywords(it).emit() },
            addLabel = stringResource(R.string.add_keyword),
            placeholder = stringResource(R.string.keyword_input_placeholder),
            editDialogTitle = stringResource(R.string.edit_keyword_title),
            accentColor = MaterialTheme.colorScheme.secondary
        )

        RpChipInputField(
            title = stringResource(R.string.categories),
            chips = form.category,
            icon = Icons.AutoMirrored.Rounded.Label,
            onChipsChanged = { WorldBookEntryEditUiIntent.SetCategories(it).emit() },
            addLabel = stringResource(R.string.add_category),
            placeholder = stringResource(R.string.category_input_placeholder),
            editDialogTitle = stringResource(R.string.edit_category_title),
            accentColor = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun AdvancedPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    val enabled = loadState == WorldBookEntryEditLoadState.None

    Panel {
        RpSectionHeader(title = stringResource(R.string.advanced_definition))

        // 插入位置与自定义出口
        PositionSelector(
            positionValue = form.position,
            enabled = enabled,
            onPositionSelected = { WorldBookEntryEditUiIntent.ChangePosition(it).emit() }
        )

        if (form.position.trim() == LorebookEntry.POSITION_OUTLET.toString()) {
            FormTextField(
                label = stringResource(R.string.entry_outlet),
                value = form.outletName,
                onChange = { WorldBookEntryEditUiIntent.ChangeOutletName(it).emit() }
            )
        }

        // 注入消息角色
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.entry_role),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            RoleSelector(
                roleValue = form.role,
                enabled = enabled,
                onRoleSelected = { WorldBookEntryEditUiIntent.ChangeRole(it).emit() }
            )
        }

        // 次要关键词判定逻辑
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.entry_logic),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SelectiveLogicSelector(
                logicValue = form.selectiveLogic,
                enabled = enabled,
                onLogicSelected = { WorldBookEntryEditUiIntent.ChangeSelectiveLogic(it).emit() }
            )
        }

        // 触发概率滑块
        RpPercentageSlider(
            title = stringResource(R.string.entry_probability),
            value = form.probability.toIntOrNull() ?: 100,
            helper = stringResource(R.string.entry_probability_helper),
            enabled = enabled,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeProbability(it.toString()).emit() }
        )

        // 扫描深度与粘滞/冷却/延迟
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_scan_depth),
                value = form.scanDepth,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeScanDepth(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_sticky),
                value = form.sticky,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeSticky(it).emit() }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_cooldown),
                value = form.cooldown,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeCooldown(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_delay),
                value = form.delay,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeDelay(it).emit() }
            )
        }

        // 扫描匹配源范围
        RpSectionHeader(title = stringResource(R.string.entry_matching_scope))
        RpSettingsGroup {
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_match_description),
                checked = form.matchCharacterDescription,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeMatchCharacterDescription(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_match_personality),
                checked = form.matchCharacterPersonality,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeMatchCharacterPersonality(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_match_character_note),
                checked = form.matchCharacterDepthPrompt,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeMatchCharacterDepthPrompt(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_match_scenario),
                checked = form.matchScenario,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeMatchScenario(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_match_persona_description),
                checked = form.matchPersonaDescription,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeMatchPersonaDescription(it).emit() }
            )
        }

        // 时序与递归控制
        RpSectionHeader(title = stringResource(R.string.entry_timing_recursion))
        RpSettingsGroup {
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_whole_words),
                checked = form.matchWholeWords == true,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeMatchWholeWords(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_case_sensitive),
                checked = form.caseSensitive == true,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeCaseSensitive(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_ignore_budget),
                checked = form.ignoreBudget,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeIgnoreBudget(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_prevent_recursion),
                checked = form.preventRecursion,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangePreventRecursion(it).emit() }
            )
            RpSettingsDivider(startIndent = false)
            RpSettingsSwitchTile(
                title = stringResource(R.string.entry_delay_until_recursion),
                checked = form.delayUntilRecursion,
                enabled = enabled,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeDelayUntilRecursion(it).emit() }
            )
        }

        RpJsonCodeEditorField(
            label = stringResource(R.string.extensions_json),
            value = form.extensionsJson,
            enabled = enabled,
            minHeight = 120.dp,
            maxHeight = 220.dp,
            onValueChange = { WorldBookEntryEditUiIntent.ChangeExtensionsJson(it).emit() }
        )
    }
}

@Composable
private fun RoleSelector(
    roleValue: String,
    enabled: Boolean,
    onRoleSelected: (String) -> Unit
) {
    val currentRole = roleValue.trim().toIntOrNull() ?: LorebookEntry.ROLE_SYSTEM
    val roles = listOf(
        LorebookEntry.ROLE_SYSTEM to stringResource(R.string.role_system),
        LorebookEntry.ROLE_USER to stringResource(R.string.role_user),
        LorebookEntry.ROLE_ASSISTANT to stringResource(R.string.role_assistant)
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        roles.forEach { (roleId, label) ->
            FilterChip(
                selected = currentRole == roleId,
                onClick = { if (enabled) onRoleSelected(roleId.toString()) },
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                leadingIcon = if (currentRole == roleId) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

@Composable
private fun SelectiveLogicSelector(
    logicValue: String,
    enabled: Boolean,
    onLogicSelected: (String) -> Unit
) {
    val currentLogic = logicValue.trim().toIntOrNull() ?: LorebookEntry.LOGIC_AND_ANY
    val logicOptions = listOf(
        LorebookEntry.LOGIC_AND_ANY to stringResource(R.string.logic_and_any),
        LorebookEntry.LOGIC_AND_ALL to stringResource(R.string.logic_and_all),
        LorebookEntry.LOGIC_NOT_ANY to stringResource(R.string.logic_not_any),
        LorebookEntry.LOGIC_NOT_ALL to stringResource(R.string.logic_not_all)
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            logicOptions.forEach { (logicId, label) ->
                FilterChip(
                    selected = currentLogic == logicId,
                    onClick = { if (enabled) onLogicSelected(logicId.toString()) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = if (currentLogic == logicId) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Text(
            text = when (currentLogic) {
                LorebookEntry.LOGIC_AND_ALL -> stringResource(R.string.logic_and_all_helper)
                LorebookEntry.LOGIC_NOT_ANY -> stringResource(R.string.logic_not_any_helper)
                LorebookEntry.LOGIC_NOT_ALL -> stringResource(R.string.logic_not_all_helper)
                else -> stringResource(R.string.logic_and_any_helper)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionSelector(
    positionValue: String,
    enabled: Boolean,
    onPositionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentPosition = positionValue.trim().toIntOrNull() ?: LorebookEntry.POSITION_AT_DEPTH

    val positions = listOf(
        LorebookEntry.POSITION_AT_DEPTH to stringResource(R.string.position_at_depth),
        LorebookEntry.POSITION_BEFORE to stringResource(R.string.position_before_char),
        LorebookEntry.POSITION_AFTER to stringResource(R.string.position_after_char),
        LorebookEntry.POSITION_AN_TOP to stringResource(R.string.position_an_top),
        LorebookEntry.POSITION_AN_BOTTOM to stringResource(R.string.position_an_bottom),
        LorebookEntry.POSITION_EXAMPLE_TOP to stringResource(R.string.position_example_top),
        LorebookEntry.POSITION_EXAMPLE_BOTTOM to stringResource(R.string.position_example_bottom),
        LorebookEntry.POSITION_OUTLET to stringResource(R.string.position_outlet)
    )

    val currentLabel = positions.firstOrNull { it.first == currentPosition }?.second
        ?: stringResource(R.string.position_at_depth)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.entry_position)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            positions.forEach { (posId, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onPositionSelected(posId.toString())
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionPanel(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = state.loadState == WorldBookEntryEditLoadState.None,
            onClick = { WorldBookEntryEditUiIntent.Back.emit() }
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = state.loadState == WorldBookEntryEditLoadState.None,
            onClick = { WorldBookEntryEditUiIntent.SaveEntry.emit() }
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(
                when {
                    state.loadState == WorldBookEntryEditLoadState.Saving -> stringResource(R.string.saving)
                    state.mode == WorldBookEntryEditMode.Create -> stringResource(R.string.create)
                    else -> stringResource(R.string.save)
                }
            )
        }
    }
}

@Composable
private fun TopBarSaveButton(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = state.loadState == WorldBookEntryEditLoadState.None,
        onClick = { WorldBookEntryEditUiIntent.SaveEntry.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            when {
                state.loadState == WorldBookEntryEditLoadState.Saving -> stringResource(R.string.saving)
                state.mode == WorldBookEntryEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun DialogSwitch(
    dialogState: WorldBookEntryEditDialogState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    when (dialogState) {
        WorldBookEntryEditDialogState.None -> Unit
        is WorldBookEntryEditDialogState.DeleteConfirm -> AppDangerDialog(
            onDismissRequest = { WorldBookEntryEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_world_book_entry_title),
            message = stringResource(R.string.delete_world_book_entry_message, dialogState.entryName),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { WorldBookEntryEditUiIntent.ConfirmDeleteEntry.emit() }
        )
        WorldBookEntryEditDialogState.UnsavedChangesConfirm -> AppDangerDialog(
            onDismissRequest = { WorldBookEntryEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { WorldBookEntryEditUiIntent.ConfirmDiscardChanges.emit() }
        )
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = minLines == 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = rememberPromptMacroVisualTransformation(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        minLines = minLines,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp)
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WorldBookEntryEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        WorldBookEntryEditLayout(
            uiState = WorldBookEntryEditUiState.Normal(
                mode = WorldBookEntryEditMode.Edit,
                form = WorldBookEntryEditForm(
                    id = 1L,
                    lorebookId = 1L,
                    name = "Old District",
                    keywords = listOf("district", "railway"),
                    secondaryKeywords = listOf("archive"),
                    category = listOf("location"),
                    content = "The old district is divided by three elevated railways."
                )
            ),
            emit = {}
        )
    }
}
