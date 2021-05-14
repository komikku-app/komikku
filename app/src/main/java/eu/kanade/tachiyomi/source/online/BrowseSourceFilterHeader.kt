package eu.kanade.tachiyomi.source.online

import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController

interface BrowseSourceFilterHeader : CatalogueSource {
    fun getFilterHeader(controller: BaseController<*>, onClick: () -> Unit): RecyclerView.Adapter<*>
}
