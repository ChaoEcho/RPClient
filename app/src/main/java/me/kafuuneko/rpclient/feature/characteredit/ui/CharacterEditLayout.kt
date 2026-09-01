package me.kafuuneko.rpclient.feature.characteredit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterLorebookItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterProviderItem
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditDialogState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditLoadState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditMode
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterPromptField
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.dialog.AppCodeEditorDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppPromptEditorDialog
import me.kafuuneko.rpclient.ui.dialog.AppWarningDialog
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.CharacterAccentColors
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.DEFAULT_PROMPT_MACROS
import me.kafuuneko.rpclient.ui.widgets.DIALOGUE_EXAMPLE_MACROS
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpChipInputField
import me.kafuuneko.rpclient.ui.widgets.RpJsonCodeEditorField
import me.kafuuneko.rpclient.ui.widgets.RpMacroActionBar
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel as Panel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.utils.rememberPromptMacroVisualTransformation

private enum class CharacterEditTab {
    Profile,
    Dialogue,
    Advanced
}

/** 角色创建与编辑页 Compose 入口，仅渲染状态并发送编辑意图。 */
@Composable
fun CharacterEditLayout(
    uiState: CharacterEditUiState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is CharacterEditUiState.Normal) { CharacterEditUiIntent.Back.emit() }
    when (uiState) {
        CharacterEditUiState.None -> Unit
        is CharacterEditUiState.Finished -> CharacterEditLayout(uiState.previous) {}
        is CharacterEditUiState.Normal -> {
            CharacterEditNormal(uiState, emit)
            DialogSwitch(uiState, emit)
        }
    }
}

