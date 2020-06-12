package eu.kanade.tachiyomi.ui.browse.latest

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.models.Manga

/**
 * Adapter that holds the manga items from search results.
 *
 * @param controller instance of [LatestController].
 */
class LatestCardAdapter(controller: LatestController) :
    FlexibleAdapter<LatestCardItem>(null, controller, true) {

    /**
     * Listen for browse item clicks.
     */
    val mangaClickListener: OnMangaClickListener = controller

    /**
     * Listener which should be called when user clicks browse.
     * Note: Should only be handled by [LatestController]
     */
    interface OnMangaClickListener {
        fun onMangaClick(manga: Manga)
        fun onMangaLongClick(manga: Manga)
    }
}
