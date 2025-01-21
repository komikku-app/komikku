package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.BrowseTabWrapper
import eu.kanade.presentation.util.Screen

class MigrationSourcesScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        BrowseTabWrapper(migrateSourceTab(), onBackPressed = navigator::pop)
    }
}
