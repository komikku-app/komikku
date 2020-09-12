package eu.kanade.tachiyomi.source.online

import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Controller

interface BrowseSourceFilterHeader {
    fun getFilterHeader(controller: Controller): RecyclerView.Adapter<*>
}
