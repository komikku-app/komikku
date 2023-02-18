package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaScreen
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetFavorites
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.migrateSourceTab(): TabContent {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { MigrateSourceScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = R.string.label_migration,
        actions = listOf(
            AppBar.Action(
                title = stringResource(R.string.migration_help_guide),
                icon = Icons.Outlined.HelpOutline,
                onClick = {
                    uriHandler.openUri("https://tachiyomi.org/help/guides/source-migration/")
                },
            ),
        ),
        content = { contentPadding, _ ->
            MigrateSourceScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source ->
                    navigator.push(MigrationMangaScreen(source.id))
                },
                onToggleSortingDirection = screenModel::toggleSortingDirection,
                onToggleSortingMode = screenModel::toggleSortingMode,
                // SY -->
                onClickAll = { source ->
                    // TODO: Jay wtf, need to clean this up sometime
                    launchIO {
                        val manga = Injekt.get<GetFavorites>().await()
                        val sourceMangas =
                            manga.asSequence().filter { it.source == source.id }.map { it.id }.toList()
                        withUIContext {
                            PreMigrationScreen.navigateToMigration(
                                Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                                navigator,
                                sourceMangas,
                            )
                        }
                    }
                },
                // SY <--
            )
        },
    )
}
