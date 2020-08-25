package eu.kanade.tachiyomi.ui.library

import android.util.TypedValue
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.source_compact_grid_item.card
import kotlinx.android.synthetic.main.source_compact_grid_item.download_text
import kotlinx.android.synthetic.main.source_compact_grid_item.local_text
import kotlinx.android.synthetic.main.source_compact_grid_item.play_layout
import kotlinx.android.synthetic.main.source_compact_grid_item.thumbnail
import kotlinx.android.synthetic.main.source_compact_grid_item.title
import kotlinx.android.synthetic.main.source_compact_grid_item.unread_text
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_source_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to single tap and long tap events.
 * @constructor creates a new library holder.
 */
open class LibraryGridHolder(
    private val view: View,
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
) : LibraryHolder(view, adapter) {

    private val preferences: PreferencesHelper = Injekt.get()

    var manga: Manga? = null

    // SY -->
    init {
        play_layout.clicks()
            .onEach {
                playButtonClicked()
            }
            .launchIn((adapter as LibraryCategoryAdapter).controller.scope)
    }
    // SY <--

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    override fun onSetValues(item: LibraryItem) {
        // SY -->
        manga = item.manga
        // SY <--
        // Update the title of the manga.
        title.text = item.manga.title

        // Update the unread count and its visibility.
        with(unread_text) {
            visibleIf { item.unreadCount > 0 }
            text = item.unreadCount.toString()
        }
        // Update the download count and its visibility.
        with(download_text) {
            visibleIf { item.downloadCount > 0 }
            text = item.downloadCount.toString()
        }
        // set local visibility if its local manga
        local_text.visibleIf { item.manga.isLocal() }

        card.radius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            preferences.eh_library_corner_radius().get().toFloat(),
            view.context.resources.displayMetrics
        )

        // SY -->
        play_layout.isVisible = (item.manga.unread > 0 && item.startReadingButton)
        // SY <--

        // Setting this via XML doesn't work
        // For rounded corners
        card.clipToOutline = true

        // Update the cover.
        GlideApp.with(view.context).clear(thumbnail)
        GlideApp.with(view.context)
            .load(item.manga.toMangaThumbnail())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(thumbnail)
    }

    // SY -->
    fun playButtonClicked() {
        manga?.let { (adapter as LibraryCategoryAdapter).controller.startReading(it, (adapter as LibraryCategoryAdapter)) }
    }
    // SY <--
}
