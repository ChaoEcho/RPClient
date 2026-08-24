package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme

data class StoryChapterDestinationOption(
    val volumeId: Long?,
    val title: String
)

/** 选择章节所属分卷并确认移动的对话框。 */
@Composable
fun StoryMoveChapterDialog(
    onDismissRequest: () -> Unit,
    chapterTitle: String,
    options: List<StoryChapterDestinationOption>,
    selectedVolumeId: Long?,
    isSaving: Boolean,
    onDestinationSelected: (Long?) -> Unit,
    onConfirm: () -> Unit
) {
    AppConfirmDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.story_move_chapter),
        badgeIcon = Icons.Rounded.FolderOpen,
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = !isSaving,
        isConfirmLoading = isSaving,
        onConfirm = onConfirm
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            options.forEach { option ->
                val selected = selectedVolumeId == option.volumeId
                FilterChip(
                    selected = selected,
                    onClick = { onDestinationSelected(option.volumeId) },
                    enabled = !isSaving,
                    label = { Text(option.title) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Preview(name = "StoryMoveChapterDialog", showBackground = true)
@Composable
private fun StoryMoveChapterDialogPreview() {
    AppTheme(dynamicColor = false) {
        StoryMoveChapterDialog(
            onDismissRequest = {},
            chapterTitle = "第一章",
            options = listOf(
                StoryChapterDestinationOption(null, "未分卷章节"),
                StoryChapterDestinationOption(1L, "第一卷"),
                StoryChapterDestinationOption(2L, "第二卷")
            ),
            selectedVolumeId = 1L,
            isSaving = false,
            onDestinationSelected = {},
            onConfirm = {}
        )
    }
}
