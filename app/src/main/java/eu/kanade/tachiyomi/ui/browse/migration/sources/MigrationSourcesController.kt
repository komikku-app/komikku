package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.ComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.BrowseController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.openInBrowser
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourcesController : ComposeController<MigrationSourcesPresenter>() {

    init {
        setHasOptionsMenu(true)
    }

    override fun createPresenter() = MigrationSourcesPresenter()

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        MigrateSourceScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickItem = { source ->
                val parentController = parentController
                if (parentController is BrowseController) {
                    parentController.router
                } else {
                    router
                }.pushController(
                    MigrationMangaController(
                        source.id,
                        source.name,
                    ),
                )
            },
            onClickAll = { source ->
                // TODO: Jay wtf, need to clean this up sometime
                launchIO {
                    val manga = Injekt.get<GetFavorites>().await()
                    val sourceMangas =
                        manga.asSequence().filter { it.source == source.id }.map { it.id }.toList()
                    withUIContext {
                        PreMigrationController.navigateToMigration(
                            Injekt.get<PreferencesHelper>().skipPreMigration().get(),
                            run {
                                val parentController = parentController
                                if (parentController is BrowseController) {
                                    parentController.router
                                } else {
                                    router
                                }
                            },
                            sourceMangas,
                        )
                    }
                }
            },
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
        inflater.inflate(R.menu.browse_migrate, menu)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (val itemId = item.itemId) {
            R.id.action_source_migration_help -> {
                activity?.openInBrowser(HELP_URL)
                true
            }
            R.id.asc_alphabetical,
            R.id.desc_alphabetical,
            -> {
                presenter.setAlphabeticalSorting(itemId == R.id.asc_alphabetical)
                true
            }
            R.id.asc_count,
            R.id.desc_count,
            -> {
                presenter.setTotalSorting(itemId == R.id.asc_count)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

private const val HELP_URL = "https://tachiyomi.org/help/guides/source-migration/"
