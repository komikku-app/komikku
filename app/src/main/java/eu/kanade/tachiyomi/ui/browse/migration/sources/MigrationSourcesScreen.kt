package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.BrowseTabWrapper
import eu.kanade.presentation.util.Screen

class MigrationSourcesScreen : Screen() {
    @Composable
    override fun Content() {
        BrowseTabWrapper(migrateSourceTab())
    }
}
