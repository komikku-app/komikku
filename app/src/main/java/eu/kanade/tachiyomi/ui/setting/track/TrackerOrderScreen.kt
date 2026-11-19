package eu.kanade.tachiyomi.ui.setting.track

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TrackingPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Lets the user choose the order trackers appear in when adding tracking to a title.
 */
class TrackerOrderScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val trackers = remember { trackerManager.orderedTrackers().toMutableStateList() }
        val lazyListState = rememberLazyListState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.pref_tracker_order),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val listPadding = contentPadding + topSmallPaddingValues
            val reorderableState = rememberReorderableLazyListState(lazyListState, listPadding) { from, to ->
                val item = trackers.removeAt(from.index)
                trackers.add(to.index, item)
                trackerManager.saveTrackerOrder(trackers)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = listPadding,
            ) {
                items(trackers, key = { it.id }) { tracker ->
                    ReorderableItem(reorderableState, tracker.id) {
                        TrackerOrderItem(tracker = tracker)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.TrackerOrderItem(tracker: Tracker) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = stringResource(KMR.strings.action_drag_to_reorder),
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.small)
                .draggableHandle(),
        )
        TrackingPreferenceWidget(
            modifier = Modifier.weight(1f),
            tracker = tracker,
            checked = tracker.isLoggedIn,
        )
    }
}
// KMK <--
