package eu.kanade.presentation.category.components.biometric

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.BiometricTimesState
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesPresenter

@Composable
fun BiometricTimesContent(
    state: BiometricTimesState,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
) {
    val timeRanges = state.timeRanges
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(timeRanges) { timeRange ->
            BiometricTimesListItem(
                modifier = Modifier.animateItemPlacement(),
                timeRange = timeRange,
                onDelete = { state.dialog = BiometricTimesPresenter.Dialog.Delete(timeRange) },
            )
        }
    }
}
