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

@Composable
fun LazyListState.shouldExpandFAB(): Boolean {
    return remember {
        derivedStateOf {
            (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) ||
                lastScrolledBackward ||
                !canScrollForward
        }
    }
        .value
}

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
fun LazyListState.isItemScrollingUp(initiallyVisible: Boolean = true): Boolean {
    var isScrolling by remember { mutableStateOf(initiallyVisible) }
    var previousIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { currentIndex ->
                if (previousIndex != currentIndex) {
                    isScrolling = previousIndex > currentIndex
                    previousIndex = currentIndex
                }
            }
    }

    return isScrolling
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

@Composable
fun LazyListState.isItemScrollingDown(initiallyVisible: Boolean = false): Boolean {
    var isScrolling by remember { mutableStateOf(initiallyVisible) }
    var previousIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { currentIndex ->
                if (previousIndex != currentIndex) {
                    isScrolling = previousIndex < currentIndex
                    previousIndex = currentIndex
                }
            }
    }

    return isScrolling
}
