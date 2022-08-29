package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.BrowseTabWrapper
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController

class MigrationSourcesController : FullComposeController<MigrationSourcesPresenterWrapper>() {

    override fun createPresenter() = MigrationSourcesPresenterWrapper()

    @Composable
    override fun ComposeContent() {
        BrowseTabWrapper(migrateSourcesTab(router, presenter = presenter.presenter))
    }
}

private const val HELP_URL = "https://tachiyomi.org/help/guides/source-migration/"
