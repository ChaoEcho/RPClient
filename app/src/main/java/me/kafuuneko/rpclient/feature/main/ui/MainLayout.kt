package me.kafuuneko.rpclient.feature.main.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.feature.main.model.MainSessionSelection
import me.kafuuneko.rpclient.feature.main.model.MainSessionType
import me.kafuuneko.rpclient.feature.main.presentation.MainDebugSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainGenerationParametersState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeResourceState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeSelectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeSessionTab
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainPromptBehaviorState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderPostProcessingState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentGroupChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummaryInjectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsTab
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserAvatarState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserIdentityState
import me.kafuuneko.rpclient.feature.main.presentation.MainWorldInfoBudgetState
import me.kafuuneko.rpclient.libs.prompt.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionRole
import me.kafuuneko.rpclient.model.TokenPreset
import me.kafuuneko.rpclient.ui.dialog.DeleteSelectedSessionsDialog
import me.kafuuneko.rpclient.ui.dialog.NumericEditDialog
import me.kafuuneko.rpclient.ui.dialog.NumericEditQuickOption
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.ProviderAvailableColor
import me.kafuuneko.rpclient.ui.theme.ProviderDisabledColor
import me.kafuuneko.rpclient.ui.theme.ProviderPendingColor
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPercentageSlider
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import androidx.compose.material.icons.rounded.Image as ImageIcon

/** 主页面 Compose 入口，承载首页会话列表与全局设置。 */
@Composable
fun MainLayout(
    uiState: MainUiState,
    emit: MainUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is MainUiState.Normal) { MainUiIntent.Back.emit() }
    when (uiState) {
        MainUiState.None -> Unit
        is MainUiState.Finished -> MainLayout(uiState.previous) {}
        is MainUiState.Normal -> MainNormal(uiState, emit)
    }
}

@Composable
private fun MainNormal(
    uiState: MainUiState.Normal,
    emit: MainUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialThemeLike.background())
        ) {
            when (uiState.selectedPage) {
                MainPage.Home -> HomePage(uiState.homeState, emit)
                MainPage.Settings -> SettingsPage(uiState.settingsState, emit)
            }

            val selectionState = uiState.homeState.selectionState
                as? MainHomeSelectionState.Selecting
            if (selectionState != null && uiState.selectedPage == MainPage.Home) {
                MultiSelectBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    selectedCount = selectionState.selectedSessions.size,
                    emit = emit
                )
            } else {
                MainBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    selectedPage = uiState.selectedPage,
                    emit = emit
                )
            }
        }
    }

    DialogSwitch(uiState.dialogState, emit)
}

@Composable
private fun MainBottomBar(
    modifier: Modifier = Modifier,
    selectedPage: MainPage,
    emit: MainUiIntent.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomBarItem(
                selected = selectedPage == MainPage.Home,
                onClick = { MainUiIntent.SelectPage(MainPage.Home).emit() },
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.home),
                modifier = Modifier.weight(1f)
            )
            MainBottomBarItem(
                selected = selectedPage == MainPage.Settings,
                onClick = { MainUiIntent.SelectPage(MainPage.Settings).emit() },
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.settings),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MultiSelectBottomBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    emit: MainUiIntent.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomBarItem(
                selected = false,
                onClick = { MainUiIntent.ExitMultiSelect.emit() },
                icon = Icons.Rounded.Close,
                label = stringResource(R.string.cancel),
                modifier = Modifier.weight(1f)
            )
            MainBottomBarItem(
                selected = false,
                onClick = { MainUiIntent.ShowDeleteSelectedDialog.emit() },
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.delete),
                modifier = Modifier.weight(1f),
                enabled = selectedCount > 0
            )
        }
    }
}

@Composable
private fun DialogSwitch(
    dialogState: MainDialogState,
    emit: MainUiIntent.() -> Unit
) {
    when (dialogState) {
        MainDialogState.None -> Unit

        is MainDialogState.DeleteSelectedSessions -> DeleteSelectedSessionsDialog(
            count = dialogState.count,
            onConfirm = { MainUiIntent.ConfirmDeleteSelected.emit() },
            onDismiss = { MainUiIntent.DismissDialog.emit() }
        )

        is MainDialogState.EditGenerationParameter -> NumericEditDialog(
            title = stringResource(dialogState.parameter.titleRes()),
            value = dialogState.draftValue,
            decimalInput = dialogState.parameter.isDecimalInput(),
            quickOptions = dialogState.parameter.quickOptions(),
            onValueChange = { MainUiIntent.ChangeGenerationParameterDraft(it).emit() },
            onConfirm = { MainUiIntent.ConfirmGenerationParameter.emit() },
            onDismiss = { MainUiIntent.DismissDialog.emit() }
        )

        is MainDialogState.ImportChatCharacterSelection -> ImportChatCharacterDialog(
            state = dialogState,
            emit = emit
        )
    }
}

