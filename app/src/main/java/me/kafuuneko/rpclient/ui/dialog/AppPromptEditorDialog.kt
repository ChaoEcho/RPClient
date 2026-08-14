package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import androidx.compose.ui.text.input.VisualTransformation
import me.kafuuneko.rpclient.utils.rememberPromptMacroVisualTransformation

/**
 * 通用提示词 / 结构化文本编辑器对话框。
 *
 * 支持实时字数与 Token 估算、宏占位符语法高亮、一键清空、复制到剪贴板、恢复默认预设、以及宏变量快速插入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppPromptEditorDialog(
    onDismissRequest: () -> Unit,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeIcon: ImageVector = Icons.Rounded.AutoAwesome,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    defaultValue: String? = null,
    availableMacros: List<String> = emptyList(),
    editorHeightMin: Dp = 180.dp,
    editorHeightMax: Dp = 320.dp,
    placeholder: String = stringResource(R.string.prompt_editor_placeholder),
    visualTransformation: VisualTransformation = rememberPromptMacroVisualTransformation(),
    confirmText: String = stringResource(R.string.save),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    val charCount = value.length
    val estimatedTokens = (charCount / 3.5).toInt().coerceAtLeast(0)

    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        badgeIcon = badgeIcon,
        badgeTone = badgeTone,
        compactHeader = true,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        isConfirmLoading = isConfirmLoading,
        onConfirm = onConfirm,
        onDismiss = onDismissRequest
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶部元数据胶囊与快捷操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 字符数与 Token 统计
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = stringResource(R.string.prompt_editor_char_count, charCount, estimatedTokens),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 快捷操作动作组：清空、复制、恢复默认
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (value.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onValueChange("")
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.prompt_editor_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                clipboardManager.setText(AnnotatedString(value))
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!defaultValue.isNullOrBlank() && value != defaultValue) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange(defaultValue)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.prompt_editor_restore_default),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 编辑器视口容器 (沉浸式卡片 + 细微高光描边)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = editorHeightMin, max = editorHeightMax)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        ),
                        visualTransformation = visualTransformation,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // 底部宏占位符快速插入栏
            if (availableMacros.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.prompt_editor_macro_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableMacros.forEach { macro ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val newText = if (value.isEmpty()) {
                                            macro
                                        } else if (value.endsWith(" ")) {
                                            value + macro
                                        } else {
                                            "$value $macro"
                                        }
                                        onValueChange(newText)
                                    }
                            ) {
                                Text(
                                    text = macro,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "AppPromptEditorDialog Preview", showBackground = true)
@Composable
private fun AppPromptEditorDialogPreview() {
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppPromptEditorDialog(
                onDismissRequest = {},
                title = "主提示词",
                subtitle = "普通对话生成时的全局核心系统指令",
                value = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.",
                defaultValue = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.\nWrite one reply only.",
                availableMacros = listOf("{{char}}", "{{user}}"),
                onValueChange = {},
                onConfirm = {}
            )
        }
    }
}
