package eu.kanade.tachiyomi.ui.libraryUpdateError

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.MigrateMangaConfigScreen

class LibraryUpdateErrorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LibraryUpdateErrorScreenModel() }
        val state by screenModel.state.collectAsState()

        LibraryUpdateErrorScreen(
            state = state,
            onClick = { item ->
                navigator.push(MigrateMangaConfigScreen(listOf(item.error.mangaId)))
            },
            onClickCover = { item -> navigator.push(MangaScreen(item.error.mangaId)) },
            onMultiMigrateClicked = {
                navigator.push(MigrateMangaConfigScreen(state.selected.map { it.error.mangaId }))
            },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onErrorsDelete = screenModel::deleteSelected,
            onErrorDelete = screenModel::delete,
            onErrorSelected = screenModel::toggleSelection,
            navigateUp = navigator::pop,
        )
    }
}