private fun MainGenerationParameter.titleRes(): Int = when (this) {
    MainGenerationParameter.Temperature -> R.string.temperature
    MainGenerationParameter.TopP -> R.string.top_p
    MainGenerationParameter.MaxTokens -> R.string.max_tokens
    MainGenerationParameter.ContextTokens -> R.string.context
}

private fun MainGenerationParameter.isDecimalInput(): Boolean = when (this) {
    MainGenerationParameter.Temperature, MainGenerationParameter.TopP -> true
    MainGenerationParameter.MaxTokens, MainGenerationParameter.ContextTokens -> false
}

private fun MainGenerationParameter.quickOptions(): List<NumericEditQuickOption> {
    if (this != MainGenerationParameter.MaxTokens &&
        this != MainGenerationParameter.ContextTokens
    ) {
        return emptyList()
    }
    return TokenPreset.entries.map { preset ->
        NumericEditQuickOption(
            label = preset.displayName,
            value = preset.value.toString()
        )
    }
}

@Composable
private fun MainBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    val targetContentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val targetContainerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }

    val contentColor by animateColorAsState(targetValue = targetContentColor, label = "bottomBarContentColor")
    val containerColor by animateColorAsState(targetValue = targetContainerColor, label = "bottomBarContainerColor")

    Box(
        modifier = modifier
            .clickable(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(containerColor, CircleShape)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HeroEntryCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AddComment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 顶部快捷行动双栏卡片（群聊与故事创作）。 */
@Composable
private fun HomeQuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(icon = icon, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 首页资产快捷管理金刚区（角色卡、世界书、Regex 脚本）。 */
@Composable
private fun HomeAssetDock(
    resourceState: MainHomeResourceState,
    emit: MainUiIntent.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.character),
            count = resourceState.totalCharacters,
            onClick = { MainUiIntent.OpenCharacterManager.emit() }
        )
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Book,
            title = stringResource(R.string.world_book),
            count = resourceState.totalWorldBooks,
            onClick = { MainUiIntent.OpenWorldBookManager.emit() }
        )
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.DataObject,
            title = stringResource(R.string.regex_script_title),
            count = null,
            onClick = { MainUiIntent.OpenRegexScripts.emit() }
        )
    }
}

/** 单个紧凑资产管理胶囊卡片，带数量 Badge。 */
@Composable
private fun HomeAssetCapsule(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    count: Int? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (count != null) {
                    Surface(
                        modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = if (count > 99) "99+" else count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomePage(
    state: MainHomeState,
    emit: MainUiIntent.() -> Unit
) {
    val selectionState = state.selectionState as? MainHomeSelectionState.Selecting
    val multiSelectMode = selectionState != null
    val selectedSessions = selectionState?.selectedSessions.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.statusBarsPadding())
        }
        if (multiSelectMode) {
            item {
                Text(
                    text = stringResource(R.string.selected_count, selectedSessions.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        } else {
            homeEntryItems(state.resourceState, emit)
        }
        homeSessionSection(
            state = state,
            multiSelectMode = multiSelectMode,
            selectedSessions = selectedSessions,
            emit = emit
        )
    }
}

private fun LazyListScope.homeEntryItems(
    resourceState: MainHomeResourceState,
    emit: MainUiIntent.() -> Unit
) {
    item {
        HeroEntryCard(
            title = stringResource(R.string.new_session),
            subtitle = stringResource(R.string.new_session_desc),
            onClick = { MainUiIntent.OpenCreateChat.emit() }
        )
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeQuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Groups,
                title = stringResource(R.string.group_chat),
                onClick = { MainUiIntent.OpenCreateGroupChat.emit() }
            )
            HomeQuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AutoStories,
                title = stringResource(R.string.story_library),
                onClick = { MainUiIntent.OpenStoryLibrary.emit() }
            )
        }
    }
    item {
        HomeAssetDock(resourceState, emit)
    }
}

