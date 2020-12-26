package eu.kanade.tachiyomi.source.online

import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.source.CatalogueSource

interface BrowseSourceFilterHeader : CatalogueSource {
    fun getFilterHeader(controller: Controller): RecyclerView.Adapter<*>
}
