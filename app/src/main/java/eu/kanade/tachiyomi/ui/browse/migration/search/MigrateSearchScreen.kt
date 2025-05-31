package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen

class MigrateSearchScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateSearchScreenModel(mangaId = mangaId) }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        MigrateSearchScreen(
            state = state,
            fromSourceId = state.from?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getManga = { screenModel.getManga(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = { navigator.push(MigrateSourceSearchScreen(state.from!!, it.id, state.searchQuery)) },
            onClickItem = { manga ->
                // KMK -->
                if (bulkFavoriteState.selectionMode) {
                    bulkFavoriteScreenModel.toggleSelection(manga)
                } else {
                    // KMK <--
                    // SY -->
                    navigator.items
                        .filterIsInstance<MigrationListScreen>()
                        .last()
                        .newSelectedItem = mangaId to manga.id
                    navigator.popUntil { it is MigrationListScreen }
                    // SY <--
                }
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
            // KMK -->
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            hasPinnedSources = screenModel.hasPinnedSources(),
            // KMK <--
        )

        // KMK -->
        // Bulk-favorite actions only
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}
