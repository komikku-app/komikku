package eu.kanade.tachiyomi.ui.browse.feed

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.GlobalSearchControllerCardBinding
import eu.kanade.tachiyomi.util.system.LocaleHelper

/**
 * Holder that binds the [FeedItem] containing catalogue cards.
 *
 * @param view view of [FeedItem]
 * @param adapter instance of [FeedAdapter]
 */
class FeedHolder(view: View, val adapter: FeedAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = GlobalSearchControllerCardBinding.bind(view)

    /**
     * Adapter containing manga from search results.
     */
    private val mangaAdapter = FeedCardAdapter(adapter.controller)

    private var lastBoundResults: List<FeedCardItem>? = null

    init {
        // Set layout horizontal.
        binding.recycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        binding.recycler.adapter = mangaAdapter

        binding.titleWrapper.setOnClickListener {
            adapter.getItem(bindingAdapterPosition)?.let {
                if (it.savedSearch != null) {
                    adapter.feedClickListener.onSavedSearchClick(it.savedSearch, it.source ?: return@let)
                } else {
                    adapter.feedClickListener.onSourceClick(it.source ?: return@let)
                }
            }
        }
        binding.titleWrapper.setOnLongClickListener {
            adapter.getItem(bindingAdapterPosition)?.let {
                adapter.feedClickListener.onRemoveClick(it.feed)
            }
            true
        }
    }

    /**
     * Show the loading of source search result.
     *
     * @param item item of card.
     */
    @SuppressLint("SetTextI18n")
    fun bind(item: FeedItem) {
        val results = item.results

        val titlePrefix = if (item.highlighted) "▶ " else ""

        binding.title.text = titlePrefix + if (item.savedSearch != null) {
            item.savedSearch.name
        } else {
            item.source?.name ?: item.feed.source.toString()
        }
        binding.subtitle.isVisible = true
        binding.subtitle.text = if (item.savedSearch != null) {
            item.source?.name ?: item.feed.source.toString()
        } else {
            LocaleHelper.getDisplayName(item.source?.lang)
        }

        when {
            results == null -> {
                binding.progress.isVisible = true
                showResultsHolder()
            }
            results.isEmpty() -> {
                binding.progress.isVisible = false
                showNoResults()
            }
            else -> {
                binding.progress.isVisible = false
                showResultsHolder()
            }
        }
        if (results !== lastBoundResults) {
            mangaAdapter.updateDataSet(results)
            lastBoundResults = results
        }
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun setImage(manga: Manga) {
        getHolder(manga)?.setImage(manga)
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(manga: Manga): FeedCardHolder? {
        mangaAdapter.allBoundViewHolders.forEach { holder ->
            val item = mangaAdapter.getItem(holder.bindingAdapterPosition)
            if (item != null && item.manga.id!! == manga.id!!) {
                return holder as FeedCardHolder
            }
        }

        return null
    }

    private fun showResultsHolder() {
        binding.noResultsFound.isVisible = false
    }

    private fun showNoResults() {
        binding.noResultsFound.isVisible = true
    }
}
