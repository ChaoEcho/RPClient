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
import androidx.compose.runtime.rememberUpdatedState
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
 * 列表拖拽重排状态管理器。
 *
 * 使用稳定 item key 而非可变索引追踪拖拽项，列表在移动回调后立即重排时仍能找到
 * 当前项目；拖拽结束回调只在一次手势结束或取消时触发。
 */
class LazyListDragDropState internal constructor(
    private val mState: LazyListState,
    private val mScope: CoroutineScope,
    private val mIsItemDraggable: (key: Any) -> Boolean,
    private val mOnMove: (fromKey: Any, toKey: Any) -> Unit,
    private val mOnDragEnd: () -> Unit = {},
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    internal val draggingItemIndex: Int?
        get() = draggingItemKey?.let { key ->
            mState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.index
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
            mState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        }

    internal var previousKeyOfDraggedItem by mutableStateOf<Any?>(null)
        private set

    internal var previousItemOffset = Animatable(Offset.Zero, Offset.VectorConverter)
        private set

    internal fun onDragStart(offset: Offset) {
        mState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            offset.y.toInt() in item.offset..(item.offset + item.size)
        }?.takeIf { mIsItemDraggable(it.key) }?.also {
            draggingItemKey = it.key
            mDraggingItemInitialOffset = it.offset.toFloat()
            mDraggingItemDraggedDelta = 0f
        }
    }

    internal fun onDragInterrupted() {
        if (draggingItemKey != null) {
            mOnDragEnd()
            previousKeyOfDraggedItem = draggingItemKey
            val startOffset = draggingItemOffset
            mScope.launch {
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

        val candidates = mState.layoutInfo.visibleItemsInfo.filter { item ->
            mIsItemDraggable(item.key) && draggingItem.key != item.key
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
                draggingItem.index == mState.firstVisibleItemIndex ||
                targetItem.index == mState.firstVisibleItemIndex
            ) {
                mState.requestScrollToItem(
                    mState.firstVisibleItemIndex,
                    mState.firstVisibleItemScrollOffset,
                )
            }
            mOnMove(draggingItem.key, targetItem.key)
        } else {
            val overscroll = when {
                mDraggingItemDraggedDelta > 0 ->
                    (endOffset - mState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                mDraggingItemDraggedDelta < 0 ->
                    (startOffset - mState.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) {
                scrollChannel.trySend(overscroll)
            }
        }
    }
}

/**
 * 创建并记住与指定 [LazyListState] 绑定的拖拽状态。
 *
 * 回调通过最新值包装，避免重组后拖拽状态继续捕获旧闭包；返回值不能跨列表复用。
 */
@Composable
fun rememberLazyListDragDropState(
    lazyListState: LazyListState,
    isItemDraggable: (key: Any) -> Boolean = { true },
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    onDragEnd: () -> Unit = {},
): LazyListDragDropState {
    val scope = rememberCoroutineScope()
    val currentIsItemDraggable = rememberUpdatedState(isItemDraggable)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val state = remember(lazyListState) {
        LazyListDragDropState(
            mState = lazyListState,
            mScope = scope,
            mIsItemDraggable = { key -> currentIsItemDraggable.value(key) },
            mOnMove = { fromKey, toKey -> currentOnMove.value(fromKey, toKey) },
            mOnDragEnd = { currentOnDragEnd.value() }
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

/** 为列表容器安装长按拖拽手势，并把结束与取消统一交给状态管理器收尾。 */
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

/**
 * 为具有稳定 [key] 的 Lazy 列表项应用拖拽位移和归位动画。
 *
 * [key] 必须与 Lazy 列表声明的 key 一致，否则重排后无法继续追踪同一项目。
 */
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
