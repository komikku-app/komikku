package eu.kanade.tachiyomi.ui.browse.source.feed

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.GlobalSearchControllerCardBinding

/**
 * Holder that binds the [SourceFeedItem] containing catalogue cards.
 *
 * @param view view of [SourceFeedItem]
 * @param adapter instance of [SourceFeedAdapter]
 */
class SourceFeedHolder(view: View, val adapter: SourceFeedAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = GlobalSearchControllerCardBinding.bind(view)

    /**
     * Adapter containing manga from search results.
     */
    private val mangaAdapter = SourceFeedCardAdapter(adapter.controller)

    private var lastBoundResults: List<SourceFeedCardItem>? = null

    init {
        // Set layout horizontal.
        binding.recycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        binding.recycler.adapter = mangaAdapter

        binding.titleWrapper.setOnClickListener {
            adapter.getItem(bindingAdapterPosition)?.let {
                when (it.sourceFeed) {
                    SourceFeed.Browse -> adapter.feedClickListener.onBrowseClick()
                    SourceFeed.Latest -> adapter.feedClickListener.onLatestClick()
                    is SourceFeed.SourceSavedSearch -> adapter.feedClickListener.onSavedSearchClick(it.sourceFeed.savedSearch)
                }
            }
        }

        binding.titleWrapper.setOnLongClickListener {
            adapter.getItem(bindingAdapterPosition)?.let {
                when (it.sourceFeed) {
                    SourceFeed.Browse -> adapter.feedClickListener.onBrowseClick()
                    SourceFeed.Latest -> adapter.feedClickListener.onLatestClick()
                    is SourceFeed.SourceSavedSearch -> adapter.feedClickListener.onRemoveClick(it.sourceFeed.feed)
                }
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
    fun bind(item: SourceFeedItem) {
        val results = item.results

        when (item.sourceFeed) {
            SourceFeed.Browse -> binding.title.setText(R.string.browse)
            SourceFeed.Latest -> binding.title.setText(R.string.latest)
            is SourceFeed.SourceSavedSearch -> binding.title.text = item.sourceFeed.savedSearch.name
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
    private fun getHolder(manga: Manga): SourceFeedCardHolder? {
        mangaAdapter.allBoundViewHolders.forEach { holder ->
            val item = mangaAdapter.getItem(holder.bindingAdapterPosition)
            if (item != null && item.manga.id!! == manga.id!!) {
                return holder as SourceFeedCardHolder
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
