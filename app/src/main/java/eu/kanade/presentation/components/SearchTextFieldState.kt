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
        val echoIndex = pendingQueries.indexOf(newQuery)
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
            .collect { text ->
                // Compare with what the caller will have once it catches up, not its stale query,
                // so typing then undoing within one frame still sends the undo.
                val knownQuery = pendingQueries.lastOrNull() ?: currentQuery.orEmpty()
                if (text == knownQuery) return@collect
                if (pendingQueries.size >= MAX_PENDING_QUERIES) pendingQueries.removeFirst()
                pendingQueries.addLast(text)
                currentOnQueryChange(text)
            }
    }

    return state
}

private const val MAX_PENDING_QUERIES = 64
