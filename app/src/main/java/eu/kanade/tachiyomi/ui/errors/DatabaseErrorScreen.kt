package eu.kanade.tachiyomi.ui.errors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.errors.DatabaseErrorScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen

class DatabaseErrorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { DatabaseErrorScreenModel() }
        val state by screenModel.state.collectAsState()

        DatabaseErrorScreen(
            state = state,
            onClick = { item ->
//                PreMigrationScreen.navigateToMigration(
//                    Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
//                    navigator,
//                    listOf(item.error.mangaId),
//                )
            },
            onClickCover = { item -> navigator.push(MangaScreen(item.error.manga.mangaId)) },
            onMultiMigrateClicked = {
//                PreMigrationScreen.navigateToMigration(
//                    Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
//                    navigator,
//                    state.selected.map { it.error.mangaId },
//                )
            },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onErrorSelected = screenModel::toggleSelection,
            navigateUp = navigator::pop,
        )
    }
}
