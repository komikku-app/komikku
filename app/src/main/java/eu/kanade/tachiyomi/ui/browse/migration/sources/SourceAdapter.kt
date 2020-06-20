package eu.kanade.tachiyomi.ui.browse.migration.sources

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Adapter that holds the catalogue cards.
 *
 * @param controller instance of [MigrationSourcesController].
 */
class SourceAdapter(val controller: MigrationSourcesController) :
    FlexibleAdapter<IFlexible<*>>(null, controller, true) {

    val cardBackground = controller.activity!!.getResourceColor(R.attr.colorSurface)

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
