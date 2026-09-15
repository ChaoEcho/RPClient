package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.media.ImagePreviewState
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState

/**
 * 原图查看器对话框，展示当前图片并发出切换、关闭和保存行为。
 *
 * 核心设计与交互：
 * - 沉浸式全屏暗色背景，突出图片色彩与细节。
 * - 多维度缩放交互：双击平滑放大与复原、双指捏合缩放平移、单指轻触显隐操作面板。
 * - 侧边悬浮箭头与底部缩略图联动，支持在多图之间快速定位切换。
 * - 结构化的原图与发送预览分段切换器。
 *
 * @param state 当前页面的图片状态；没有预览时不显示对话框。
 * @param emit 图片操作的意图回调。
 */
@Composable
fun MessageImageViewerDialog(
    state: MessageImageState,
    emit: (MessageImageAction) -> Unit
) {
    // 守卫校验当前预览状态，无预览数据时不展开对话框
    val preview = state.preview ?: return
    // 维护控件浮层显隐状态，默认展示
    var controlsVisible by remember { mutableStateOf(true) }

    // 创建全屏沉浸式对话框容器
    Dialog(
        onDismissRequest = { emit(MessageImageAction.ClosePreview) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // 承载图片主画布与手势响应，点击空白或图片切换浮层显隐
            ImageViewerCanvas(
                preview = preview,
                onToggleControls = { controlsVisible = !controlsVisible }
            )

            // 渲染左右两侧悬浮快速切换箭头（仅在多图场景呈现）
            AnimatedVisibility(
                visible = controlsVisible && preview.ids.size > 1,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ImageViewerSideNavigation(
                    preview = preview,
                    emit = emit
                )
            }

            // 渲染顶部关闭按钮、页码指示胶囊与快捷保存动作
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ImageViewerTopBar(
                    preview = preview,
                    onClose = { emit(MessageImageAction.ClosePreview) },
                    onSave = { emit(MessageImageAction.Save) }
                )
            }

            // 渲染底部缩略图导航行与版本/保存操作胶囊栏
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (preview.ids.size > 1) {
                        ImageViewerThumbnailStrip(
                            preview = preview,
                            thumbnails = state.thumbnails,
                            onSelect = { selectedIndex ->
                                emit(
                                    MessageImageAction.Preview(
                                        preview.ids,
                                        selectedIndex,
                                        preview.sendVersion
                                    )
                                )
                            }
                        )
                    }
                    ImageViewerBottomBar(
                        preview = preview,
                        emit = emit
                    )
                }
            }
        }
    }
}

/**
 * 兼容旧命名调用的向后兼容别名。
 *
 * @param state 当前页面的图片状态。
 * @param emit 图片操作的意图回调。
 */
@Composable
fun MessageImageViewer(
    state: MessageImageState,
    emit: (MessageImageAction) -> Unit
) {
    MessageImageViewerDialog(state, emit)
}

/**
 * 原图查看器顶部导航栏，集成退出、计数指示与快捷保存动作。
 *
 * @param preview 当前查看器的图片及加载状态。
 * @param onClose 点击关闭预览的回调。
 * @param onSave 点击保存图片的回调。
 */
@Composable
private fun ImageViewerTopBar(
    preview: ImagePreviewState,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    // 适配状态栏安全边距并水平铺满
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 布局左侧圆形半透明退出按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.image_close),
                tint = Color.White
            )
        }

        // 布局中央当前图片序号指示胶囊
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Text(
                text = "${preview.index + 1} / ${preview.ids.size}",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        // 布局右侧圆形半透明保存快捷按钮
        IconButton(
            onClick = onSave,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Rounded.FileDownload,
                contentDescription = stringResource(R.string.image_save),
                tint = Color.White
            )
        }
    }
}

/**
 * 原图手势交互画布，支持双指缩放、平移拖拽与双击放大复原。
 *
 * @param preview 当前查看器的图片及加载状态。
 * @param onToggleControls 单击空白或图片区域显隐操作层的回调。
 */
