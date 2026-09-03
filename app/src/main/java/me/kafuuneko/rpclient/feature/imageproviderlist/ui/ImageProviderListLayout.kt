package me.kafuuneko.rpclient.feature.imageproviderlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imageproviderlist.model.ImageProviderListItem
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImagePromptProviderItem
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListDialogState
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListUiIntent
import me.kafuuneko.rpclient.feature.imageproviderlist.presentation.ImageProviderListUiState
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpCollapsibleSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpFormTextField
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDropdown
import me.kafuuneko.rpclient.ui.widgets.RpTagPill

/** 图片服务列表页 Compose 入口。 */
@Composable
fun ImageProviderListLayout(
    uiState: ImageProviderListUiState,
    emit: ImageProviderListUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is ImageProviderListUiState.Normal) {
        ImageProviderListUiIntent.Back.emit()
    }
    when (uiState) {
        ImageProviderListUiState.None -> Unit
        is ImageProviderListUiState.Finished -> ImageProviderListLayout(uiState.previous) {}
        is ImageProviderListUiState.Normal -> {
            ImageProviderListNormal(uiState, emit)
            ImageProviderListDialog(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun ImageProviderListNormal(
    state: ImageProviderListUiState.Normal,
    emit: ImageProviderListUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.image_provider_title),
            onBack = { ImageProviderListUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { ImageProviderListUiIntent.CreateProvider.emit() }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.create_image_provider)
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.image_provider_title),
                    subtitle = stringResource(R.string.image_provider_list_subtitle)
                )
            }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.all_image_providers),
                    action = stringResource(R.string.create),
                    onAction = { ImageProviderListUiIntent.CreateProvider.emit() }
                )
            }
            if (state.isLoading) {
                item { LoadingRow() }
            }
            if (state.providers.isEmpty() && !state.isLoading) {
                item {
                    ImageProviderEmptyState(
                        onCreateClick = { ImageProviderListUiIntent.CreateProvider.emit() }
                    )
                }
            } else {
                items(state.providers, key = { it.id }) { provider ->
                    ImageProviderCard(
                        provider = provider,
                        onClick = { ImageProviderListUiIntent.EditProvider(provider.id).emit() },
                        onSelectCurrent = {
                            ImageProviderListUiIntent.SelectCurrentProvider(provider.id).emit()
                        },
                        onDelete = {
                            ImageProviderListUiIntent.ShowDeleteProviderDialog(provider.id).emit()
                        }
                    )
                }
            }
            item {
                PromptModelPanel(
                    selectedProviderId = state.promptProviderId,
                    providers = state.promptProviders,
                    emit = emit
                )
            }
            item {
                StylePanel(
                    sceneStylePrompt = state.sceneStylePrompt,
                    avatarStylePrompt = state.avatarStylePrompt,
                    emit = emit
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ImageProviderEmptyState(onCreateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.no_image_providers_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.no_image_providers_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onCreateClick, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.add_first_image_provider))
            }
        }
    }
}

@Composable
private fun ImageProviderCard(
    provider: ImageProviderListItem,
    onClick: () -> Unit,
    onSelectCurrent: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (provider.isCurrent) 1.dp else 0.8.dp,
            color = if (provider.isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            }
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.baseUrl.removePrefix("https://").removePrefix("http://"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.widthIn(max = 200.dp)
                ) {
                    Text(
                        text = provider.model.ifBlank { stringResource(R.string.pending_config) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (provider.isCurrent) {
                        RpTagPill(stringResource(R.string.current_badge))
                    } else {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable(onClick = onSelectCurrent)
                        ) {
                            Text(
                                text = stringResource(R.string.set_as_current),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    RpMetaPill(
                        stringResource(
                            R.string.llm_provider_concurrency_badge,
                            provider.maxConcurrentRequests
                        )
                    )
                    ImageProviderStatusDot(isConfigured = provider.isConfigured)
                }
            }
        }
    }
}

@Composable
private fun ImageProviderStatusDot(isConfigured: Boolean) {
    val dotColor = if (isConfigured) Color(0xFF10B981) else MaterialTheme.colorScheme.error
    Surface(
        shape = CircleShape,
        color = dotColor.copy(alpha = 0.2f),
        modifier = Modifier.size(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(7.dp)) {}
        }
    }
}

