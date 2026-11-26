package app.sigmatube.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Fires each time the bottom-bar icon of the tab being shown is tapped again. */
val LocalTabReselect = compositionLocalOf<Flow<Unit>> { emptyFlow() }

/**
 * Tapping the current tab's icon again: back to the top of the list if it is scrolled,
 * otherwise [onRefresh] (when the page can refresh).
 */
@Composable
fun TabReselectEffect(listState: LazyListState, onRefresh: (() -> Unit)? = null) {
    val events = LocalTabReselect.current
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(events, listState) {
        events.collect {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                // Far down a long feed: jump close first so the animation stays short.
                if (listState.firstVisibleItemIndex > 8) listState.scrollToItem(8)
                listState.animateScrollToItem(0)
            } else {
                refresh?.invoke()
            }
        }
    }
}
