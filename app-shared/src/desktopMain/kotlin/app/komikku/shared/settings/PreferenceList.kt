package app.komikku.shared.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceItem
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import tachiyomi.presentation.core.components.ScrollbarLazyColumn

/**
 * KMK: desktop renderer of the borrowed settings model (Android `PreferenceScreen` without the settings
 * search highlight): groups with their header, then each item through the borrowed `PreferenceItem`
 * (switch, list, multi-select, edit text, slider, text / link, info widgets).
 */
@Composable
fun PreferenceList(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    ScrollbarLazyColumn(
        modifier = modifier,
        state = rememberLazyListState(),
        contentPadding = contentPadding,
    ) {
        items.forEachIndexed { i, preference ->
            when (preference) {
                is Preference.PreferenceGroup -> {
                    if (!preference.enabled) return@forEachIndexed
                    item { Column { PreferenceGroupHeader(title = preference.title) } }
                    items(preference.preferenceItems) { item -> PreferenceItem(item = item, highlightKey = null) }
                    item { if (i < items.lastIndex) Spacer(modifier = Modifier.height(12.dp)) }
                }
                is Preference.PreferenceItem<*, *> -> item { PreferenceItem(item = preference, highlightKey = null) }
            }
        }
    }
}

/** KMK: one item of the borrowed settings model outside a [PreferenceList] (e.g. inside another lazy list). */
@Composable
fun PreferenceRow(item: Preference.PreferenceItem<*, *>) {
    PreferenceItem(item = item, highlightKey = null)
}
