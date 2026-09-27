package com.example.juzzics.common.uiComponents.dragable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * LazyColumn whose items can be reordered: long-press an item, drag it, release.
 *  - the dragged item follows the finger, lifted above the others
 *  - the others slide out of its way (animated)
 *  - dragging near the top/bottom edge scrolls the list, faster the closer to the edge
 *  - on release it settles into its new place with a spring
 *
 * [key] must be unique and stable per item (e.g. the song id), it keeps rows from being
 * re-created when an item's contents change. [onDragEnd] gets the list in its new order.
 */
@Composable
fun <T : Any> ReorderableList(
    modifier: Modifier,
    list: SnapshotStateList<T>,
    lazyListState: LazyListState,
    key: (T) -> Any = { it },
    /** false: tap only, no long-press dragging (e.g. while the list is filtered or sorted) */
    reorderEnabled: Boolean = true,
    onDragEnd: (SnapshotStateList<T>) -> Unit,
    onClick: (T) -> Unit,
    content: @Composable (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentList by rememberUpdatedState(list)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val dragState = remember(lazyListState) {
        ReorderState(
            listState = lazyListState,
            scope = scope,
            haptics = haptics,
            edgePx = with(density) { 64.dp.toPx() },
            maxScrollStepPx = with(density) { 10.dp.toPx() },
        ) { from, to -> currentList.moveAt(from, to) }
    }
    dragState.onDragFinished = { currentOnDragEnd(currentList) }

    // auto-scroll while dragging near an edge, one step per frame
    LaunchedEffect(dragState) {
        for (delta in dragState.scrollRequests) {
            withFrameNanos { }
            lazyListState.scrollBy(delta)
            dragState.onScrolled()
        }
    }

    LazyColumn(
        modifier = if (!reorderEnabled) modifier else modifier.pointerInput(dragState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> dragState.onDragStart(offset) },
                onDrag = { change, offset ->
                    change.consume()
                    dragState.onDrag(offset)
                },
                onDragEnd = { dragState.onDragStop() },
                onDragCancel = { dragState.onDragStop() },
            )
        },
        state = lazyListState,
    ) {
        itemsIndexed(list, key = { _, item -> key(item) }) { index, item ->
            val itemModifier = when (index) {
                dragState.draggingIndex -> Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = dragState.draggingOffset
                        scaleX = 1.02f
                        scaleY = 1.02f
                        shadowElevation = 8.dp.toPx()
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                    }

                dragState.settlingIndex -> Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = dragState.settlingOffset.value }

                else -> Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
            }
            Column(
                modifier = itemModifier.clickable { onClick(item) }
            ) {
                content(item)
            }
        }
    }
}

@Stable
private class ReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    /** size of the "hot" area at each edge that scrolls the list */
    private val edgePx: Float,
    /** scroll per frame with the finger right at the edge */
    private val maxScrollStepPx: Float,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** item that was just dropped and is animating into place */
    var settlingIndex by mutableStateOf<Int?>(null)
        private set
    val settlingOffset = Animatable(0f)

    var onDragFinished: () -> Unit = {}

    val scrollRequests = Channel<Float>(Channel.CONFLATED)

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)
    private var moved = false
    private var lastDragDelta = 0f

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingIndex }

    /** how far the dragged item is drawn from where the list lays it out */
    val draggingOffset: Float
        get() = draggingItem?.let { initialOffset + draggedDistance - it.offset } ?: 0f

    fun onDragStart(touch: Offset) {
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { touch.y.toInt() in it.offset..(it.offset + it.size) } ?: return
        draggingIndex = item.index
        initialOffset = item.offset
        draggedDistance = 0f
        moved = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(delta: Offset) {
        if (draggingIndex == null) return
        draggedDistance += delta.y
        lastDragDelta = delta.y
        swapIfNeeded()
        requestEdgeScroll()
    }

    /** after an auto-scroll the item under the finger changed: check again */
    fun onScrolled() {
        if (draggingIndex == null) return
        swapIfNeeded()
        requestEdgeScroll()
    }

    fun onDragStop() {
        val index = draggingIndex ?: return
        val dropOffset = draggingOffset
        draggingIndex = null
        draggedDistance = 0f
        if (moved) onDragFinished()

        // settle into place from where it was dropped
        settlingIndex = index
        scope.launch {
            settlingOffset.snapTo(dropOffset)
            settlingOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (settlingIndex == index) settlingIndex = null
        }
    }

    private fun swapIfNeeded() {
        val item = draggingItem ?: return
        val top = item.offset + draggingOffset
        val middle = top + item.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != item.index && middle.toInt() in it.offset..(it.offset + it.size)
        } ?: return

        // moving the first visible item would make the list jump: keep the scroll position
        if (item.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        onMove(item.index, target.index)
        draggingIndex = target.index
        moved = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    private fun requestEdgeScroll() {
        val item = draggingItem ?: return
        val info = listState.layoutInfo
        val top = item.offset + draggingOffset
        val bottom = top + item.size
        val edge = edgePx
        val scroll = when {
            lastDragDelta >= 0 && bottom > info.viewportEndOffset - edge ->
                ((bottom - (info.viewportEndOffset - edge)) / edge).coerceIn(0f, 1f) * maxScrollStepPx
            lastDragDelta <= 0 && top < info.viewportStartOffset + edge ->
                -((info.viewportStartOffset + edge - top) / edge).coerceIn(0f, 1f) * maxScrollStepPx
            else -> 0f
        }
        // stop at the ends of the list
        val canScroll = if (scroll > 0) listState.canScrollForward else listState.canScrollBackward
        if (scroll != 0f && canScroll) scrollRequests.trySend(scroll)
    }
}

fun <T> MutableList<T>.moveAt(oldIndex: Int, newIndex: Int) {
    val item = this[oldIndex]
    removeAt(oldIndex)
    add(newIndex, item)
}
