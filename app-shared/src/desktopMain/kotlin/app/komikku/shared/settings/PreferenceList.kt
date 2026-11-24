package app.komikku.shared.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceItem
import eu.kanade.presentation.more.settings.screen.findHighlightedIndex
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import kotlin.time.Duration.Companion.seconds

/**
 * KMK: desktop renderer of the borrowed settings model (Android `PreferenceScreen`): groups with their header, then
 * each item through the borrowed `PreferenceItem` (switch, list, multi-select, edit text, slider, text / link, info
 * widgets). With [highlightKey] (a settings search result, P15; Android's `SearchableSettings.highlightKey`) the list
 * scrolls to the item of that title and the item blinks like Android's; afterwards nothing is highlighted any more.
 * [onHighlightConsumed] is called once the highlight started: the caller forgets [highlightKey] there (Android resets
 * `SearchableSettings.highlightKey`), so showing the page again later neither scrolls nor blinks again (review fix).
 */
@Composable
fun PreferenceList(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    highlightKey: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    val state = rememberLazyListState()
    var highlighted by remember { mutableStateOf(highlightKey) }
    highlighted?.let { key ->
        LaunchedEffect(Unit) {
            onHighlightConsumed()
            val i = items.findHighlightedIndex(key)
            if (i >= 0) {
                delay(0.5.seconds)
                state.animateScrollToItem(i)
            }
            // The widget's blinking (3 s) has started by now; items composed later are not highlighted
            delay(4.seconds)
            highlighted = null
        }
    }
    ScrollbarLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        items.forEachIndexed { i, preference ->
            when (preference) {
                is Preference.PreferenceGroup -> {
                    if (!preference.enabled) return@forEachIndexed
                    item { Column { PreferenceGroupHeader(title = preference.title) } }
                    items(preference.preferenceItems) { item -> PreferenceItem(item = item, highlightKey = highlighted) }
                    item { if (i < items.lastIndex) Spacer(modifier = Modifier.height(12.dp)) }
                }
                is Preference.PreferenceItem<*, *> -> item { PreferenceItem(item = preference, highlightKey = highlighted) }
            }
        }
    }
}

/** KMK: one item of the borrowed settings model outside a [PreferenceList] (e.g. inside another lazy list). */
@Composable
fun PreferenceRow(item: Preference.PreferenceItem<*, *>) {
    PreferenceItem(item = item, highlightKey = null)
}
