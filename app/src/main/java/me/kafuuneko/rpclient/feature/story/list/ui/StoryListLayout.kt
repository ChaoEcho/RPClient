package me.kafuuneko.rpclient.feature.story.list.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.story.list.model.StoryListItem
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListContentState
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListDialogState
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListUiIntent
import me.kafuuneko.rpclient.feature.story.list.presentation.StoryListUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

/** Story 列表与 CRUD 对话框的 Compose 入口。 */
@Composable
fun StoryListLayout(
    uiState: StoryListUiState,
    emit: StoryListUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is StoryListUiState.Normal) {
        StoryListUiIntent.Back.emit()
    }
    when (uiState) {
        StoryListUiState.None -> Unit
        is StoryListUiState.Normal -> StoryListNormal(uiState, emit)
        is StoryListUiState.Finished -> StoryListLayout(uiState.previous) {}
    }
}

@Composable
private fun StoryListNormal(
    state: StoryListUiState.Normal,
    emit: StoryListUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.story_library),
            onBack = { StoryListUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { StoryListUiIntent.ShowCreateStoryDialog.emit() }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.story_create_story)
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.story_library_title),
                    subtitle = stringResource(R.string.story_library_subtitle)
                )
            }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.story_my_stories),
                    action = stringResource(R.string.create),
                    onAction = { StoryListUiIntent.ShowCreateStoryDialog.emit() }
                )
            }
            when (val contentState = state.contentState) {
                StoryListContentState.Loading -> item { LoadingPanel() }
                StoryListContentState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = stringResource(R.string.story_empty_title),
                        subtitle = stringResource(R.string.story_empty_subtitle)
                    )
                }
                is StoryListContentState.Content -> items(
                    items = contentState.stories,
                    key = { it.id }
                ) { story ->
                    StoryCard(
                        story = story,
                        onOpen = { StoryListUiIntent.OpenStory(story.id).emit() },
                        onRename = {
                            StoryListUiIntent.ShowRenameStoryDialog(story.id).emit()
                        },
                        onDelete = {
                            StoryListUiIntent.ShowDeleteStoryDialog(story.id).emit()
                        }
                    )
                }
            }
        }
    }
    StoryListDialog(state.dialogState, emit)
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun StoryCard(
    story: StoryListItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            RpIconBubble(Icons.AutoMirrored.Rounded.MenuBook)
            Spacer(Modifier.width(13.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = story.preview.ifBlank {
                        stringResource(R.string.story_empty_document_preview)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RpTagRow(
                        modifier = Modifier.weight(1f),
                        tags = listOf(
                            stringResource(
                                R.string.story_character_count,
                                story.characterCount
                            )
                        )
                    )
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = story.updatedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StoryMenu(onRename = onRename, onDelete = onDelete)
        }
    }
}

@Composable
private fun StoryMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun StoryListDialog(
    dialogState: StoryListDialogState,
    emit: StoryListUiIntent.() -> Unit
) {
    when (dialogState) {
        StoryListDialogState.None -> Unit
        is StoryListDialogState.EditTitle -> AlertDialog(
            onDismissRequest = { StoryListUiIntent.DismissDialog.emit() },
            title = {
                Text(
                    stringResource(
                        if (dialogState.storyId == null) {
                            R.string.story_create_story
                        } else {
                            R.string.story_rename_story
                        }
                    )
                )
            },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dialogState.title,
                    onValueChange = { StoryListUiIntent.ChangeTitleDraft(it).emit() },
                    label = { Text(stringResource(R.string.story_title)) },
                    enabled = !dialogState.isSaving,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { StoryListUiIntent.ConfirmTitle.emit() },
                    enabled = dialogState.title.isNotBlank() && !dialogState.isSaving
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { StoryListUiIntent.DismissDialog.emit() },
                    enabled = !dialogState.isSaving
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        is StoryListDialogState.DeleteStory -> AlertDialog(
            onDismissRequest = { StoryListUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.story_delete_story)) },
            text = {
                Text(stringResource(R.string.story_delete_story_message, dialogState.title))
            },
            confirmButton = {
                TextButton(
                    onClick = { StoryListUiIntent.ConfirmDeleteStory.emit() },
                    enabled = !dialogState.isDeleting
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { StoryListUiIntent.DismissDialog.emit() },
                    enabled = !dialogState.isDeleting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StoryListLayoutPreview() {
    AppTheme(dynamicColor = false) {
        StoryListLayout(
            uiState = StoryListUiState.Normal(
                contentState = StoryListContentState.Content(
                    listOf(
                        StoryListItem(
                            id = 1L,
                            title = "Rain over the old city",
                            preview = "The station clock stopped at midnight, just as the last train arrived.",
                            characterCount = 12840,
                            updatedAt = "08-05 21:40"
                        ),
                        StoryListItem(
                            id = 2L,
                            title = "Untitled journey",
                            preview = "",
                            characterCount = 0,
                            updatedAt = "08-03 09:12"
                        )
                    )
                )
            ),
            emit = {}
        )
    }
}
