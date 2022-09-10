package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.biometric.BiometricTimesContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesPresenter
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesPresenter.Dialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration

@Composable
fun BiometricTimesScreen(
    presenter: BiometricTimesPresenter,
    navigateUp: () -> Unit,
    openCreateDialog: (Duration?) -> Unit,
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
                onCreate = { presenter.dialog = Dialog.Create },
            )
        },
    ) { paddingValues ->
        val context = LocalContext.current
        when {
            presenter.isLoading -> LoadingScreen()
            presenter.isEmpty -> EmptyScreen(textResource = R.string.biometric_lock_times_empty)
            else -> {
                BiometricTimesContent(
                    state = presenter,
                    lazyListState = lazyListState,
                    paddingValues = paddingValues + topPaddingValues + PaddingValues(horizontal = horizontalPadding),
                )
            }
        }

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            Dialog.Create -> {
                LaunchedEffect(Unit) {
                    openCreateDialog(null)
                }
            }
            is Dialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { presenter.deleteTimeRanges(dialog.timeRange) },
                    title = stringResource(R.string.delete_time_range),
                    text = stringResource(R.string.delete_time_range_confirmation, dialog.timeRange.formattedString),
                )
            }
            else -> {}
        }
        LaunchedEffect(Unit) {
            presenter.events.collectLatest { event ->
                when (event) {
                    is BiometricTimesPresenter.Event.TimeConflicts -> {
                        context.toast(R.string.biometric_lock_time_conflicts)
                    }
                    is BiometricTimesPresenter.Event.InternalError -> {
                        context.toast(R.string.internal_error)
                    }
                }
            }
        }
    }
}
