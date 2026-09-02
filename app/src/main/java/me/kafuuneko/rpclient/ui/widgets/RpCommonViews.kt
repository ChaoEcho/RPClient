package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kafuuneko.rpclient.R

/** 页面主标题，带辅助说明，统一页面顶部视觉规范。 */
@Composable
fun RpPageTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}

/** 内容区段标题，右侧支持快捷操作入口。 */
@Composable
fun RpSectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    action,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 项目统一的文字占位头像，采用现代化柔和 Squircle 圆角与微弱高光边框。 */
@Composable
fun RpAvatar(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    Surface(
        modifier = modifier.size(46.dp),
        shape = shape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.28f))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 用于列表项或卡片的微光圆角图标背景。 */
@Composable
fun RpIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.size(38.dp),
        shape = RoundedCornerShape(13.dp),
        color = containerColor,
        border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.15f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 可自动换行的标签集合。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpTagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxCount: Int = tags.size
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.take(maxCount).forEach { tag ->
            RpTagPill(tag)
        }
    }
}

/** 单个强调标签，采用胶囊结构与轻量填充，去除粗糙黑边。 */
@Composable
fun RpTagPill(text: String) {
    Surface(
        modifier = Modifier.widthIn(max = 240.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 可自动换行的弱化元信息集合。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpMetaRow(items: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            RpMetaPill(item)
        }
    }
}

/** 单个弱化元信息标签。 */
@Composable
fun RpMetaPill(text: String) {
    Surface(
        modifier = Modifier.widthIn(max = 240.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 带图标、标题和可选操作区的现代化信息卡片。 */
@Composable
fun RpInfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        )
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                )
            }
            trailing?.invoke()
        }
    }
}

/** 使用项目统一圆角与背景色的内容面板。 */
@Composable
fun RpPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

/** 现代分组设置卡片容器，统一设置页卡片层次规范。 */
@Composable
fun RpSettingsGroup(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/** 分组卡片内部的浅色内嵌分割线。 */
@Composable
fun RpSettingsDivider(
    modifier: Modifier = Modifier,
    startIndent: Boolean = true
) {
    HorizontalDivider(
        modifier = modifier.padding(
            start = if (startIndent) 58.dp else 16.dp,
            end = 16.dp
        ),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    )
}

/** 现代设置页标准列表行项，支持多彩微光图标、主副标题与尾部扩展。 */
@Composable
fun RpSettingsTile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null && enabled) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            RpIconBubble(
                icon = icon,
                contentColor = if (enabled) iconColor else iconColor.copy(alpha = 0.38f),
                containerColor = if (enabled) iconContainerColor else iconContainerColor.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                    }
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * 设置页跳转箭头。
 *
 * 各页面此前反复手写同一段 trailing lambda（仅 MainLayout 就有 11 处），
 * 收敛成一个组件以保证尺寸与透明度一致。
 */
@Composable
fun RpNavigationChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
        modifier = Modifier.size(18.dp)
    )
}

/** 现代设置页带 Switch 开关的标准行项。 */
@Composable
fun RpSettingsSwitchTile(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
) {
    RpSettingsTile(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        iconContainerColor = iconContainerColor,
        enabled = enabled,
        contentPadding = contentPadding,
        modifier = modifier,
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

/** 现代设置页带数值标签与跳转箭头的标准行项。 */
@Composable
fun RpSettingsValueTile(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
) {
    RpSettingsTile(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        iconContainerColor = iconContainerColor,
        enabled = enabled,
        contentPadding = contentPadding,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

/** 项目统一的小号垂直间距。 */
@Composable
fun RpSpacerSmall() {
    Spacer(modifier = Modifier.height(8.dp))
}

/** 故事创建/设置中用于注入用户人设的现代化卡片。 */
@Composable
fun StoryUserPersonaCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) {
                onCheckedChange(!checked)
            },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RpIconBubble(
                icon = Icons.Rounded.Person,
                containerColor = if (checked) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                },
                contentColor = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.story_include_user_persona),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.story_include_user_persona_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}



/**
 * 现代通用可折叠设置分组卡片，支持旋转箭头、副标题、概览元信息与内部表单折叠展开。
 *
 * @param title 标题
 * @param subtitle 副标题
 * @param summary 摘要元信息文本（显示在右侧胶囊）
 * @param icon 左侧图标
 * @param iconColor 图标前景色
 * @param iconContainerColor 图标背景色
 * @param initiallyExpanded 初始展开状态
 * @param contentPadding 展开内容区域的内边距
 * @param content 展开区域的内容插槽
 */
@Composable
fun RpCollapsibleSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    summary: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    initiallyExpanded: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    RpIconBubble(
                        icon = icon,
                        contentColor = iconColor,
                        containerColor = iconContainerColor
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
                if (!summary.isNullOrBlank()) {
                    RpMetaPill(text = summary)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    content()
                }
            }
        }
    }
}

/**
 * 设置页统一表单输入框。
 *
 * 此前图片、语音、备份、模型编辑四个页面各自实现了一份，圆角在 12/14/16dp 之间漂移，
 * 且只有部分页面带密码可见性切换与键盘弹出时的滚动跟随。这里收敛为唯一实现。
 */
@Composable
fun RpFormTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // 输入框获得焦点后软键盘才完成弹出，延迟一帧再滚动才能真正把它带到可见区域。
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(FIELD_SCROLL_INTO_VIEW_DELAY_MS)
            bringIntoViewRequester.bringIntoView()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { isFocused = it.isFocused },
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Rounded.VisibilityOff
                        } else {
                            Icons.Rounded.Visibility
                        },
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.hide else R.string.show
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = RoundedCornerShape(FIELD_CORNER_RADIUS)
    )
}

/**
 * 设置页统一下拉选择框，外观与 [RpFormTextField] 完全一致。
 *
 * [selectedLabel] 由调用方直接给出，因此“跟随聊天模型”这类不对应任何列表项的占位值
 * 不需要在 [values] 里造一个假条目。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RpSettingsDropdown(
    label: String,
    selectedLabel: String,
    values: List<T>,
    valueLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingOption: Pair<String, () -> Unit>? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            maxLines = 1,
            shape = RoundedCornerShape(FIELD_CORNER_RADIUS)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (leadingOption != null) {
                DropdownMenuItem(
                    text = { Text(leadingOption.first) },
                    onClick = {
                        leadingOption.second()
                        expanded = false
                    }
                )
            }
            values.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = valueLabel(value),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 折叠分组内部使用的磁贴内边距。
 *
 * 分组本身已提供水平内边距，磁贴只需保留垂直节奏，否则会与相邻的表单行左边缘错开。
 */
val RpGroupedTilePadding = PaddingValues(horizontal = 0.dp, vertical = 13.dp)

/** 设置页表单控件统一圆角；改这里即可整体调整，不要在各页面各写一遍。 */
private val FIELD_CORNER_RADIUS = 12.dp
private const val FIELD_SCROLL_INTO_VIEW_DELAY_MS = 300L

/**
 * 通用数字输入行组件。
 */
@Composable
fun RpNumberSettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (!helper.isNullOrBlank()) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            // 数字设置项此前弹的是字母键盘。
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.width(100.dp),
            shape = RoundedCornerShape(FIELD_CORNER_RADIUS),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}


/**
 * 现代通用空状态占位提示组件。
 */
@Composable
fun RpEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
