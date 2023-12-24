package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.biometric.BiometricTimesContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesScreenState
import eu.kanade.tachiyomi.ui.category.biometric.TimeRangeItem
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

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
                title = stringResource(SYMR.strings.biometric_lock_times),
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
                SYMR.strings.biometric_lock_times_empty,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        BiometricTimesContent(
            timeRanges = state.timeRanges,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topSmallPaddingValues +
                PaddingValues(horizontal = MaterialTheme.padding.medium),
            onClickDelete = onClickDelete,
        )
    }
}