private fun LazyListScope.homeSessionSection(
    state: MainHomeState,
    multiSelectMode: Boolean,
    selectedSessions: Set<MainSessionSelection>,
    emit: MainUiIntent.() -> Unit
) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_chats),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (!multiSelectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.selectedSessionTab == MainHomeSessionTab.All,
                        onClick = {
                            MainUiIntent.SelectHomeSessionTab(MainHomeSessionTab.All).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_all)) }
                    )
                    FilterChip(
                        selected = state.selectedSessionTab == MainHomeSessionTab.Single,
                        onClick = {
                            MainUiIntent.SelectHomeSessionTab(MainHomeSessionTab.Single).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_single)) }
                    )
                    FilterChip(
                        selected = state.selectedSessionTab == MainHomeSessionTab.Group,
                        onClick = {
                            MainUiIntent.SelectHomeSessionTab(MainHomeSessionTab.Group).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_group)) }
                    )
                }
            }
        }
    }

    when (state.selectedSessionTab) {
        MainHomeSessionTab.All -> {
            val singleEmpty = state.recentChatsState is MainRecentChatsState.Empty
            val groupEmpty = state.recentGroupChatsState is MainRecentGroupChatsState.Empty

            if (singleEmpty && groupEmpty) {
                item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.ChatBubble,
                        title = stringResource(R.string.no_recent_chats),
                        subtitle = stringResource(R.string.no_recent_chats_desc)
                    )
                }
            } else {
                if (state.recentChatsState is MainRecentChatsState.Content) {
                    recentChatSessionItems(
                        state = state.recentChatsState,
                        multiSelectMode = multiSelectMode,
                        selectedSessions = selectedSessions,
                        emit = emit
                    )
                }
                if (state.recentGroupChatsState is MainRecentGroupChatsState.Content) {
                    if (!singleEmpty) {
                        item {
                            RpSectionHeader(
                                title = stringResource(R.string.recent_group_chats),
                                action = if (multiSelectMode) "" else stringResource(R.string.new_group_chat)
                            ) {
                                if (!multiSelectMode) MainUiIntent.OpenCreateGroupChat.emit()
                            }
                        }
                    }
                    recentGroupChatSessionItems(
                        state = state.recentGroupChatsState,
                        multiSelectMode = multiSelectMode,
                        selectedSessions = selectedSessions,
                        emit = emit
                    )
                }
            }
        }

        MainHomeSessionTab.Single -> {
            when (state.recentChatsState) {
                MainRecentChatsState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.ChatBubble,
                        title = stringResource(R.string.no_recent_chats),
                        subtitle = stringResource(R.string.no_recent_chats_desc)
                    )
                }

                is MainRecentChatsState.Content -> recentChatSessionItems(
                    state = state.recentChatsState,
                    multiSelectMode = multiSelectMode,
                    selectedSessions = selectedSessions,
                    emit = emit
                )
            }
        }

        MainHomeSessionTab.Group -> {
            when (state.recentGroupChatsState) {
                MainRecentGroupChatsState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Groups,
                        title = stringResource(R.string.no_group_chats),
                        subtitle = stringResource(R.string.no_group_chats_desc)
                    )
                }

                is MainRecentGroupChatsState.Content -> recentGroupChatSessionItems(
                    state = state.recentGroupChatsState,
                    multiSelectMode = multiSelectMode,
                    selectedSessions = selectedSessions,
                    emit = emit
                )
            }
        }
    }
}

private fun LazyListScope.recentChatSessionItems(
    state: MainRecentChatsState.Content,
    multiSelectMode: Boolean,
    selectedSessions: Set<MainSessionSelection>,
    emit: MainUiIntent.() -> Unit
) {
    state.sessionGroups.forEach { group ->
        val characterId = group.characterId
        val expanded = characterId !in state.collapsedCharacterIds
        item(key = "character-$characterId") {
            SessionCharacterHeader(
                modifier = Modifier.animateItem(),
                characterName = group.characterName,
                sessionCount = group.sessions.size,
                expanded = expanded,
                onClick = { MainUiIntent.ToggleSessionGroup(characterId).emit() }
            )
        }
        if (!expanded) return@forEach
        items(
            items = group.sessions,
            key = { session -> "session-${session.id}" }
        ) { session ->
            val selection = MainSessionSelection(MainSessionType.Chat, session.id)
            HomeSessionCard(
                modifier = Modifier.animateItem(),
                accentKey = session.characterName,
                icon = Icons.Rounded.ChatBubble,
                title = session.title,
                preview = session.preview,
                metadata = listOf(
                    session.characterName,
                    stringResource(R.string.message_count, session.messageCount),
                    session.updatedAt
                ),
                multiSelectMode = multiSelectMode,
                selected = selection in selectedSessions,
                onClick = {
                    if (multiSelectMode) {
                        MainUiIntent.ToggleSessionSelection(selection).emit()
                    } else {
                        MainUiIntent.OpenChat(session.id).emit()
                    }
                },
                onLongClick = {
                    if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
                }
            )
        }
    }
}

