package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class MigrationSourcesController : BasicFullComposeController() {

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MigrationSourcesScreen())
    }
}

private const val HELP_URL = "https://tachiyomi.org/help/guides/source-migration/"