@Composable
private fun CharacterEditNormal(
    state: CharacterEditUiState.Normal,
    emit: CharacterEditUiIntent.() -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(CharacterEditTab.Profile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = if (state.mode == CharacterEditMode.Create) {
                stringResource(R.string.create_character)
            } else {
                stringResource(R.string.edit_character_title)
            },
            onBack = { CharacterEditUiIntent.Back.emit() },
            actions = {
                if (state.mode == CharacterEditMode.Edit) {
                    TopBarUpdateButton(state.loadState, emit)
                }
                TopBarSaveButton(state.mode, state.loadState, emit)
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = state.form.name.ifBlank {
                        if (state.mode == CharacterEditMode.Create) {
                            stringResource(R.string.create_character)
                        } else {
                            stringResource(R.string.character)
                        }
                    },
                    subtitle = stringResource(R.string.character_editor_subtitle)
                )
            }
            if (state.loadState == CharacterEditLoadState.Loading) {
                item { LoadingPanel() }
            } else {
                item {
                    HeroHeaderPanel(
                        form = state.form,
                        avatarImage = state.avatarImage,
                        isAvatarGenerating = state.isAvatarGenerating,
                        availableLorebooks = state.availableLorebooks,
                        loadState = state.loadState,
                        emit = emit
                    )
                }
                item {
                    CharacterEditTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                when (selectedTab) {
                    CharacterEditTab.Profile -> {
                        item { ProfileBasicPanel(state.form, emit) }
                        item { TagsPanel(state.form.tags, emit) }
                    }
                    CharacterEditTab.Dialogue -> {
                        item {
                            DialogueDefinitionPanel(
                                form = state.form,
                                emit = emit
                            )
                        }
                        item {
                            FirstMessagesPanel(
                                firstMessages = state.form.firstMessages,
                                emit = emit
                            )
                        }
                        item {
                            DialogueExamplesPanel(
                                form = state.form,
                                emit = emit
                            )
                        }
                    }
                    CharacterEditTab.Advanced -> {
                        item {
                            AdvancedPanel(
                                form = state.form,
                                availableLorebooks = state.availableLorebooks,
                                availableProviders = state.availableProviders,
                                emit = emit
                            )
                        }
                    }
                }

                item { ActionPanel(state.mode, state.loadState, emit) }
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
private fun HeroHeaderPanel(
    form: CharacterEditForm,
    avatarImage: ImageBitmap?,
    isAvatarGenerating: Boolean,
    availableLorebooks: List<CharacterLorebookItem>,
    loadState: CharacterEditLoadState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    val boundLorebookName = remember(form.characterLorebookId, availableLorebooks) {
        availableLorebooks.firstOrNull { it.id == form.characterLorebookId }?.name
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )

            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
                onClick = { CharacterEditUiIntent.DeleteCharacterClick.emit() }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 18.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clickable(enabled = loadState == CharacterEditLoadState.None) {
                            CharacterEditUiIntent.PickAvatarClick.emit()
                        }
                ) {
                    AvatarPreview(
                        avatarText = form.avatarText(),
                        avatarColor = form.avatarColor(),
                        image = avatarImage,
                        size = 92
                    )
                    if (isAvatarGenerating) {
                        Surface(
                            modifier = Modifier.matchParentSize(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(30.dp))
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.change_avatar),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = form.name.ifBlank { stringResource(R.string.character) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    if (form.characterVersion.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "v${form.characterVersion}",
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .widthIn(max = 140.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (boundLorebookName != null)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (boundLorebookName != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (boundLorebookName != null)
                                    stringResource(R.string.bound_to_lorebook, boundLorebookName)
                                else
                                    stringResource(R.string.unbound_lorebook),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (boundLorebookName != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterEditTabBar(
    selectedTab: CharacterEditTab,
    onTabSelected: (CharacterEditTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                CharacterEditTab.Profile to stringResource(R.string.tab_character_profile),
                CharacterEditTab.Dialogue to stringResource(R.string.tab_character_dialogue),
                CharacterEditTab.Advanced to stringResource(R.string.tab_character_advanced)
            )
            tabs.forEach { (tab, title) ->
                val selected = selectedTab == tab
                Surface(
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileBasicPanel(
    form: CharacterEditForm,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.basic_info))
        FormTextField(stringResource(R.string.name), form.name) {
            CharacterEditUiIntent.ChangeName(it).emit()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.character_creator),
                value = form.creator,
                modifier = Modifier.weight(1f),
                onChange = { CharacterEditUiIntent.ChangeCreator(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.character_version),
                value = form.characterVersion,
                modifier = Modifier.weight(1f),
                onChange = { CharacterEditUiIntent.ChangeCharacterVersion(it).emit() }
            )
        }
        FormTextField(
            label = stringResource(R.string.character_description),
            value = form.description,
            minLines = 3,
            showMacroBar = true,
            onExpandFullscreen = {
                CharacterEditUiIntent.ShowPromptEditor(CharacterPromptField.Description).emit()
            },
            onChange = { CharacterEditUiIntent.ChangeDescription(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_creator_notes),
            value = form.creatorNotes,
            minLines = 3,
            onChange = { CharacterEditUiIntent.ChangeCreatorNotes(it).emit() }
        )
    }
}

@Composable
private fun TagsPanel(
    tags: List<String>,
    emit: CharacterEditUiIntent.() -> Unit
) {
    RpChipInputField(
        title = stringResource(R.string.character_tags),
        chips = tags,
        onChipsChanged = { CharacterEditUiIntent.SetTags(it).emit() },
        addLabel = stringResource(R.string.add_tag),
        placeholder = stringResource(R.string.tag_input_placeholder),
        editDialogTitle = stringResource(R.string.edit_tag_title)
    )
}

@Composable
private fun DialogueDefinitionPanel(
    form: CharacterEditForm,
    emit: CharacterEditUiIntent.() -> Unit
) {
    val personalityLabel = stringResource(R.string.character_personality)
    val scenarioLabel = stringResource(R.string.character_scenario)

    Panel {
        RpSectionHeader(title = stringResource(R.string.character_definition))
        FormTextField(
            label = personalityLabel,
            value = form.personality,
            minLines = 4,
            showMacroBar = true,
            onExpandFullscreen = {
                CharacterEditUiIntent.ShowPromptEditor(CharacterPromptField.Personality).emit()
            },
            onChange = { CharacterEditUiIntent.ChangePersonality(it).emit() }
        )
        FormTextField(
            label = scenarioLabel,
            value = form.scenario,
            minLines = 4,
            showMacroBar = true,
            onExpandFullscreen = {
                CharacterEditUiIntent.ShowPromptEditor(CharacterPromptField.Scenario).emit()
            },
            onChange = { CharacterEditUiIntent.ChangeScenario(it).emit() }
        )
    }
}

@Composable
private fun FirstMessagesPanel(
    firstMessages: List<String>,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(
            title = stringResource(R.string.character_first_messages),
            action = stringResource(R.string.add),
            onAction = { CharacterEditUiIntent.AddFirstMessage.emit() }
        )
        firstMessages.forEachIndexed { index, message ->
            val label = stringResource(R.string.character_first_message_index, index + 1)
            ListTextField(
                label = label,
                value = message,
                minLines = 3,
                showMacroBar = true,
                onExpandFullscreen = {
                    CharacterEditUiIntent.ShowPromptEditor(
                        CharacterPromptField.FirstMessage(index)
                    ).emit()
                },
                onValueChange = { CharacterEditUiIntent.ChangeFirstMessage(index, it).emit() },
                onDelete = { CharacterEditUiIntent.DeleteFirstMessage(index).emit() }
            )
        }
    }
}

@Composable
private fun DialogueExamplesPanel(
    form: CharacterEditForm,
    emit: CharacterEditUiIntent.() -> Unit
) {
    val dialogueLabel = stringResource(R.string.character_examples_of_dialogue)

    Panel {
        RpSectionHeader(title = stringResource(R.string.character_dialogue))
        FormTextField(
            label = dialogueLabel,
            value = form.examplesOfDialogue,
            minLines = 5,
            showMacroBar = true,
            macros = DIALOGUE_EXAMPLE_MACROS,
            onExpandFullscreen = {
                CharacterEditUiIntent.ShowPromptEditor(CharacterPromptField.DialogueExamples).emit()
            },
            onChange = { CharacterEditUiIntent.ChangeExamplesOfDialogue(it).emit() }
        )
    }
}

@Composable
private fun AdvancedPanel(
    form: CharacterEditForm,
    availableLorebooks: List<CharacterLorebookItem>,
    availableProviders: List<CharacterProviderItem>,
    emit: CharacterEditUiIntent.() -> Unit
) {
    var isExtensionsExpanded by rememberSaveable(form.id) { mutableStateOf(false) }

    val systemPromptLabel = stringResource(R.string.character_main_prompt_override)
    val postHistoryLabel = stringResource(R.string.character_post_history_instructions)
    val noteLabel = stringResource(R.string.character_note)

    Panel {
        RpSectionHeader(title = stringResource(R.string.advanced_definition))
        CharacterProviderSelector(
            selectedId = form.llmProviderId,
            availableProviders = availableProviders,
            onSelect = { CharacterEditUiIntent.SelectLLMProvider(it).emit() }
        )
        LorebookSelector(
            selectedId = form.characterLorebookId,
            availableLorebooks = availableLorebooks,
            onSelect = { CharacterEditUiIntent.UpdateCharacterLorebook(it).emit() },
            onManage = { CharacterEditUiIntent.OpenWorldBookManager.emit() }
        )
        FormTextField(
            label = systemPromptLabel,
            value = form.systemPrompt,
            minLines = 4,
            maxLines = 8,
            showMacroBar = true,
            onExpandFullscreen = {
                CharacterEditUiIntent.ShowPromptEditor(CharacterPromptField.SystemPrompt).emit()
            },
            onChange = { CharacterEditUiIntent.ChangeSystemPrompt(it).emit() }
        )
        FormTextField(
            label = postHistoryLabel,
            value = form.postHistoryInstructions,
            minLines = 4,
            maxLines = 8,
            showMacroBar = true,
            onExpandFullscreen = {
                CharacterEditUiIntent.ShowPromptEditor(
                    CharacterPromptField.PostHistoryInstructions
                ).emit()
            },
            onChange = { CharacterEditUiIntent.ChangePostHistoryInstructions(it).emit() }
        )
        FormTextField(
            label = noteLabel,
            value = form.depthPromptPrompt,
            minLines = 4,
            maxLines = 8,
            showMacroBar = true,
            onExpandFullscreen = {
                CharacterEditUiIntent.ShowPromptEditor(CharacterPromptField.DepthPrompt).emit()
            },
            onChange = { CharacterEditUiIntent.ChangeDepthPromptPrompt(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_note_depth),
            value = form.depthPromptDepth,
            keyboardType = KeyboardType.Number,
            onChange = { CharacterEditUiIntent.ChangeDepthPromptDepth(it).emit() }
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.entry_role),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            CharacterNoteRoleSelector(
                selectedRole = form.depthPromptRole,
                onRoleSelected = { CharacterEditUiIntent.ChangeDepthPromptRole(it).emit() }
            )
        }
        RpSectionHeader(
            title = stringResource(R.string.character_alternate_greetings),
            action = stringResource(R.string.add),
            onAction = { CharacterEditUiIntent.AddAlternateGreeting.emit() }
        )
        form.alternateGreetings.forEachIndexed { index, greeting ->
            val label = stringResource(R.string.character_alternate_greeting_index, index + 1)
            ListTextField(
                label = label,
                value = greeting,
                minLines = 3,
                maxLines = 6,
                showMacroBar = true,
                onExpandFullscreen = {
                    CharacterEditUiIntent.ShowPromptEditor(
                        CharacterPromptField.AlternateGreeting(index)
                    ).emit()
                },
                onValueChange = { CharacterEditUiIntent.ChangeAlternateGreeting(index, it).emit() },
                onDelete = { CharacterEditUiIntent.DeleteAlternateGreeting(index).emit() }
            )
        }
        RawExtensionsPanel(
            value = form.extensionsJson,
            expanded = isExtensionsExpanded,
            onExpandedChange = { isExtensionsExpanded = it },
            onChange = { CharacterEditUiIntent.ChangeExtensionsJson(it).emit() }
        )
    }
}

@Composable
private fun RawExtensionsPanel(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.extensions_json), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.extensions_json_size, value.length),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                }
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                        contentDescription = null
                    )
                    Text(if (expanded) stringResource(R.string.hide) else stringResource(R.string.edit))
                }
            }
            if (expanded) {
                RpJsonCodeEditorField(
                    value = value,
                    onValueChange = onChange,
                    minHeight = 130.dp,
                    maxHeight = 240.dp
                )
            } else {
                Text(
                    text = value.ifBlank { "{}" }.compactPreview(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionPanel(
    mode: CharacterEditMode,
    loadState: CharacterEditLoadState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
            onClick = { CharacterEditUiIntent.Back.emit() }
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
            onClick = { CharacterEditUiIntent.SaveCharacter.emit() }
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(
                when {
                    loadState == CharacterEditLoadState.Saving -> stringResource(R.string.saving)
                    mode == CharacterEditMode.Create -> stringResource(R.string.create)
                    else -> stringResource(R.string.save)
                }
            )
        }
    }
}

@Composable
private fun TopBarSaveButton(
    mode: CharacterEditMode,
    loadState: CharacterEditLoadState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
        onClick = { CharacterEditUiIntent.SaveCharacter.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            when {
                loadState == CharacterEditLoadState.Saving -> stringResource(R.string.saving)
                mode == CharacterEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun TopBarUpdateButton(
    loadState: CharacterEditLoadState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    IconButton(
        enabled = loadState == CharacterEditLoadState.None,
        onClick = { CharacterEditUiIntent.UpdateCharacterClick.emit() }
    ) {
        Icon(
            Icons.Rounded.Sync,
            contentDescription = stringResource(R.string.character_update_from_card)
        )
    }
}

@Composable
private fun DialogSwitch(
    state: CharacterEditUiState.Normal,
    emit: CharacterEditUiIntent.() -> Unit
) {
    val dialogState = state.dialogState
    when (dialogState) {
        CharacterEditDialogState.None -> Unit
        CharacterEditDialogState.AvatarActions -> {
            AppActionListDialog(
                onDismissRequest = { CharacterEditUiIntent.DismissActionDialog.emit() },
                title = stringResource(R.string.choose_avatar),
                badgeIcon = Icons.Rounded.ImageIcon,
                actions = buildList {
                    add(
                        AppActionItem(
                            icon = Icons.Rounded.ImageIcon,
                            title = stringResource(R.string.choose_from_gallery),
                            enabled = !state.isAvatarGenerating,
                            onClick = { CharacterEditUiIntent.ChooseAvatarFromAlbum.emit() }
                        )
                    )
                    add(
                        AppActionItem(
                            icon = Icons.Rounded.AutoAwesome,
                            title = stringResource(R.string.generate_avatar_with_ai),
                            enabled = !state.isAvatarGenerating,
                            onClick = { CharacterEditUiIntent.GenerateAvatar.emit() }
                        )
                    )
                    if (state.form.avatar.isNotBlank()) {
                        add(
                            AppActionItem(
                                icon = Icons.Rounded.RestartAlt,
                                title = stringResource(R.string.restore_default_avatar),
                                enabled = !state.isAvatarGenerating,
                                onClick = { CharacterEditUiIntent.RestoreDefaultAvatar.emit() }
                            )
                        )
                    }
                }
            )
        }
        CharacterEditDialogState.UpdateSource -> AppActionListDialog(
            onDismissRequest = { CharacterEditUiIntent.DismissActionDialog.emit() },
            title = stringResource(R.string.update_character),
            subtitle = stringResource(R.string.choose_character_card_source),
            badgeIcon = Icons.Rounded.Sync,
            actions = listOf(
                AppActionItem(
                    icon = Icons.Rounded.ContentPaste,
                    title = stringResource(R.string.paste_character_card_json),
                    subtitle = stringResource(R.string.paste_character_card_json_description),
                    onClick = { CharacterEditUiIntent.PasteUpdateJsonClick.emit() }
                ),
                AppActionItem(
                    icon = Icons.Rounded.FileUpload,
                    title = stringResource(R.string.choose_character_card_file),
                    subtitle = stringResource(R.string.choose_character_card_file_description),
                    onClick = { CharacterEditUiIntent.PickUpdateJsonFileClick.emit() }
                )
            )
        )
        is CharacterEditDialogState.UpdateJsonEditor -> AppCodeEditorDialog(
            onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.update_json_editor_title),
            value = dialogState.draftText,
            onValueChange = { CharacterEditUiIntent.ChangeUpdateJsonDraft(it).emit() },
            confirmEnabled = dialogState.draftText.isNotBlank(),
            onConfirm = { CharacterEditUiIntent.ConfirmUpdateJson.emit() }
        )
        is CharacterEditDialogState.LowEmbeddedLorebookBudgetConfirm -> AppWarningDialog(
            onDismissRequest = {
                CharacterEditUiIntent.UpdateCharacterWithOriginalLorebookBudget.emit()
            },
            title = stringResource(R.string.low_world_book_budget_title),
            message = stringResource(
                R.string.low_world_book_budget_message,
                dialogState.importedTokenBudget
            ),
            confirmText = stringResource(R.string.follow_global_budget),
            dismissText = stringResource(R.string.keep_imported_budget),
            onConfirm = {
                CharacterEditUiIntent.UpdateCharacterWithGlobalLorebookBudget.emit()
            }
        )
        is CharacterEditDialogState.ConfirmCharacterUpdate -> {
            val worldbookMessage = stringResource(
                if (dialogState.hasEmbeddedLorebook) {
                    R.string.update_character_worldbook_added
                } else {
                    R.string.update_character_worldbook_detached
                }
            )
            val updateMessage = stringResource(
                R.string.update_character_confirm_message,
                dialogState.currentName,
                dialogState.importedName,
                worldbookMessage
            )
            AppWarningDialog(
                onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
                title = stringResource(R.string.confirm_character_update),
                message = if (dialogState.willDiscardUnsavedChanges) {
                    updateMessage + "\n\n" + stringResource(R.string.confirm_character_update_unsaved_message)
                } else {
                    updateMessage
                },
                confirmText = stringResource(android.R.string.ok),
                dismissText = stringResource(android.R.string.cancel),
                onConfirm = { CharacterEditUiIntent.ConfirmCharacterUpdate.emit() }
            )
        }
        is CharacterEditDialogState.DeleteConfirm -> AppDangerDialog(
            onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_character_title),
            message = stringResource(R.string.delete_character_message, dialogState.characterName),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { CharacterEditUiIntent.ConfirmDeleteCharacter.emit() }
        )
        is CharacterEditDialogState.DeleteWithLorebookConfirm -> AppDialogScaffold(
            onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_character_with_world_book_title),
            subtitle = stringResource(
                R.string.delete_character_with_world_book_message,
                dialogState.characterName,
                dialogState.lorebookName
            ),
            badgeIcon = Icons.Rounded.Delete,
            badgeTone = DialogBadgeTone.Danger,
            confirmText = stringResource(R.string.delete_character_and_world_book),
            dismissText = stringResource(R.string.delete_character_only),
            confirmIsDestructive = true,
            onConfirm = { CharacterEditUiIntent.ConfirmDeleteCharacterWithLorebook.emit() },
            onDismiss = { CharacterEditUiIntent.ConfirmDeleteCharacterOnly.emit() }
        )
        is CharacterEditDialogState.PromptEditor -> AppPromptEditorDialog(
            onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
            title = dialogState.field.editorTitle(),
            value = dialogState.draftText,
            availableMacros = dialogState.field.availableMacros(),
            onValueChange = { CharacterEditUiIntent.ChangePromptEditorDraft(it).emit() },
            onCopyRequest = { CharacterEditUiIntent.CopyPromptEditorText.emit() },
            onConfirm = { CharacterEditUiIntent.ConfirmPromptEditor.emit() }
        )
        CharacterEditDialogState.UnsavedChangesConfirm -> AppDangerDialog(
            onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { CharacterEditUiIntent.ConfirmDiscardChanges.emit() }
        )
    }
}

@Composable
private fun CharacterPromptField.editorTitle(): String {
    return when (this) {
        CharacterPromptField.Description -> stringResource(R.string.character_description)
        CharacterPromptField.Personality -> stringResource(R.string.character_personality)
        CharacterPromptField.Scenario -> stringResource(R.string.character_scenario)
        is CharacterPromptField.FirstMessage -> stringResource(
            R.string.character_first_message_index,
            index + 1
        )
        CharacterPromptField.DialogueExamples -> stringResource(R.string.character_examples_of_dialogue)
        CharacterPromptField.SystemPrompt -> stringResource(R.string.character_main_prompt_override)
        CharacterPromptField.PostHistoryInstructions -> stringResource(
            R.string.character_post_history_instructions
        )
        CharacterPromptField.DepthPrompt -> stringResource(R.string.character_note)
        is CharacterPromptField.AlternateGreeting -> stringResource(
            R.string.character_alternate_greeting_index,
            index + 1
        )
    }
}

private fun CharacterPromptField.availableMacros(): List<String> {
    return if (this == CharacterPromptField.DialogueExamples) {
        DIALOGUE_EXAMPLE_MACROS
    } else {
        DEFAULT_PROMPT_MACROS
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) minLines.coerceAtLeast(6) else 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = rememberPromptMacroVisualTransformation(),
    showMacroBar: Boolean = false,
    macros: List<String> = DEFAULT_PROMPT_MACROS,
    onExpandFullscreen: (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = value,
                selection = TextRange(
                    textFieldValue.selection.start.coerceIn(0, value.length),
                    textFieldValue.selection.end.coerceIn(0, value.length)
                )
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (maxLines > 1) Modifier.heightIn(max = 220.dp) else Modifier),
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != value) {
                    onChange(newValue.text)
                }
            },
            label = { Text(label) },
            minLines = minLines,
            maxLines = maxLines.coerceAtLeast(minLines),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(12.dp)
        )
        if (showMacroBar) {
            RpMacroActionBar(
                macros = macros,
                onInsertMacro = { macro ->
                    val currentText = textFieldValue.text
                    val selection = textFieldValue.selection
                    val start = selection.min.coerceIn(0, currentText.length)
                    val end = selection.max.coerceIn(0, currentText.length)
                    val before = currentText.substring(0, start)
                    val after = currentText.substring(end)
                    val insertContent = if (macro == "<START>") {
                        if (before.isNotEmpty() && !before.endsWith("\n")) "\n<START>\n" else "<START>\n"
                    } else {
                        macro
                    }
                    val newText = before + insertContent + after
                    val newCursorPos = start + insertContent.length
                    textFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursorPos))
                    onChange(newText)
                },
                onFullscreenClick = onExpandFullscreen
            )
        }
    }
}

