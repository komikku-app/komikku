package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.bluelinelabs.conductor.Router
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun migrateSourcesTab(
    router: Router?,
    presenter: MigrationSourcesPresenter,
): TabContent {
    val uriHandler = LocalUriHandler.current

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
        content = {
            MigrateSourceScreen(
                presenter = presenter,
                onClickItem = { source ->
                    router?.pushController(
                        MigrationMangaController(
                            source.id,
                            source.name,
                        ),
                    )
                },
                // SY -->
                onClickAll = { source ->
                    // TODO: Jay wtf, need to clean this up sometime
                    launchIO {
                        val manga = Injekt.get<GetFavorites>().await()
                        val sourceMangas =
                            manga.asSequence().filter { it.source == source.id }.map { it.id }.toList()
                        withUIContext {
                            if (router != null) {
                                PreMigrationController.navigateToMigration(
                                    Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                                    router,
                                    sourceMangas,
                                )
                            }
                        }
                    }
                },
                // SY <--
            )
        },
    )
}
