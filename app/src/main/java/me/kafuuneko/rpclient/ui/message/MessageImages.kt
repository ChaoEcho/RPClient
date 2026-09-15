package me.kafuuneko.rpclient.ui.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.ui.dialog.MessageImageViewerDialog

/**
 * 图片条只渲染状态并发出用户行为；缩略图由 ViewModel 按可见项加载。
 *
 * @param ids 按显示顺序排列的原图 ID。
 * @param state 当前页面的图片状态。
 * @param editable 是否显示附件编辑操作。
 * @param editing 是否属于历史消息编辑区。
 * @param enabled 页面是否允许修改附件。
 * @param emit 图片操作的意图回调。
 */
@Composable
fun MessageImageStrip(
    ids: List<String>, state: MessageImageState, editable: Boolean = false,
    editing: Boolean = false, enabled: Boolean = true, emit: (MessageImageAction) -> Unit
) {
    if (ids.isEmpty() && !editable) return
    val currentEmit by rememberUpdatedState(emit)
    Column {
        if (editable) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { emit(MessageImageAction.Choose(editing)) },
                enabled = enabled && !state.processing && (if (editing) state.canAddEditing else state.canAddDraft)
            ) {
                Text(stringResource(R.string.attach_images))
            }
            if (state.processing) {
                CircularProgressIndicator(Modifier.size(20.dp))
                TextButton(onClick = { emit(MessageImageAction.CancelProcessing) }) {
                    Text(
                        stringResource(R.string.cancel)
                    )
                }
            }
        }
        // 历史图片按实际缩略图宽度占位，不能把纯文字气泡或单张图片气泡撑满整行。
        val stripModifier = if (editable) Modifier.fillMaxWidth() else Modifier
        Row(
            stripModifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ids.forEachIndexed { index, uuid ->
                DisposableEffect(uuid) {
                    currentEmit(MessageImageAction.RegisterDisplay(uuid))
                    onDispose { currentEmit(MessageImageAction.ReleaseDisplay(uuid)) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(88.dp)
                            .clickable { emit(MessageImageAction.Preview(ids, index)) },
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = state.thumbnails[uuid]
                        if (bitmap != null) Image(
                            bitmap,
                            stringResource(R.string.message_image),
                            Modifier.size(88.dp),
                            contentScale = ContentScale.Fit
                        )
                        else Text(
                            stringResource(if (state.thumbnails.containsKey(uuid)) R.string.image_missing else R.string.image_loading),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (editable) Row {
                        TextButton(
                            onClick = { emit(MessageImageAction.Move(uuid, editing)) },
                            enabled = enabled && index > 0 && !state.processing
                        ) { Text("←") }
                        TextButton(
                            onClick = { emit(MessageImageAction.Remove(uuid, editing)) },
                            enabled = enabled && !state.processing
                        ) { Text("×") }
                    }
                }
            }
        }
        if (editable) state.errorResId?.let {
            Text(
                stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}