@Composable
private fun ListTextField(
    label: String,
    value: String,
    minLines: Int = 1,
    maxLines: Int = minLines.coerceAtLeast(4),
    leadingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = rememberPromptMacroVisualTransformation(),
    showMacroBar: Boolean = false,
    macros: List<String> = DEFAULT_PROMPT_MACROS,
    onExpandFullscreen: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = value,
                selection = TextRange(
                    textFieldValue.selection.start.coerceIn(0, value.length),
                    textFieldValue.selection.end.coerceIn(0, value.length)
                )
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .then(if (maxLines > 1) Modifier.heightIn(max = 220.dp) else Modifier),
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    if (newValue.text != value) {
                        onValueChange(newValue.text)
                    }
                },
                label = { Text(label) },
                minLines = minLines,
                maxLines = maxLines.coerceAtLeast(minLines),
                visualTransformation = visualTransformation,
                shape = RoundedCornerShape(12.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.delete))
            }
        }
        if (showMacroBar) {
            RpMacroActionBar(
                macros = macros,
                onInsertMacro = { macro ->
                    val currentText = textFieldValue.text
                    val selection = textFieldValue.selection
                    val start = selection.min.coerceIn(0, currentText.length)
                    val end = selection.max.coerceIn(0, currentText.length)
                    val before = currentText.substring(0, start)
                    val after = currentText.substring(end)
                    val insertContent = if (macro == "<START>") {
                        if (before.isNotEmpty() && !before.endsWith("\n")) "\n<START>\n" else "<START>\n"
                    } else {
                        macro
                    }
                    val newText = before + insertContent + after
                    val newCursorPos = start + insertContent.length
                    textFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursorPos))
                    onValueChange(newText)
                },
                onFullscreenClick = onExpandFullscreen
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterProviderSelector(
    selectedId: Long,
    availableProviders: List<CharacterProviderItem>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProvider = availableProviders.firstOrNull { it.id == selectedId }
    val selectedName = selectedProvider?.displayName()
        ?: stringResource(R.string.follow_global_model)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.character_model_config)) },
            supportingText = { Text(stringResource(R.string.character_model_config_helper)) },
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
            availableProviders.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName()) },
                    onClick = {
                        onSelect(provider.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CharacterProviderItem.displayName(): String {
    val displayName = name.ifBlank { stringResource(R.string.unnamed_model_config) }
    return if (isEnabled) {
        displayName
    } else {
        stringResource(R.string.disabled_model_config_format, displayName)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LorebookSelector(
    selectedId: Long,
    availableLorebooks: List<CharacterLorebookItem>,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedId == 0L) stringResource(R.string.none)
    else availableLorebooks.find { it.id == selectedId }?.name ?: stringResource(R.string.unknown_world_book)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.associated_world_book)) },
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
                    text = { Text(stringResource(R.string.none)) },
                    onClick = {
                        onSelect(0L)
                        expanded = false
                    }
                )
                availableLorebooks.forEach { lorebook ->
                    DropdownMenuItem(
                        text = { Text(lorebook.name.ifBlank { stringResource(R.string.untitled) }) },
                        onClick = {
                            onSelect(lorebook.id)
                            expanded = false
                        }
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onManage,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(stringResource(R.string.manage_world_books))
        }
    }
}

