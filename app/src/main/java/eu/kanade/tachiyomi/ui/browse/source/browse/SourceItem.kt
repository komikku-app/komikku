package eu.kanade.tachiyomi.ui.browse.source.browse

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.fredporciuncula.flow.preferences.Preference
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceComfortableGridItemBinding
import eu.kanade.tachiyomi.databinding.SourceCompactGridItemBinding
import eu.kanade.tachiyomi.ui.library.setting.LibraryDisplayMode
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.isEhBasedManga
import uy.kohesive.injekt.injectLazy

class SourceItem(val manga: Manga, private val displayMode: Preference<LibraryDisplayMode> /* SY --> */, private val metadata: RaisedSearchMetadata? = null /* SY <-- */) :
    AbstractFlexibleItem<SourceHolder<*>>() {
    // SY -->
    val preferences: PreferencesHelper by injectLazy()
    // SY <--

    override fun getLayoutRes(): Int {
        // SY -->
        if (manga.isEhBasedManga() && preferences.enhancedEHentaiView().get()) {
            return R.layout.source_enhanced_ehentai_list_item
        }
        // SY <--
        return when (displayMode.get()) {
            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> R.layout.source_compact_grid_item
            LibraryDisplayMode.ComfortableGrid -> R.layout.source_comfortable_grid_item
            LibraryDisplayMode.List -> R.layout.source_list_item
        }
    }

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): SourceHolder<*> {
        // SY -->
        if (manga.isEhBasedManga() && preferences.enhancedEHentaiView().get()) {
            return SourceEnhancedEHentaiListHolder(view, adapter)
        }
        // SY <--
        return when (displayMode.get()) {
            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
                SourceCompactGridHolder(SourceCompactGridItemBinding.bind(view), adapter)
            }
            LibraryDisplayMode.ComfortableGrid -> {
                SourceComfortableGridHolder(SourceComfortableGridItemBinding.bind(view), adapter)
            }
            LibraryDisplayMode.List -> {
                SourceListHolder(view, adapter)
            }
        }
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: SourceHolder<*>,
        position: Int,
        payloads: List<Any?>?,
    ) {
        holder.onSetValues(manga)
        // SY -->
        if (metadata != null) {
            holder.onSetMetadataValues(manga, metadata)
        }
        // SY <--
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SourceItem) {
            return manga.id == other.manga.id
        }
        return false
    }

    override fun hashCode(): Int {
        return manga.id.hashCode()
    }
}
