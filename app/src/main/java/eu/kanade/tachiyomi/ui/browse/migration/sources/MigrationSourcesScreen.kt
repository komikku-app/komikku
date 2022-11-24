package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.browse.BrowseTabWrapper

class MigrationSourcesScreen : Screen {

    @Composable
    override fun Content() {
        BrowseTabWrapper(migrateSourceTab())
    }
}
