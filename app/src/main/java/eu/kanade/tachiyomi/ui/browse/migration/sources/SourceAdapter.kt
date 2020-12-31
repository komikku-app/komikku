package eu.kanade.tachiyomi.ui.browse.migration.sources

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible

/**
 * Adapter that holds the catalogue cards.
 *
 * @param controller instance of [MigrationSourcesController].
 */
class SourceAdapter(controller: MigrationSourcesController) :
    FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    init {
        setDisplayHeadersAtStartUp(true)
    }

    // SY -->
    /**
     * Listener for auto item clicks.
     */
    val allClickListener: OnAllClickListener? = controller

    /**
     * Listener which should be called when user clicks select.
     */
    interface OnAllClickListener {
        fun onAllClick(position: Int)
    }
    // SY <--
}
