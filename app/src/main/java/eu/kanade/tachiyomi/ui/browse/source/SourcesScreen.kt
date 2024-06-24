package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.BrowseTabWrapper
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import exh.recs.RecommendsScreen
import java.io.Serializable

/**
 * Navigated to when invoking [MangaScreen.openSmartSearch] for entries to merge or
 * from [RecommendsScreen.openSmartSearch] for click a recommendation entry.
 * This will show a [sourcesTab] to select a source to search for entries to merge or
 * search for recommending entry.
 */
class SourcesScreen(private val smartSearchConfig: SmartSearchConfig?) : Screen() {
    @Composable
    override fun Content() {
        BrowseTabWrapper(sourcesTab(smartSearchConfig))
    }

    /**
     * initialized when invoking [MangaScreen.openSmartSearch] or [RecommendsScreen.openSmartSearch]
     */
    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long? = null) : Serializable
}
