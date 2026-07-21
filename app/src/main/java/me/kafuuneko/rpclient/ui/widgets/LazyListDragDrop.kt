package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 列表拖拽重排状态管理器
 */
class LazyListDragDropState internal constructor(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val isItemDraggable: (key: Any) -> Boolean,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit,
    private val onDragEnd: () -> Unit = {},
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    internal val draggingItemIndex: Int?
        get() = draggingItemKey?.let { key ->
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.index
        }

    internal val scrollChannel = Channel<Float>()

    private var mDraggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var mDraggingItemInitialOffset by mutableFloatStateOf(0f)

    internal val draggingItemOffset: Float
        get() = mDraggingItemLayoutInfo?.let { item ->
            mDraggingItemInitialOffset + mDraggingItemDraggedDelta - item.offset
        } ?: 0f

    private val mDraggingItemLayoutInfo: LazyListItemInfo?
        get() = draggingItemKey?.let { key ->
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        }

    internal var previousKeyOfDraggedItem by mutableStateOf<Any?>(null)
        private set

    internal var previousItemOffset = Animatable(Offset.Zero, Offset.VectorConverter)
        private set

    internal fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            offset.y.toInt() in item.offset..(item.offset + item.size)
        }?.takeIf { isItemDraggable(it.key) }?.also {
            draggingItemKey = it.key
            mDraggingItemInitialOffset = it.offset.toFloat()
            mDraggingItemDraggedDelta = 0f
        }
    }

    internal fun onDragInterrupted() {
        if (draggingItemKey != null) {
            onDragEnd()
            previousKeyOfDraggedItem = draggingItemKey
            val startOffset = draggingItemOffset
            scope.launch {
                previousItemOffset.snapTo(Offset(0f, startOffset))
                previousItemOffset.animateTo(
                    Offset.Zero,
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = Offset.VisibilityThreshold,
                    ),
                )
                previousKeyOfDraggedItem = null
            }
        }
        mDraggingItemDraggedDelta = 0f
        draggingItemKey = null
        mDraggingItemInitialOffset = 0f
    }

    internal fun onDrag(offset: Offset) {
        mDraggingItemDraggedDelta += offset.y

        val draggingItem = mDraggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset.toFloat() + draggingItemOffset
        val endOffset = startOffset + draggingItem.size

        val candidates = state.layoutInfo.visibleItemsInfo.filter { item ->
            isItemDraggable(item.key) && draggingItem.key != item.key
        }

        val targetItem = if (mDraggingItemDraggedDelta >= 0) {
            // 向下拖拽：寻找下方首个被拖拽项覆盖超过 20% 高度的 Item
            candidates
                .filter { it.index > draggingItem.index }
                .firstOrNull { item ->
                    val itemTop = item.offset.toFloat()
                    val threshold = item.size * 0.20f
                    endOffset > (itemTop + threshold)
                }
        } else {
            // 向上拖拽：寻找上方首个被拖拽项覆盖超过 20% 高度的 Item
            candidates
                .filter { it.index < draggingItem.index }
                .lastOrNull { item ->
                    val itemBottom = (item.offset + item.size).toFloat()
                    val threshold = item.size * 0.20f
                    startOffset < (itemBottom - threshold)
                }
        }

        if (targetItem != null) {
            if (
                draggingItem.index == state.firstVisibleItemIndex ||
                targetItem.index == state.firstVisibleItemIndex
            ) {
                state.requestScrollToItem(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                )
            }
            onMove(draggingItem.key, targetItem.key)
        } else {
            val overscroll = when {
                mDraggingItemDraggedDelta > 0 ->
                    (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                mDraggingItemDraggedDelta < 0 ->
                    (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) {
                scrollChannel.trySend(overscroll)
            }
        }
    }
}

@Composable
fun rememberLazyListDragDropState(
    lazyListState: LazyListState,
    isItemDraggable: (key: Any) -> Boolean = { true },
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    onDragEnd: () -> Unit = {},
): LazyListDragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        LazyListDragDropState(
            state = lazyListState,
            scope = scope,
            isItemDraggable = isItemDraggable,
            onMove = onMove,
            onDragEnd = onDragEnd
        )
    }
    LaunchedEffect(state) {
        while (true) {
            val diff = state.scrollChannel.receive()
            lazyListState.scrollBy(diff)
        }
    }
    return state
}

fun Modifier.dragContainer(dragDropState: LazyListDragDropState): Modifier {
    return pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDrag = { change, offset ->
                change.consume()
                dragDropState.onDrag(offset = offset)
            },
            onDragStart = { offset -> dragDropState.onDragStart(offset) },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() },
        )
    }
}

@Composable
fun LazyItemScope.DraggableItem(
    dragDropState: LazyListDragDropState,
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val dragging = key == dragDropState.draggingItemKey
    val draggingModifier = if (dragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = dragDropState.draggingItemOffset
            }
    } else if (key == dragDropState.previousKeyOfDraggedItem) {
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = dragDropState.previousItemOffset.value.y
            }
    } else {
        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }
    Box(modifier = modifier.then(draggingModifier), propagateMinConstraints = true) {
        content(dragging)
    }
}
