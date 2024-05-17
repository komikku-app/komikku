package eu.kanade.tachiyomi.ui.browse

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.RelatedMangasContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.SelectionToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.manga.RelatedManga
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RelatedMangasScreen(
    val mangaScreenModel: MangaScreenModel,
    val onKeywordClick: (String) -> Unit,
    val onKeywordLongClick: (String) -> Unit,
    private val getManga: GetManga = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : Screen() {
    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        var displayMode by sourcePreferences.sourceDisplayMode().asState(scope)

        val screenState by mangaScreenModel.state.collectAsState()
        val successState = screenState as? MangaScreenModel.State.Success ?: return

        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.toggleSelectionMode()
        }

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                if (bulkFavoriteState.selectionMode) {
                    SelectionToolbar(
                        selectedCount = bulkFavoriteState.selection.size,
                        onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                        onChangeCategoryClicked = bulkFavoriteScreenModel::addFavorite,
                        onSelectAll = {
                            successState.relatedMangasSorted?.forEach {
                                val relatedManga = it as RelatedManga.Success
                                relatedManga.mangaList.forEach { manga ->
                                    if (!bulkFavoriteState.selection.contains(manga)) {
                                        bulkFavoriteScreenModel.select(manga)
                                    }
                                }
                            }
                        },
                    )
                } else {
                    BrowseSourceSimpleToolbar(
                        navigateUp = navigator::pop,
                        title = successState.manga.title,
                        displayMode = displayMode,
                        onDisplayModeChange = { displayMode = it },
                        scrollBehavior = scrollBehavior,
                        toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            RelatedMangasContent(
                relatedMangas = successState.relatedMangasSorted,
                getMangaState = { manga -> getMangaState(initialManga = manga) },
                columns = getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = displayMode,
                contentPadding = paddingValues,
                onMangaClick = { manga ->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        navigator.push(MangaScreen(manga.id, true))
                    }
                },
                onMangaLongClick = { manga ->
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        navigator.push(MangaScreen(manga.id, true))
                    }
                },
                onKeywordClick = onKeywordClick,
                onKeywordLongClick = onKeywordLongClick,
                selection = bulkFavoriteState.selection,
            )
        }

        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.AddDuplicateManga ->
                AddDuplicateMangaDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.RemoveManga ->
                RemoveMangaDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.ChangeMangaCategory ->
                ChangeMangaCategoryDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeMangasCategoryDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)
            else -> {}
        }
    }

    private fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns()
        } else {
            libraryPreferences.portraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    @Composable
    fun getMangaState(initialManga: Manga): State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }
}
