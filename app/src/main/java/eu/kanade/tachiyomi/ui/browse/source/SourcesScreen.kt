package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.BrowseTabWrapper
import eu.kanade.presentation.util.Screen
import java.io.Serializable

class SourcesScreen(private val smartSearchConfig: SmartSearchConfig?) : Screen() {
    @Composable
    override fun Content() {
        BrowseTabWrapper(sourcesTab(smartSearchConfig))
    }

    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long? = null) : Serializable
}