private fun LazyListScope.recentGroupChatSessionItems(
    state: MainRecentGroupChatsState.Content,
    multiSelectMode: Boolean,
    selectedSessions: Set<MainSessionSelection>,
    emit: MainUiIntent.() -> Unit
) {
    items(
        items = state.sessions,
        key = { "group-session-${it.id}" }
    ) { session ->
        val selection = MainSessionSelection(MainSessionType.GroupChat, session.id)
        HomeSessionCard(
            modifier = Modifier.animateItem(),
            accentKey = session.title,
            icon = Icons.Rounded.Groups,
            title = session.title,
            preview = session.preview,
            metadata = listOf(
                session.memberNames,
                stringResource(R.string.message_count, session.messageCount),
                session.updatedAt
            ),
            multiSelectMode = multiSelectMode,
            selected = selection in selectedSessions,
            onClick = {
                if (multiSelectMode) {
                    MainUiIntent.ToggleSessionSelection(selection).emit()
                } else {
                    MainUiIntent.OpenGroupChat(session.id).emit()
                }
            },
            onLongClick = {
                if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSessionCard(
    modifier: Modifier = Modifier,
    accentKey: String,
    icon: ImageVector,
    title: String,
    preview: String,
    metadata: List<String>,
    multiSelectMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        },
        label = "homeSessionCardBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "homeSessionCardContainer"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            if (selected) 1.dp else 0.5.dp,
            borderColor
        )
    ) {
        val accentColor = remember(accentKey) { getMacaronColor(accentKey) }
        val ambientBrush = remember(accentColor) {
            Brush.horizontalGradient(
                listOf(
                    accentColor.copy(alpha = 0.08f),
                    accentColor.copy(alpha = 0.02f),
                    Color.Transparent
                )
            )
        }
        Row(
            modifier = Modifier
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .background(ambientBrush),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RpIconBubble(
                        icon = icon,
                        containerColor = accentColor.copy(alpha = 0.14f),
                        contentColor = accentColor,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                RpMetaRow(items = metadata)
            }
            if (multiSelectMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}



@Composable
private fun SessionCharacterHeader(
    modifier: Modifier = Modifier,
    characterName: String,
    sessionCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = characterName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.session_group_count, sessionCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(
                if (expanded) R.string.collapse_session_group else R.string.expand_session_group
            ),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun SettingsPage(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.statusBarsPadding())
        }
        item {
            RpPageTitle(
                title = stringResource(R.string.setting_page_title),
                subtitle = stringResource(R.string.setting_subtitle)
            )
        }

        // ================= 1. 用户身份与人设 =================
        item {
            RpSectionHeader(title = stringResource(R.string.user_identity))
        }
        item { UserIdentityPanel(state.identityState, emit) }

        // ================= 2. 模型与推理服务 =================
        item {
            RpSectionHeader(
                title = stringResource(R.string.model_provider),
                action = stringResource(R.string.manage)
            ) { MainUiIntent.OpenProviderManager.emit() }
        }
        when (val providerState = state.providerState) {
            MainProviderSettingsState.Empty -> {
                item { EmptyProviderCard { MainUiIntent.OpenProviderManager.emit() } }
            }

            is MainProviderSettingsState.Available -> {
                items(providerState.providers) { provider ->
                    ProviderCard(
                        provider = provider,
                        selected = provider.id == providerState.selectedProviderId,
                        onClick = { MainUiIntent.SelectProvider(provider.id).emit() }
                    )
                }
                item { ParameterPanel(providerState.generationParametersState, emit) }
            }
        }

        // ================= 3. 提示词与上下文记忆 =================
        item {
            RpSectionHeader(title = stringResource(R.string.prompt_and_memory_section))
        }
        item { PromptPresetEntryCard { MainUiIntent.OpenPromptPreset.emit() } }
        item { PromptBehaviorPanel(state.promptBehaviorState, emit) }
        item { WorldInfoBudgetPanel(state.worldInfoBudgetState, emit) }
        item { SummaryPanel(state.summaryState, emit) }

        // ================= 4. 数据与系统 =================
        item {
            RpSectionHeader(title = stringResource(R.string.system_and_data_section))
        }
        item { ChatDataManagementPanel(state.chatDataManagementState, emit) }
        item { DebugPanel(state.debugState, emit) }
        item { AboutEntryCard { emit(MainUiIntent.OpenAbout) } }
    }
}

@Composable
private fun WorldInfoBudgetPanel(
    state: MainWorldInfoBudgetState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = stringResource(R.string.world_info_budget_section))
            Text(
                text = stringResource(R.string.world_info_budget_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
            RpPercentageSlider(
                title = stringResource(R.string.world_info_context_percent),
                value = state.budgetPercent,
                helper = stringResource(R.string.world_info_context_percent_helper),
                onValueChange = { MainUiIntent.ChangeWorldInfoBudgetPercent(it).emit() }
            )
            NumberSettingRow(
                title = stringResource(R.string.world_info_budget_cap),
                value = state.budgetCap.toString(),
                helper = stringResource(R.string.world_info_budget_cap_helper),
                onValueChange = { MainUiIntent.ChangeWorldInfoBudgetCap(it).emit() }
            )
            SettingSwitchRow(
                icon = Icons.Rounded.Book,
                title = stringResource(R.string.world_info_overflow_alert),
                subtitle = stringResource(R.string.world_info_overflow_alert_desc),
                checked = state.overflowAlert,
                onCheckedChange = { MainUiIntent.ToggleWorldInfoOverflowAlert(it).emit() }
            )
        }
    }
}

@Composable
private fun UserIdentityPanel(
    state: MainUserIdentityState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatarPicker(
                    state = state,
                    emit = emit
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.userName,
                        onValueChange = { MainUiIntent.ChangeUserName(it).emit() },
                        label = { Text(stringResource(R.string.user_display_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.userDescription,
                        onValueChange = { MainUiIntent.ChangeUserDescription(it).emit() },
                        label = { Text(stringResource(R.string.user_persona_description)) },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.avatarState is MainUserAvatarState.Configured) {
                        TextButton(
                            onClick = { MainUiIntent.ClearUserAvatar.emit() }
                        ) {
                            Text(stringResource(R.string.clear_user_avatar))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserAvatarPicker(
    state: MainUserIdentityState,
    emit: MainUiIntent.() -> Unit
) {
    val avatarText = state.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColor = remember(state.userName) {
        getMacaronColor(state.userName.ifBlank { "user" })
    }

    Surface(
        modifier = Modifier
            .size(72.dp)
            .clickable { MainUiIntent.PickUserAvatarClick.emit() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            val avatarState = state.avatarState
            val avatarImage = (avatarState as? MainUserAvatarState.Configured)?.image
            if (avatarImage == null) {
                RpAvatar(
                    text = avatarText,
                    color = avatarColor,
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                Image(
                    bitmap = avatarImage,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Rounded.ImageIcon,
                    contentDescription = stringResource(R.string.choose_user_avatar),
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun PromptBehaviorPanel(
    state: MainPromptBehaviorState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = stringResource(R.string.prompt_behavior_section))
            Text(
                text = stringResource(R.string.prompt_post_processing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
            val postProcessingState = state.providerPostProcessingState
            val selectedMode = (postProcessingState as? MainProviderPostProcessingState.Available)
                ?.mode
            PromptPostProcessingMode.entries.forEach { mode ->
                PromptPostProcessingModeRow(
                    mode = mode,
                    selected = mode == selectedMode,
                    enabled = postProcessingState is MainProviderPostProcessingState.Available,
                    onClick = { MainUiIntent.SelectPostProcessingMode(mode).emit() }
                )
            }
            Text(
                text = stringResource(R.string.prompt_example_behavior_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.prompt_example_behavior_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExampleDialogueBehavior.entries.forEach { behavior ->
                    FilterChip(
                        selected = behavior == state.exampleDialogueBehavior,
                        onClick = {
                            MainUiIntent.SelectExampleDialogueBehavior(behavior).emit()
                        },
                        label = { Text(stringResource(behavior.titleRes())) }
                    )
                }
            }
            SettingSwitchRow(
                icon = Icons.Rounded.Compress,
                title = stringResource(R.string.prompt_include_think_context_title),
                subtitle = stringResource(R.string.prompt_include_think_context_desc),
                checked = state.includeThinkInContext,
                onCheckedChange = { MainUiIntent.ToggleIncludeThinkInContext(it).emit() }
            )
            SettingSwitchRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.context_trimming_alert),
                subtitle = stringResource(R.string.context_trimming_alert_desc),
                checked = state.contextTrimmingAlert,
                onCheckedChange = { MainUiIntent.ToggleContextTrimmingAlert(it).emit() }
            )
            SettingSwitchRow(
                Icons.Rounded.Refresh,
                stringResource(R.string.streaming_response),
                stringResource(R.string.streaming_response_desc),
                state.streamEnabled,
                onCheckedChange = { MainUiIntent.ToggleStreamEnabled(it).emit() }
            )
        }
    }
}

@Composable
private fun PromptPostProcessingModeRow(
    mode: PromptPostProcessingMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
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

@Composable
private fun EmptyProviderCard(
    onClick: () -> Unit
) {
    RpInfoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        icon = Icons.Rounded.Storage,
        title = stringResource(R.string.no_enabled_model),
        subtitle = stringResource(R.string.go_to_model_manager)
    )
}

@Composable
private fun ProviderCard(
    provider: MainProviderItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = 0.22f
            )
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(if (provider.isEnabled) Icons.Rounded.Key else Icons.Rounded.Storage)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    provider.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    provider.model,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    provider.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.48f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val dotColor = when {
                !provider.isEnabled -> ProviderDisabledColor
                !provider.isConfigured -> ProviderPendingColor
                else -> ProviderAvailableColor
            }
            val statusText = when {
                !provider.isEnabled -> stringResource(R.string.not_enabled)
                !provider.isConfigured -> stringResource(R.string.pending_config)
                else -> stringResource(R.string.available)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ParameterPanel(
    state: MainGenerationParametersState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(
                title = stringResource(R.string.generation_parameters),
                action = stringResource(R.string.preset)
            ) { MainUiIntent.OpenSelectedProviderEdit.emit() }
            ParameterRow(
                stringResource(R.string.temperature),
                state.temperature.toString()
            ) {
                MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.Temperature)
                    .emit()
            }
            ParameterRow(
                stringResource(R.string.top_p),
                state.topP.toString()
            ) { MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.TopP).emit() }
            ParameterRow(
                stringResource(R.string.max_tokens),
                state.maxTokens.toString()
            ) {
                MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.MaxTokens).emit()
            }
            ParameterRow(
                stringResource(R.string.context),
                "${state.contextTokens} ${stringResource(R.string.tokens)}"
            ) {
                MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.ContextTokens)
                    .emit()
            }
        }
    }
}

@Composable
private fun ParameterRow(label: String, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        FilterChip(selected = true, onClick = onClick, label = { Text(value) })
    }
}

@Composable
private fun PromptPresetEntryCard(onClick: () -> Unit) {
    RpInfoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        icon = Icons.Rounded.AutoAwesome,
        title = stringResource(R.string.prompt_preset_title),
        subtitle = stringResource(R.string.prompt_preset_entry_subtitle)
    )
}

@Composable
private fun SummaryPanel(
    state: MainSummarySettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(
                title = stringResource(R.string.summary_memory)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MainSummarySettingsTab.entries.forEach { tab ->
                    FilterChip(
                        selected = tab == state.selectedTab,
                        onClick = { MainUiIntent.SelectSummarySettingsTab(tab).emit() },
                        label = { Text(stringResource(tab.titleRes())) }
                    )
                }
            }
            when (state.selectedTab) {
                MainSummarySettingsTab.General -> GeneralSummarySettings(state, emit)
                MainSummarySettingsTab.Conversation -> ConversationSummarySettings(state, emit)
            }
        }
    }
}

@Composable
private fun GeneralSummarySettings(
    state: MainSummarySettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberSettingRow(
            title = stringResource(R.string.summary_target_words),
            value = state.wordsLimit.toString(),
            onValueChange = { MainUiIntent.ChangeSummaryWordsLimit(it).emit() }
        )
        NumberSettingRow(
            title = stringResource(R.string.summary_response_tokens),
            value = state.responseTokens.toString(),
            onValueChange = { MainUiIntent.ChangeSummaryResponseTokens(it).emit() }
        )
    }
}

@Composable
private fun ConversationSummarySettings(
    state: MainSummarySettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingSwitchRow(
            Icons.Rounded.AutoAwesome,
            stringResource(R.string.auto_summarize),
            stringResource(R.string.auto_summarize_desc),
            state.autoSummaryEnabled,
            onCheckedChange = { MainUiIntent.ToggleAutoSummaryEnabled(it).emit() }
        )
        NumberSettingRow(
            title = stringResource(R.string.summary_update_every_messages),
            value = state.triggerMessageCount.toString(),
            onValueChange = { MainUiIntent.ChangeSummaryTriggerMessageCount(it).emit() }
        )
        NumberSettingRow(
            title = stringResource(R.string.summary_max_messages_per_request),
            value = state.maxMessagesPerRequest.toString(),
            helper = stringResource(R.string.summary_max_messages_helper),
            onValueChange = { MainUiIntent.ChangeSummaryMaxMessagesPerRequest(it).emit() }
        )
        Text(
            text = stringResource(R.string.summary_injection_position),
            style = MaterialTheme.typography.titleSmall
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryInjectionPosition.entries.forEach { position ->
                FilterChip(
                    selected = position == state.injectionState.position,
                    onClick = {
                        MainUiIntent.SelectSummaryInjectionPosition(position).emit()
                    },
                    label = { Text(stringResource(position.titleRes())) }
                )
            }
        }
        val injectionState = state.injectionState
        if (injectionState is MainSummaryInjectionState.InChat) {
            NumberSettingRow(
                title = stringResource(R.string.summary_injection_depth),
                value = injectionState.depth.toString(),
                onValueChange = {
                    MainUiIntent.ChangeSummaryInjectionDepth(it).emit()
                }
            )
            Text(
                text = stringResource(R.string.summary_injection_role),
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryInjectionRole.entries.forEach { role ->
                    FilterChip(
                        selected = role == injectionState.role,
                        onClick = {
                            MainUiIntent.SelectSummaryInjectionRole(role).emit()
                        },
                        label = { Text(stringResource(role.titleRes())) }
                    )
                }
            }
        }
    }
}

