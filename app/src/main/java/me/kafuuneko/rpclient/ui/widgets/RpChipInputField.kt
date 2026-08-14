package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog

/**
 * 现代流式标签/关键词胶囊输入组件。
 *
 * 支持快速添加、逗号/空格/换行自动切分批量添加、点选修改以及一键移除标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpChipInputField(
    title: String,
    chips: List<String>,
    onChipsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    addLabel: String = stringResource(R.string.add),
    placeholder: String = stringResource(R.string.tag_input_placeholder),
    editDialogTitle: String = stringResource(R.string.edit_tag_title),
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    // 过滤掉空字符串后的实际显示列表
    val cleanChips = remember(chips) { chips.filter { it.isNotBlank() } }
    var isInputting by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 正在编辑中的单条标签下标（null 表示无弹窗）
    var editingChipIndex by remember { mutableStateOf<Int?>(null) }

    fun commitCurrentInput() {
        val raw = inputText.trim()
        if (raw.isNotBlank()) {
            val additions = raw.split(Regex("[,，\\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (additions.isNotEmpty()) {
                val newChips = (cleanChips + additions).distinct()
                onChipsChanged(newChips)
            }
        }
        inputText = ""
        isInputting = false
    }

    RpSettingsGroup(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 头部标题与统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    RpIconBubble(
                        icon = icon,
                        containerColor = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.20f))
                ) {
                    Text(
                        text = stringResource(R.string.chip_count, cleanChips.size),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }

            // 流式胶囊展示区
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cleanChips.forEachIndexed { index, chipText ->
                    InputChip(
                        selected = false,
                        onClick = {
                            if (enabled) {
                                editingChipIndex = index
                            }
                        },
                        label = {
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = if (enabled) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val updated = cleanChips.toMutableList()
                                            if (index in updated.indices) {
                                                updated.removeAt(index)
                                                onChipsChanged(updated)
                                            }
                                        }
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(10.dp),
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = accentColor.copy(alpha = 0.08f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            enabled = enabled,
                            selected = false,
                            borderColor = accentColor.copy(alpha = 0.22f),
                            borderWidth = 0.5.dp
                        )
                    )
                }

                // 添加按钮胶囊
                if (enabled && !isInputting) {
                    AssistChip(
                        onClick = { isInputting = true },
                        label = {
                            Text(
                                text = addLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = addLabel,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            borderWidth = 0.5.dp
                        )
                    )
                }
            }

            // 内联输入展开框
            AnimatedVisibility(
                visible = isInputting,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { value ->
                            if (value.endsWith(",") || value.endsWith("，") || value.endsWith("\n")) {
                                val splitCandidates = value.split(Regex("[,，\\n]+"))
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                if (splitCandidates.isNotEmpty()) {
                                    val newChips = (cleanChips + splitCandidates).distinct()
                                    onChipsChanged(newChips)
                                }
                                inputText = ""
                            } else {
                                inputText = value
                            }
                        },
                        placeholder = {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitCurrentInput() }),
                        trailingIcon = {
                            if (inputText.isNotBlank()) {
                                IconButton(onClick = { commitCurrentInput() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.confirm),
                                        tint = accentColor
                                    )
                                }
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            commitCurrentInput()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    // 单条修改弹窗
    val targetIndex = editingChipIndex
    var editingDraft by remember(targetIndex) {
        mutableStateOf(targetIndex?.let { cleanChips.getOrNull(it) }.orEmpty())
    }

    if (targetIndex != null && targetIndex in cleanChips.indices) {
        AppInputDialog(
            onDismissRequest = { editingChipIndex = null },
            title = editDialogTitle,
            value = editingDraft,
            onValueChange = { editingDraft = it },
            singleLine = true,
            confirmText = stringResource(R.string.save),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val trimmed = editingDraft.trim()
                val list = cleanChips.toMutableList()
                if (trimmed.isBlank()) {
                    list.removeAt(targetIndex)
                } else {
                    list[targetIndex] = trimmed
                }
                onChipsChanged(list)
                editingChipIndex = null
            }
        )
    }
}
