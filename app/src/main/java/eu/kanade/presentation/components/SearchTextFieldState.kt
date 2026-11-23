package eu.kanade.presentation.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest

/**
 * A [TextFieldState] kept in two-way sync with an external search [query], so a state-based
 * `BasicTextField` (which, unlike the value/onValueChange one, lets the user drag the cursor and
 * selection handles) can be driven by a screen model's query.
 *
 * - Every edit is sent to [onQueryChange] right away, without debouncing, like the
 *   value/onValueChange field did; screen models that need a debounce already apply their own.
 * - When [query] changes to a value this field did not send (a tag/genre tapped to search, a
 *   deep link, a filter clearing the search, closing the search with `null`...), the field text
 *   is replaced by it. Echoes of values this field sent are ignored, so the screen model catching
 *   up late never overwrites what the user typed in the meantime.
 * - `null` and empty [query] both mean an empty field, and an empty field never sends a change
 *   while [query] is `null`, so it doesn't reopen a closed search.
 */
@Composable
fun rememberSearchTextFieldState(
    query: String?,
    onQueryChange: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(query.orEmpty())
    val currentQuery by rememberUpdatedState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    // Values sent to onQueryChange that haven't come back through `query` yet, oldest first.
    // Only touched from the main thread (composition and effects), so it needn't be snapshot state.
    val pendingQueries = remember { ArrayDeque<String>() }

    // Declared before the collector below so it runs first and wins on the initial composition.
    LaunchedEffect(query) {
        val newQuery = query.orEmpty()
        if (state.text.toString() == newQuery) {
            // Caller and field agree, so nothing sent earlier can still be in flight.
            pendingQueries.clear()
            return@LaunchedEffect
        }
        // `null` (search closed) is never an echo: this field only sends strings.
        val echoIndex = if (query != null) pendingQueries.indexOf(newQuery) else -1
        if (echoIndex >= 0) {
            // Our own value coming back (possibly skipping some, as state flows conflate).
            repeat(echoIndex + 1) { pendingQueries.removeFirst() }
            return@LaunchedEffect
        }
        pendingQueries.clear()
        state.setTextAndPlaceCursorAtEnd(newQuery)
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collectLatest { text ->
                // Compare with what the caller will have once it catches up, not its stale query,
                // so typing then undoing within one frame still sends the undo.
                val knownQuery = pendingQueries.lastOrNull() ?: currentQuery.orEmpty()
                if (text != knownQuery) {
                    if (pendingQueries.size >= MAX_PENDING_QUERIES) pendingQueries.removeFirst()
                    pendingQueries.addLast(text)
                    // Sent before the first suspension point, so a newer edit cancelling this
                    // block never drops it.
                    currentOnQueryChange(text)
                }
                // Edits like `a -> ab -> a` can settle without `query` ever changing, so the effect
                // above never sees the echoes. Once the caller has had a couple of frames to
                // recompose with what we sent, a query matching the text means it caught up.
                repeat(ECHO_SETTLE_FRAMES) { withFrameNanos { } }
                if (currentQuery.orEmpty() == state.text.toString()) pendingQueries.clear()
            }
    }

    return state
}

private const val MAX_PENDING_QUERIES = 64
private const val ECHO_SETTLE_FRAMES = 2
