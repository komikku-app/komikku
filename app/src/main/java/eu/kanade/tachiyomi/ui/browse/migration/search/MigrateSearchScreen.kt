package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.manga.AllowDuplicateDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen

class MigrateSearchScreen(private val mangaId: Long, private val validSources: List<Long>) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel =
            rememberScreenModel { MigrateSearchScreenModel(mangaId = mangaId, validSources = validSources) }
        val state by screenModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel { MigrateSearchScreenDialogScreenModel(mangaId = mangaId) }
        val dialogState by dialogScreenModel.state.collectAsState()

        // KMK -->
        BackHandler(enabled = state.selectionMode) {
            when {
                state.selectionMode -> screenModel.toggleSelectionMode()
            }
        }
        // KMK <--

        MigrateSearchScreen(
            // KMK -->
            screenModel = screenModel,
            // KMK <--
            state = state,
            fromSourceId = state.fromSourceId,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getManga = { screenModel.getManga(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                // SY -->
                navigator.push(SourceSearchScreen(dialogState.manga!!, it.id, state.searchQuery))
                // SY <--
            },
            onClickItem = {
                // KMK -->
                if (state.selectionMode) {
                    screenModel.toggleSelection(it)
                }
                else
                // KMK <--
                {
                    // SY -->
                    navigator.items
                        .filterIsInstance<MigrationListScreen>()
                        .last()
                        .newSelectedItem = mangaId to it.id
                    navigator.popUntil { it is MigrationListScreen }
                    // SY <--
                }
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
        )

        // KMK -->
        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is SearchScreenModel.Dialog.ChangeMangasCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, exclude ->
                        screenModel.setMangaCategories(dialog.mangas, include, exclude)
                    },
                )
            }
            is SearchScreenModel.Dialog.AllowDuplicate -> {
                AllowDuplicateDialog(
                    onDismissRequest = onDismissRequest,
                    onAllowAllDuplicate = {
                        screenModel.addFavoriteDuplicate()
                    },
                    onSkipAllDuplicate = {
                        screenModel.addFavoriteDuplicate(skipAllDuplicates = true)
                    },
                    onOpenManga = {
                        navigator.push(MangaScreen(dialog.duplicatedManga.second.id))
                    },
                    onAllowDuplicate = {
                        screenModel.addFavorite(startIdx = dialog.duplicatedManga.first + 1)
                    },
                    onSkipDuplicate = {
                        screenModel.removeDuplicateSelectedManga(index = dialog.duplicatedManga.first)
                        screenModel.addFavorite(startIdx = dialog.duplicatedManga.first)
                    },
                    duplicatedName = dialog.duplicatedManga.second.title,
                )
            }
            else -> {}
        }
        // KMK <--
    }
}
