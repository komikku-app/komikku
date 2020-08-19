package eu.kanade.tachiyomi.ui.browse.source.index

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.database.models.Manga

/**
 * Adapter that holds the manga items from search results.
 *
 * @param controller instance of [IndexController].
 */
class IndexCardAdapter(controller: IndexController) :
    FlexibleAdapter<IndexCardItem>(null, controller, true) {

    /**
     * Listen for browse item clicks.
     */
    val mangaClickListener: OnMangaClickListener = controller

    /**
     * Listener which should be called when user clicks browse.
     * Note: Should only be handled by [IndexController]
     */
    interface OnMangaClickListener {
        fun onMangaClick(manga: Manga)
        fun onMangaLongClick(manga: Manga)
    }
}
