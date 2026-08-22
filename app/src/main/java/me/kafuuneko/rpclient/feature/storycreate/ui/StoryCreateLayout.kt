package me.kafuuneko.rpclient.feature.storycreate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateLoadState
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiIntent
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiState
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

/** 新建 Story 页面 Compose 入口。 */
@Composable
fun StoryCreateLayout(
    uiState: StoryCreateUiState,
    emit: StoryCreateUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is StoryCreateUiState.Normal) {
        StoryCreateUiIntent.Back.emit()
    }
    when (uiState) {
        StoryCreateUiState.None -> Unit
        is StoryCreateUiState.Normal -> StoryCreateNormal(uiState, emit)
        is StoryCreateUiState.Finished -> StoryCreateLayout(uiState.previous) {}
    }
}

@Composable
private fun StoryCreateNormal(
    state: StoryCreateUiState.Normal,
    emit: StoryCreateUiIntent.() -> Unit
) {
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val searchingLorebooks = state.lorebookQuery.isNotBlank()
    val controlsEnabled = state.loadState == StoryCreateLoadState.Ready

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.story_create_story),
            onBack = { StoryCreateUiIntent.Back.emit() }
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
                    title = stringResource(R.string.story_create_story),
                    subtitle = stringResource(R.string.story_create_subtitle)
                )
            }
            if (state.loadState == StoryCreateLoadState.Loading) {
                item { LoadingRow() }
            } else {
                item { StoryTitleField(state.form, controlsEnabled, emit) }
                item {
                    RpSectionHeader(title = stringResource(R.string.story_character_references))
                }
                item {
                    Text(
                        text = stringResource(R.string.story_character_references_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.characters.isEmpty()) {
                    item {
                        EmptyCard(
                            icon = Icons.Rounded.Person,
                            text = stringResource(R.string.story_no_characters)
                        )
                    }
                }
                items(state.characters, key = { "character-${it.id}" }) { character ->
                    CharacterOption(
                        character = character,
                        selected = character.id in state.form.selectedCharacterIds,
                        enabled = controlsEnabled,
                        onClick = { StoryCreateUiIntent.ToggleCharacter(character.id).emit() }
                    )
                }
                item {
                    RpSectionHeader(title = stringResource(R.string.enabled_world_book_entries))
                }
                item {
                    Text(
                        text = stringResource(R.string.story_lorebook_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.lorebookGroups.isNotEmpty()) {
                    item {
                        LorebookSearchField(
                            query = state.lorebookQuery,
                            enabled = controlsEnabled,
                            onQueryChange = {
                                StoryCreateUiIntent.ChangeLorebookQuery(it).emit()
                            }
                        )
                    }
                }
                if (state.lorebookGroups.isEmpty()) {
                    item {
                        EmptyCard(
                            icon = Icons.Rounded.Book,
                            text = stringResource(R.string.no_world_book_entries_selectable)
                        )
                    }
                } else if (state.visibleLorebookGroups.isEmpty()) {
                    item {
                        EmptyCard(
                            icon = Icons.Rounded.Search,
                            text = stringResource(R.string.no_world_book_search_results)
                        )
                    }
                }
                items(state.visibleLorebookGroups, key = { "lorebook-${it.lorebookId}" }) { group ->
                    val selectedCount = state.lorebookGroups
                        .firstOrNull { it.lorebookId == group.lorebookId }
                        ?.entries
                        ?.count { it.id in state.form.selectedLorebookEntryIds }
                        ?: 0
                    LorebookGroupOption(
                        group = group,
                        selectedEntryIds = state.form.selectedLorebookEntryIds,
                        selectedCount = selectedCount,
                        expanded = searchingLorebooks || group.lorebookId in expandedLorebookIds,
                        enabled = controlsEnabled,
                        onExpandedChange = {
                            expandedLorebookIds = expandedLorebookIds.toggle(group.lorebookId)
                        },
                        emit = emit
                    )
                }
                item {
                    CreateButton(
                        title = state.form.title,
                        loadState = state.loadState,
                        emit = emit
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryTitleField(
    form: StoryCreateForm,
    enabled: Boolean,
    emit: StoryCreateUiIntent.() -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.title,
        onValueChange = { StoryCreateUiIntent.ChangeTitle(it).emit() },
        label = { Text(stringResource(R.string.story_title)) },
        leadingIcon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CharacterOption(
    character: StoryCreateCharacterItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val autoLabel = stringResource(R.string.story_character_auto)
    val linkedLorebookLabel = character.linkedLorebookName?.let { name ->
        stringResource(R.string.story_linked_lorebook, name)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                enabled = enabled,
                onCheckedChange = { onClick() }
            )
            Spacer(Modifier.width(8.dp))
            RpIconBubble(Icons.Rounded.Person)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = character.description.ifBlank {
                        stringResource(R.string.no_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val tags = buildList {
                    if (selected) add(autoLabel)
                    linkedLorebookLabel?.let(::add)
                    addAll(character.tags)
                }
                RpTagRow(tags = tags, maxCount = 4)
            }
        }
    }
}

@Composable
private fun LorebookGroupOption(
    group: StoryCreateLorebookGroupItem,
    selectedEntryIds: Set<Long>,
    selectedCount: Int,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: () -> Unit,
    emit: StoryCreateUiIntent.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onExpandedChange),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Checkbox(
                    checked = group.entryCount > 0 && selectedCount == group.entryCount,
                    enabled = enabled,
                    onCheckedChange = {
                        StoryCreateUiIntent.ToggleLorebook(group.lorebookId).emit()
                    }
                )
                Spacer(Modifier.width(8.dp))
                RpIconBubble(Icons.Rounded.Book)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.lorebookName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            selectedCount,
                            group.entryCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (expanded) {
                group.entries.forEach { entry ->
                    LorebookEntryOption(
                        entry = entry,
                        selected = entry.id in selectedEntryIds,
                        enabled = enabled,
                        onClick = {
                            StoryCreateUiIntent.ToggleLorebookEntry(entry.id).emit()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryOption(
    entry: StoryCreateLorebookEntryItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val constantLabel = stringResource(R.string.entry_constant)
    val orderDepthLabel = stringResource(
        R.string.entry_order_depth,
        entry.order,
        entry.depth
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                enabled = enabled,
                onCheckedChange = { onClick() }
            )
            Spacer(Modifier.width(8.dp))
            RpIconBubble(Icons.Rounded.Book)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RpTagRow(
                    tags = buildList {
                        add(entry.lorebookName)
                        if (entry.constant) add(constantLabel)
                        add(orderDepthLabel)
                    }
                )
            }
        }
    }
}

@Composable
private fun LorebookSearchField(
    query: String,
    enabled: Boolean,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.search_world_books),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    enabled = enabled,
                    onClick = { onQueryChange("") }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CreateButton(
    title: String,
    loadState: StoryCreateLoadState,
    emit: StoryCreateUiIntent.() -> Unit
) {
    val creating = loadState == StoryCreateLoadState.Creating
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = title.isNotBlank() && !creating,
        onClick = { StoryCreateUiIntent.CreateStory.emit() }
    ) {
        if (creating) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.story_create_story))
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCard(
    icon: ImageVector,
    text: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(icon)
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StoryCreateLayoutPreview() {
    AppTheme(dynamicColor = false) {
        val lorebookGroup = StoryCreateLorebookGroupItem(
            lorebookId = 2L,
            lorebookName = "Fog Harbor",
            entries = listOf(
                StoryCreateLorebookEntryItem(
                    id = 10L,
                    lorebookName = "Fog Harbor",
                    name = "Old Town",
                    content = "A rain-soaked district surrounding the central archive.",
                    keywords = listOf("old town"),
                    constant = false,
                    order = 100,
                    depth = 0
                )
            )
        )
        StoryCreateLayout(
            uiState = StoryCreateUiState.Normal(
                loadState = StoryCreateLoadState.Ready,
                form = StoryCreateForm(
                    title = "Rain over the old city",
                    selectedCharacterIds = setOf(1L),
                    selectedLorebookEntryIds = setOf(10L)
                ),
                characters = listOf(
                    StoryCreateCharacterItem(
                        id = 1L,
                        name = "Lyra",
                        description = "An archivist following a trail through the old city.",
                        tags = listOf("Mystery"),
                        linkedLorebookId = 2L,
                        linkedLorebookName = "Fog Harbor"
                    )
                ),
                lorebookGroups = listOf(lorebookGroup),
                visibleLorebookGroups = listOf(lorebookGroup)
            ),
            emit = {}
        )
    }
}
