package app.sigmatube.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a LazyColumn: the dragged row follows the finger and swaps with the row
 * under its centre. Only rows whose key is in the reorderable set take part.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit,
    private val canMoveTo: (Any) -> Boolean,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        draggingKey = key
        offset = 0f
    }

    fun drag(delta: Float) {
        val key = draggingKey ?: return
        offset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val center = current.offset + offset + current.size / 2f
        val target = items.firstOrNull {
            it.key != key && canMoveTo(it.key) && center > it.offset && center < it.offset + it.size
        } ?: return
        onMove(key, target.key)
        // The row now sits where the target was: keep it under the finger.
        offset -= (target.offset - current.offset)
    }

    fun end() {
        draggingKey = null
        offset = 0f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canMoveTo: (Any) -> Boolean,
    onMove: (fromKey: Any, toKey: Any) -> Unit,
): ReorderState {
    val move by rememberUpdatedState(onMove)
    val can by rememberUpdatedState(canMoveTo)
    return remember(listState) { ReorderState(listState, { a, b -> move(a, b) }, { can(it) }) }
}

/** Lifts the row being dragged above the others and moves it with the finger. */
fun Modifier.reorderable(state: ReorderState, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.offset else 0f
            shadowElevation = if (dragging) 12f else 0f
        }
}

/** Put on the drag handle of a row. */
fun Modifier.dragHandle(state: ReorderState, key: Any): Modifier = pointerInput(key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
    )
}