private fun MainSummarySettingsTab.titleRes(): Int {
    return when (this) {
        MainSummarySettingsTab.General -> R.string.general_summary_memory
        MainSummarySettingsTab.Conversation -> R.string.conversation_summary_memory
    }
}

@Composable
private fun NumberSettingRow(
    title: String,
    value: String,
    helper: String? = null,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!helper.isNullOrBlank()) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.width(100.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
    }
}

@Composable
private fun DebugPanel(
    state: MainDebugSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = stringResource(R.string.debug_mode))
            SettingSwitchRow(
                Icons.Rounded.BugReport,
                stringResource(R.string.debug_mode),
                stringResource(R.string.debug_mode_desc),
                state.enabled,
                onCheckedChange = { MainUiIntent.ToggleDebugModeEnabled(it).emit() }
            )
            if (state.enabled) {
                RpInfoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { MainUiIntent.OpenRequestLogs.emit() },
                    icon = Icons.Rounded.DataObject,
                    title = stringResource(R.string.request_logs),
                    subtitle = stringResource(R.string.request_logs_entry_subtitle)
                )
            }
        }
    }
}

@Composable
private fun AboutEntryCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(Icons.Rounded.Info)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.about_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RpIconBubble(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun PromptPostProcessingMode.titleRes(): Int {
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

private fun ExampleDialogueBehavior.titleRes(): Int {
    return when (this) {
        ExampleDialogueBehavior.Normal -> R.string.prompt_example_behavior_normal
        ExampleDialogueBehavior.Pinned -> R.string.prompt_example_behavior_pinned
        ExampleDialogueBehavior.Disabled -> R.string.prompt_example_behavior_disabled
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

private object MaterialThemeLike {
    @Composable
    fun background() = MaterialTheme.colorScheme.background
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainLayoutPreview() {
    AppTheme(dynamicColor = false) {
        MainLayout(
            uiState = MainUiState.Normal(
                homeState = MainHomeState(
                    resourceState = MainHomeResourceState(
                        totalCharacters = 24,
                        totalWorldBooks = 7
                    ),
                    recentChatsState = MainRecentChatsState.Content(
                        sessionGroups = listOf(
                            MainChatSessionGroup(
                                characterId = "1",
                                characterName = "Luna",
                                sessions = listOf(
                                    MainChatSessionItem(
                                        id = "1",
                                        characterId = "1",
                                        characterName = "Luna",
                                        title = "Night train",
                                        preview = "The city lights recede beyond the window.",
                                        messageCount = 18,
                                        updatedAt = "06-15 21:30"
                                    )
                                )
                            )
                        )
                    ),
                    recentGroupChatsState = MainRecentGroupChatsState.Content(
                        sessions = listOf(
                            MainGroupChatSessionItem(
                                id = "1",
                                title = "Expedition team",
                                memberNames = "Luna, Aster, Rowan",
                                preview = "We should reach the ruins before sunrise.",
                                messageCount = 42,
                                updatedAt = "06-15 22:10"
                            )
                        )
                    )
                ),
                settingsState = MainSettingsState(
                    identityState = MainUserIdentityState(
                        userName = "You",
                        userDescription = "",
                        avatarState = MainUserAvatarState.None
                    ),
                    providerState = MainProviderSettingsState.Empty,
                    promptBehaviorState = MainPromptBehaviorState(
                        providerPostProcessingState = MainProviderPostProcessingState.Unavailable,
                        exampleDialogueBehavior = ExampleDialogueBehavior.default,
                        includeThinkInContext = false,
                        contextTrimmingAlert = true,
                        streamEnabled = true
                    ),
                    worldInfoBudgetState = MainWorldInfoBudgetState(
                        budgetPercent = 25,
                        budgetCap = 0,
                        overflowAlert = true
                    ),
                    summaryState = MainSummarySettingsState(
                        autoSummaryEnabled = false,
                        triggerMessageCount = 20,
                        wordsLimit = 500,
                        maxMessagesPerRequest = 0,
                        responseTokens = 800,
                        injectionState = MainSummaryInjectionState.AfterMain
                    ),
                    debugState = MainDebugSettingsState(enabled = false)
                )
            ),
            emit = {}
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainSettingsLayoutPreview() {
    AppTheme(dynamicColor = false) {
        MainLayout(
            uiState = MainUiState.Normal(
                selectedPage = MainPage.Settings,
                homeState = MainHomeState(
                    resourceState = MainHomeResourceState(
                        totalCharacters = 24,
                        totalWorldBooks = 7
                    ),
                    recentChatsState = MainRecentChatsState.Empty,
                    recentGroupChatsState = MainRecentGroupChatsState.Empty
                ),
                settingsState = MainSettingsState(
                    identityState = MainUserIdentityState(
                        userName = "KafuuNeko",
                        userDescription = "A traveler exploring AI worlds.",
                        avatarState = MainUserAvatarState.None
                    ),
                    providerState = MainProviderSettingsState.Available(
                        selectedProviderId = 1L,
                        providers = listOf(
                            MainProviderItem(
                                id = 1L,
                                name = "DeepSeek V3",
                                model = "deepseek-chat",
                                baseUrl = "https://api.deepseek.com/v1",
                                isEnabled = true
                            )
                        ),
                        generationParametersState = MainGenerationParametersState(
                            temperature = 0.8f,
                            topP = 0.95f,
                            maxTokens = 4096,
                            contextTokens = 32768
                        )
                    ),
                    promptBehaviorState = MainPromptBehaviorState(
                        providerPostProcessingState = MainProviderPostProcessingState.Available(
                            mode = PromptPostProcessingMode.Strict
                        ),
                        exampleDialogueBehavior = ExampleDialogueBehavior.Normal,
                        includeThinkInContext = true,
                        contextTrimmingAlert = true,
                        streamEnabled = true
                    ),
                    worldInfoBudgetState = MainWorldInfoBudgetState(
                        budgetPercent = 25,
                        budgetCap = 2048,
                        overflowAlert = true
                    ),
                    summaryState = MainSummarySettingsState(
                        autoSummaryEnabled = true,
                        triggerMessageCount = 20,
                        wordsLimit = 500,
                        maxMessagesPerRequest = 0,
                        responseTokens = 800,
                        injectionState = MainSummaryInjectionState.InChat(
                            depth = 4,
                            role = SummaryInjectionRole.System
                        )
                    ),
                    debugState = MainDebugSettingsState(enabled = true)
                )
            ),
            emit = {}
        )
    }
}
