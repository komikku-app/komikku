package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.biometric.BiometricTimesContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesScreenState
import eu.kanade.tachiyomi.ui.category.biometric.TimeRangeItem

@Composable
fun BiometricTimesScreen(
    state: BiometricTimesScreenState.Success,
    onClickCreate: () -> Unit,
    onClickDelete: (TimeRangeItem) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(R.string.biometric_lock_times),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                textResource = R.string.biometric_lock_times_empty,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        BiometricTimesContent(
            timeRanges = state.timeRanges,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topPaddingValues + PaddingValues(horizontal = horizontalPadding),
            onClickDelete = onClickDelete,
        )
    }
}
