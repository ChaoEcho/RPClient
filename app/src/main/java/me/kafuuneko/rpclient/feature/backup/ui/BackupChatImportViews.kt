package me.kafuuneko.rpclient.feature.backup.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.backup.presentation.BackupDialogState
import me.kafuuneko.rpclient.feature.backup.presentation.BackupUiIntent
import me.kafuuneko.rpclient.feature.backup.presentation.ImportCharacterItem
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.RpAvatar

/** 解析成功后强制用户确认目标角色卡的导入对话框。 */
@Composable
internal fun ImportChatCharacterDialog(
    state: BackupDialogState.ImportChatCharacterSelection,
    emit: BackupUiIntent.() -> Unit
) {
    val canDismiss = !state.isImporting
    AppDialogScaffold(
        onDismissRequest = {
            if (canDismiss) BackupUiIntent.DismissDialog.emit()
        },
        title = stringResource(R.string.select_import_character_title),
        badgeIcon = Icons.Rounded.FileDownload,
        badgeTone = DialogBadgeTone.Primary,
        confirmText = stringResource(
            if (state.isImporting) R.string.importing_chat else R.string.import_chat
        ),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = state.selectedCharacterId != null && !state.isImporting,
        isConfirmLoading = state.isImporting,
        onConfirm = { BackupUiIntent.ConfirmImportChat.emit() },
        onDismiss = {
            if (canDismiss) BackupUiIntent.DismissDialog.emit()
        }
    ) {
        ImportCharacterSelectionContent(state, emit)
    }
}

@Composable
private fun ImportCharacterSelectionContent(
    state: BackupDialogState.ImportChatCharacterSelection,
    emit: BackupUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ImportPreview(state)
        if (state.characters.isEmpty()) {
            Text(
                text = stringResource(R.string.no_characters_for_chat_import),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { BackupUiIntent.ChangeImportCharacterQuery(it).emit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isImporting,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_characters),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        if (state.visibleCharacters.isEmpty()) {
            Text(
                text = stringResource(R.string.no_matching_characters),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.visibleCharacters, key = { it.id }) { character ->
                    ImportCharacterItem(
                        character = character,
                        selected = character.id == state.selectedCharacterId,
                        enabled = !state.isImporting,
                        onClick = {
                            BackupUiIntent.SelectImportCharacter(character.id).emit()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPreview(state: BackupDialogState.ImportChatCharacterSelection) {
    val sourceCharacterName = if (state.sourceCharacterName.isBlank()) {
        stringResource(R.string.unknown_character)
    } else {
        state.sourceCharacterName
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(
                R.string.import_chat_source_character,
                sourceCharacterName
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = pluralStringResource(
                R.plurals.import_chat_message_count,
                state.messageCount,
                state.messageCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportCharacterItem(
    character: ImportCharacterItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(selected = selected, enabled = enabled, onClick = onClick)
            RpAvatar(
                text = character.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = getMacaronColor(character.name.ifBlank { "character" }),
                modifier = Modifier.size(38.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (character.details.isNotBlank()) {
                    Text(
                        text = character.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
