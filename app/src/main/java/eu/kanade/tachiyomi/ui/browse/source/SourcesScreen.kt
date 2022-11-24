package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.browse.BrowseTabWrapper

class SourcesScreen(private val smartSearchConfig: SourcesController.SmartSearchConfig?) : Screen {

    @Composable
    override fun Content() {
        BrowseTabWrapper(sourcesTab(smartSearchConfig))
    }
}