@Composable
private fun PromptModelPanel(
    selectedProviderId: Long,
    providers: List<ImagePromptProviderItem>,
    emit: ImageProviderListUiIntent.() -> Unit
) {
    val followChatModel = stringResource(R.string.image_prompt_follow_chat_model)
    RpPanel {
        RpSectionHeader(title = stringResource(R.string.image_prompt_model_section))

        RpSettingsDropdown(
            label = stringResource(R.string.image_prompt_model),
            supportingText = stringResource(R.string.image_prompt_model_helper),
            selectedLabel = providers.firstOrNull { it.id == selectedProviderId }?.displayName()
                ?: followChatModel,
            values = providers,
            valueLabel = { it.displayName() },
            onSelect = { ImageProviderListUiIntent.ChangePromptProvider(it.id).emit() },
            leadingOption = followChatModel to {
                ImageProviderListUiIntent.ChangePromptProvider(0L).emit()
            }
        )
    }
}

@Composable
private fun StylePanel(
    sceneStylePrompt: String,
    avatarStylePrompt: String,
    emit: ImageProviderListUiIntent.() -> Unit
) {
    RpCollapsibleSettingsGroup(
        title = stringResource(R.string.image_generation_style_section),
        subtitle = stringResource(R.string.image_generation_style_prompt),
        initiallyExpanded = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpFormTextField(
                value = sceneStylePrompt,
                label = stringResource(R.string.image_generation_scene_style_prompt),
                supportingText = stringResource(R.string.image_generation_scene_style_prompt_desc),
                onValueChange = { ImageProviderListUiIntent.ChangeSceneStylePrompt(it).emit() },
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Default
            )
            RpFormTextField(
                value = avatarStylePrompt,
                label = stringResource(R.string.image_generation_avatar_style_prompt),
                supportingText = stringResource(R.string.image_generation_avatar_style_prompt_desc),
                onValueChange = { ImageProviderListUiIntent.ChangeAvatarStylePrompt(it).emit() },
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Default
            )
        }
    }
}

@Composable
private fun ImageProviderListDialog(
    dialogState: ImageProviderListDialogState,
    emit: ImageProviderListUiIntent.() -> Unit
) {
    when (dialogState) {
        ImageProviderListDialogState.None -> Unit
        is ImageProviderListDialogState.DeleteProvider -> AppDangerDialog(
            onDismissRequest = { ImageProviderListUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_image_provider),
            message = stringResource(
                R.string.delete_image_provider_message,
                dialogState.providerName
            ),
            isConfirmLoading = dialogState.isDeleting,
            onConfirm = { ImageProviderListUiIntent.ConfirmDeleteProvider.emit() }
        )
    }
}

private fun ImagePromptProviderItem.displayName(): String {
    val providerName = name.trim()
    val providerModel = model.trim()
    return when {
        providerName.isEmpty() -> providerModel
        providerModel.isEmpty() -> providerName
        else -> "$providerName · $providerModel"
    }
}

@Preview(showBackground = true)
@Composable
private fun ImageProviderListPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        ImageProviderListLayout(
            uiState = ImageProviderListUiState.Normal(
                providers = listOf(
                    ImageProviderListItem(
                        id = 1L,
                        name = "api.openai.com",
                        baseUrl = "https://api.openai.com/v1",
                        model = "gpt-image-2",
                        maxConcurrentRequests = 1,
                        isCurrent = true
                    )
                )
            ),
            emit = {}
        )
    }
}
