package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen

class MigrateSearchScreen(private val mangaId: Long, private val validSources: List<Long>) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val router = LocalRouter.currentOrThrow
        val screenModel = rememberScreenModel { MigrateSearchScreenModel(mangaId = mangaId, validSources = validSources) }
        val state by screenModel.state.collectAsState()
        // SY -->
        val migrationScreen = remember {
            navigator.items.filterIsInstance<MigrationListScreen>().last()
        }
        // SY <--

        MigrateSearchScreen(
            navigateUp = navigator::pop,
            state = state,
            getManga = { source, manga ->
                screenModel.getManga(source = source, initialManga = manga)
            },
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = screenModel::search,
            onClickSource = {
                if (!screenModel.incognitoMode.get()) {
                    screenModel.lastUsedSourceId.set(it.id)
                }
                // SY -->
                router.pushController(
                    SourceSearchController(state.manga!!, it, state.searchQuery)
                        .also { searchController ->
                            searchController.useMangaForMigration = { newMangaId ->
                                migrationScreen.newSelectedItem = mangaId to newMangaId
                                navigator.pop()
                            }
                        },
                )
                // SY <--
            },
            onClickItem = {
                // SY -->
                migrationScreen.newSelectedItem = mangaId to it.id
                navigator.pop()
                // SY <--
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
        )
    }
}