@Composable
private fun CharacterNoteRoleSelector(
    selectedRole: String,
    onRoleSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRole = selectedRole.trim().toIntOrNull() ?: 0
    val roles = listOf(
        0 to stringResource(R.string.role_system),
        1 to stringResource(R.string.role_user),
        2 to stringResource(R.string.role_assistant)
    )
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        roles.forEach { (roleValue, label) ->
            FilterChip(
                selected = currentRole == roleValue,
                onClick = { onRoleSelected(roleValue.toString()) },
                shape = RoundedCornerShape(10.dp),
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
private fun AvatarPreview(
    avatarText: String,
    avatarColor: Color,
    image: ImageBitmap?,
    size: Int
) {
    if (image == null) {
        RpAvatar(
            text = avatarText,
            color = avatarColor,
            modifier = Modifier.size(size.dp),
            shape = RoundedCornerShape(20.dp)
        )
    } else {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun CharacterEditForm.avatarText(): String {
    return name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private fun CharacterEditForm.avatarColor(): Color {
    val seed = if (id == 0L) name.hashCode().toLong() else id
    return CharacterAccentColors[kotlin.math.abs(seed % CharacterAccentColors.size).toInt()]
}

private fun String.compactPreview(limit: Int = 240): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= limit) compact else compact.take(limit).trimEnd() + "..."
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CharacterEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        CharacterEditLayout(
            uiState = CharacterEditUiState.Normal(
                mode = CharacterEditMode.Edit,
                form = CharacterEditForm(
                    id = 1L,
                    name = "Character",
                    tags = listOf("Tag"),
                    description = "Description",
                    creatorNotes = "Notes",
                    firstMessages = listOf("Hello")
                )
            ),
            emit = {}
        )
    }
}
