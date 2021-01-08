package eu.kanade.tachiyomi.ui.browse.source.index

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.IndexAdapterBinding

/**
 * Adapter that holds the search cards.
 *
 * @param controller instance of [IndexController].
 */
class IndexAdapter(val controller: IndexController) :
    RecyclerView.Adapter<IndexAdapter.ViewHolder>() {

    val clickListener: ClickListener = controller

    private lateinit var binding: IndexAdapterBinding

    var holder: IndexAdapter.ViewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IndexAdapter.ViewHolder {
        binding = IndexAdapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: IndexAdapter.ViewHolder, position: Int) {
        this.holder = holder
        holder.bindBrowse(null)
        holder.bindLatest(null)
    }

    // stores and recycles views as they are scrolled off screen
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val latestAdapter = IndexCardAdapter(controller)
        private var latestLastBoundResults: List<IndexCardItem>? = null

        private val browseAdapter = IndexCardAdapter(controller)
        private var browseLastBoundResults: List<IndexCardItem>? = null

        init {
            binding.browseBarWrapper.setOnClickListener {
                clickListener.onBrowseClick()
            }
            binding.latestBarWrapper.setOnClickListener {
                clickListener.onLatestClick()
            }

            binding.latestRecycler.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            binding.latestRecycler.adapter = latestAdapter

            binding.browseRecycler.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            binding.browseRecycler.adapter = browseAdapter
        }

        fun bindLatest(latestResults: List<IndexCardItem>?) {
            when {
                latestResults == null -> {
                    binding.latestProgress.isVisible = true
                    showLatestResultsHolder()
                }
                latestResults.isEmpty() -> {
                    binding.latestProgress.isVisible = false
                    showLatestNoResults()
                }
                else -> {
                    binding.latestProgress.isVisible = false
                    showLatestResultsHolder()
                }
            }
            if (latestResults !== latestLastBoundResults) {
                latestAdapter.updateDataSet(latestResults)
                latestLastBoundResults = latestResults
            }
        }

        fun bindBrowse(browseResults: List<IndexCardItem>?) {
            when {
                browseResults == null -> {
                    binding.browseProgress.isVisible = true
                    showBrowseResultsHolder()
                }
                browseResults.isEmpty() -> {
                    binding.browseProgress.isVisible = false
                    showBrowseNoResults()
                }
                else -> {
                    binding.browseProgress.isVisible = false
                    showBrowseResultsHolder()
                }
            }
            if (browseResults !== browseLastBoundResults) {
                browseAdapter.updateDataSet(browseResults)
                browseLastBoundResults = browseResults
            }
        }

        private fun showLatestResultsHolder() {
            binding.latestNoResultsFound.isVisible = false
        }

        private fun showLatestNoResults() {
            binding.latestNoResultsFound.isVisible = true
        }

        private fun showBrowseResultsHolder() {
            binding.browseNoResultsFound.isVisible = false
        }

        private fun showBrowseNoResults() {
            binding.browseNoResultsFound.isVisible = true
        }

        fun setLatestImage(manga: Manga) {
            latestAdapter.allBoundViewHolders.forEach {
                if (it !is IndexCardHolder) return@forEach
                if (latestAdapter.getItem(it.bindingAdapterPosition)?.manga?.id != manga.id) return@forEach
                it.setImage(manga)
            }
        }
        fun setBrowseImage(manga: Manga) {
            browseAdapter.allBoundViewHolders.forEach {
                if (it !is IndexCardHolder) return@forEach
                if (browseAdapter.getItem(it.bindingAdapterPosition)?.manga?.id != manga.id) return@forEach
                it.setImage(manga)
            }
        }
    }

    interface ClickListener {
        fun onBrowseClick(search: String? = null, filters: String? = null)
        fun onLatestClick()
    }

    override fun getItemCount(): Int = 1
}