@Composable
private fun ImageViewerCanvas(
    preview: ImagePreviewState,
    onToggleControls: () -> Unit
) {
    // 切换图片序列、索引或发送版本时重设缩放与位移，异步加载位图完成时不重置手势
    var scale by remember(preview.ids, preview.index, preview.sendVersion) { mutableFloatStateOf(1f) }
    var offset by remember(preview.ids, preview.index, preview.sendVersion) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 绑定轻触与双击手势
            .pointerInput(preview.ids, preview.index, preview.sendVersion) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    },
                    onTap = {
                        onToggleControls()
                    }
                )
            }
            // 绑定双指缩放与拖拽平移手势
            .pointerInput(preview.ids, preview.index, preview.sendVersion) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                    scale = newScale
                    if (newScale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 根据当前位图状态呈现大图、加载进度或缺失占位
        val bitmap = preview.bitmap
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.message_image),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit
                )
            }
            preview.loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = stringResource(R.string.image_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.image_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * 屏幕两侧悬浮切换按钮，用于快速前后切换图片。
 *
 * @param preview 当前查看器的图片及加载状态。
 * @param emit 图片操作的意图回调。
 */
@Composable
private fun ImageViewerSideNavigation(
    preview: ImagePreviewState,
    emit: (MessageImageAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 布局左侧上一张悬浮箭头按钮
        if (preview.index > 0) {
            IconButton(
                onClick = {
                    emit(MessageImageAction.Preview(preview.ids, preview.index - 1, preview.sendVersion))
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        // 布局右侧下一张悬浮箭头按钮
        if (preview.index < preview.ids.lastIndex) {
            IconButton(
                onClick = {
                    emit(MessageImageAction.Preview(preview.ids, preview.index + 1, preview.sendVersion))
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * 底部多图微缩图导航栏，点击对应微缩项即可无缝跳转至目标图片。
 *
 * @param preview 当前查看器的图片及加载状态。
 * @param thumbnails 已加载的缩略图缓存映射。
 * @param onSelect 选中目标索引的回调。
 */
@Composable
private fun ImageViewerThumbnailStrip(
    preview: ImagePreviewState,
    thumbnails: Map<String, ImageBitmap?>,
    onSelect: (Int) -> Unit
) {
    // 渲染水平滚动的缩略图列表
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 遍历消息中的所有图片项
        preview.ids.forEachIndexed { index, uuid ->
            val isCurrent = index == preview.index
            // 为当前展示图片施加主色外框
            val borderStroke = if (isCurrent) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(borderStroke, RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                val thumbnail = thumbnails[uuid]
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (isCurrent) 1f else 0.6f },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * 底部操作胶囊控制栏，集成原图/发送预览分段切换器与保存主动作。
 *
 * @param preview 当前查看器的图片及加载状态。
 * @param emit 图片操作的意图回调。
 */
@Composable
private fun ImageViewerBottomBar(
    preview: ImagePreviewState,
    emit: (MessageImageAction) -> Unit
) {
    // 布局半透明毛玻璃底栏卡片
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 渲染版本切换分段指示器
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VersionSegmentButton(
                    title = stringResource(R.string.image_original),
                    selected = !preview.sendVersion,
                    onClick = {
                        if (preview.sendVersion) {
                            emit(MessageImageAction.Preview(preview.ids, preview.index, sendVersion = false))
                        }
                    }
                )
                VersionSegmentButton(
                    title = stringResource(R.string.image_send_preview),
                    selected = preview.sendVersion,
                    onClick = {
                        if (!preview.sendVersion) {
                            emit(MessageImageAction.Preview(preview.ids, preview.index, sendVersion = true))
                        }
                    }
                )
            }

            // 渲染保存图片主操作按钮
            Button(
                onClick = { emit(MessageImageAction.Save) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.image_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * 版本分段选择按钮，高亮呈现当前激活状态。
 *
 * @param title 按钮文本。
 * @param selected 是否处于选中状态。
 * @param onClick 点击事件回调。
 */
@Composable
private fun VersionSegmentButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 渲染分段选择胶囊容器并响应点击
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // 展示版本标题文字并根据选中状态调整字重与颜色
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f)
        )
    }
}
