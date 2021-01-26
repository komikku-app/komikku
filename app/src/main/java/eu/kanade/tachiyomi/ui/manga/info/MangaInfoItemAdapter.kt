package eu.kanade.tachiyomi.ui.manga.info

import android.graphics.PorterDuff
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.MangaInfoItemBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.source.getMainSource
import exh.source.isEhBasedSource
import exh.util.getRaisedTags
import exh.util.makeSearchChip
import exh.util.setChipsExtended
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks

class MangaInfoItemAdapter(
    private val controller: MangaController,
    private val fromSource: Boolean
) :
    RecyclerView.Adapter<MangaInfoItemAdapter.HeaderViewHolder>() {

    private var manga: Manga = controller.presenter.manga
    private var source: Source = controller.presenter.source
    private var meta: RaisedSearchMetadata? = controller.presenter.meta

    private lateinit var binding: MangaInfoItemBinding

    private var initialLoad: Boolean = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        binding = MangaInfoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding.root)
    }

    private var mangaTagsInfoAdapter: FlexibleAdapter<IFlexible<*>>? = FlexibleAdapter(null)

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    /**
     * Update the view with manga information.
     *
     * @param manga manga object containing information about manga.
     * @param source the source of the manga.
     */
    fun update(manga: Manga, source: Source, meta: RaisedSearchMetadata?) {
        this.manga = manga
        this.source = source
        this.meta = meta

        notifyDataSetChanged()
    }

    inner class HeaderViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            binding.mangaSummaryText.longClicks()
                .onEach {
                    controller.activity?.copyToClipboard(
                        view.context.getString(R.string.description),
                        binding.mangaSummaryText.text.toString()
                    )
                }
                .launchIn(controller.viewScope)

            binding.genreGroups.layoutManager = LinearLayoutManager(itemView.context)
            binding.genreGroups.adapter = mangaTagsInfoAdapter

            // SY -->
            mangaTagsInfoAdapter?.mItemClickListener = FlexibleAdapter.OnItemClickListener { _, _ ->
                controller.viewScope.launch {
                    toggleMangaInfo()
                }
                false
            }
            // SY <--

            setMangaInfo(manga, source)
        }

        /**
         * Update the view with manga information.
         *
         * @param manga manga object containing information about manga.
         * @param source the source of the manga.
         */
        private fun setMangaInfo(manga: Manga, source: Source?) {
            // Manga info section
            val hasInfoContent = !manga.description.isNullOrBlank() || !manga.genre.isNullOrBlank()
            showMangaInfo(hasInfoContent)
            if (hasInfoContent) {
                // Update description TextView.
                binding.mangaSummaryText.text = if (manga.description.isNullOrBlank()) {
                    view.context.getString(R.string.unknown)
                } else {
                    manga.description
                }

                // SY -->
                if (binding.mangaSummaryText.text == "meta") {
                    binding.mangaSummaryText.text = ""
                    binding.mangaSummaryText.maxLines = 1
                    binding.mangaInfoToggleLess.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        topToBottom = -1
                        bottomToBottom = binding.mangaSummaryText.id
                    }
                }
                // SY <--

                // Update genres list
                if (!manga.genre.isNullOrBlank()) {
                    // SY -->
                    if (source != null && source.getMainSource() is NamespaceSource) {
                        val metaTags = meta?.tags?.filterNot { it.type == TAG_TYPE_VIRTUAL }?.groupBy { it.namespace }
                        var namespaceTags: List<NamespaceTagsItem> = emptyList()
                        if (source.isEhBasedSource() && metaTags != null && metaTags.all { it.key != null }) {
                            namespaceTags = metaTags
                                .mapValues { values -> values.value.map { makeSearchChip(it.name, controller::performSearch, controller::performGlobalSearch, source.id, itemView.context, it.namespace, it.type) } }
                                .map { NamespaceTagsItem(it.key!!, it.value) }
                        } else {
                            val genre = manga.getRaisedTags()
                            if (!genre.isNullOrEmpty()) {
                                namespaceTags = genre
                                    .groupBy { it.namespace }
                                    .mapValues { values -> values.value.map { makeSearchChip(it.name, controller::performSearch, controller::performGlobalSearch, source.id, itemView.context, it.namespace) } }
                                    .map { NamespaceTagsItem(it.key, it.value) }
                            }
                        }
                        mangaTagsInfoAdapter?.updateDataSet(namespaceTags)
                    }
                    binding.mangaGenresTagsFullChips.setChipsExtended(manga.getGenres(), controller::performSearch, controller::performGlobalSearch, source?.id ?: 0)
                    binding.mangaGenresTagsCompactChips.setChipsExtended(manga.getGenres(), controller::performSearch, controller::performGlobalSearch, source?.id ?: 0)
                    // SY <--
                } else {
                    binding.mangaGenresTagsCompactChips.isVisible = false
                    binding.mangaGenresTagsFullChips.isVisible = false
                    // SY -->
                    binding.genreGroups.isVisible = false
                    // SY <--
                }

                // Handle showing more or less info
                merge(
                    binding.mangaSummarySection.clicks(),
                    binding.mangaSummaryText.clicks(),
                    binding.mangaInfoToggleMore.clicks(),
                    binding.mangaInfoToggleLess.clicks()
                )
                    .onEach { toggleMangaInfo() }
                    .launchIn(controller.viewScope)

                // Expand manga info if navigated from source listing
                if (initialLoad && fromSource) {
                    toggleMangaInfo()
                    initialLoad = false
                }
            }

            // backgroundTint attribute doesn't work properly on Android 5
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                binding.mangaInfoToggleMoreScrim.background.setColorFilter(
                    view.context.getResourceColor(android.R.attr.colorBackground),
                    PorterDuff.Mode.SRC_ATOP
                )
            }
        }

        private fun showMangaInfo(visible: Boolean) {
            binding.mangaSummarySection.isVisible = visible
        }

        private fun toggleMangaInfo() {
            val isCurrentlyExpanded = binding.mangaSummaryText.maxLines != 2 /* SY --> */ && binding.mangaSummaryText.maxLines != 1 /* SY <-- */

            binding.mangaInfoToggleMoreScrim.isVisible = isCurrentlyExpanded
            binding.mangaInfoToggleMore.isVisible = isCurrentlyExpanded
            binding.mangaInfoToggleLess.isVisible = !isCurrentlyExpanded

            binding.mangaSummaryText.maxLines = if (isCurrentlyExpanded) {
                /* SY --> */ if (binding.mangaSummaryText.text.isBlank()) 1 else /* SY <-- */ 2
            } else {
                Int.MAX_VALUE
            }

            binding.mangaGenresTagsCompact.isVisible = isCurrentlyExpanded
            // SY -->
            if (source.getMainSource() !is NamespaceSource) {
                binding.mangaGenresTagsFullChips.isVisible = !isCurrentlyExpanded
            } else {
                binding.genreGroups.isVisible = !isCurrentlyExpanded
            }
            // SY <--
        }
    }
}
