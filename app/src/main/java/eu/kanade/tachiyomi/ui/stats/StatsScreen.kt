package eu.kanade.tachiyomi.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.stats.StatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen

class StatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { StatsScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is StatsScreenState.Loading) {
            LoadingScreen()
            return
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(R.string.label_stats),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    // SY -->
                    actions = {
                        val allRead by screenModel.allRead.collectAsState()
                        AppBarActions(
                            listOf(
                                AppBar.OverflowAction(
                                    title = if (allRead) {
                                        stringResource(R.string.ignore_non_library_entries)
                                    } else {
                                        stringResource(R.string.include_all_read_entries)
                                    },
                                    onClick = screenModel::toggleReadManga,
                                ),
                            ),
                        )
                    },
                    // SY <--
                )
            },
        ) { paddingValues ->
            StatsScreenContent(
                state = state as? StatsScreenState.Success ?: return@Scaffold,
                paddingValues = paddingValues,
            )
        }
    }
}
