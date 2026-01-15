package tachiyomi.presentation.core.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun LazyListState.shouldExpandFAB(): Boolean = lastScrolledBackward || !canScrollForward || !canScrollBackward

@Composable
fun LazyListState.isScrolledToStart(): Boolean {
    return remember {
        derivedStateOf {
            val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstItem == null || firstItem.offset == layoutInfo.viewportStartOffset
        }
    }.value
}

@Composable
fun LazyListState.isScrolledToEnd(): Boolean {
    return remember {
        derivedStateOf {
            val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastItem == null || lastItem.size + lastItem.offset <= layoutInfo.viewportEndOffset
        }
    }.value
}

@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

@Composable
fun LazyListState.isScrollingDown(): Boolean {
    var previousIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex < firstVisibleItemIndex
            } else {
                previousScrollOffset <= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

// KMK -->
@Composable
private fun LazyListState.isItemScrolling(
    initialValue: Boolean,
    comparison: (Int, Int) -> Boolean,
): Boolean {
    var isScrolling by remember { mutableStateOf(initialValue) }
    var previousIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { currentIndex ->
                if (previousIndex != currentIndex) {
                    isScrolling = comparison(previousIndex, currentIndex)
                    previousIndex = currentIndex
                }
            }
    }

    return isScrolling
}

@Composable
fun LazyListState.isItemScrollingUp(initiallyVisible: Boolean = true): Boolean {
    return isItemScrolling(initialValue = initiallyVisible) { previous, current -> previous > current }
}

@Composable
fun LazyListState.isItemScrollingDown(initiallyVisible: Boolean = false): Boolean {
    return isItemScrolling(initialValue = initiallyVisible) { previous, current -> previous < current }
}
// KMK <--
