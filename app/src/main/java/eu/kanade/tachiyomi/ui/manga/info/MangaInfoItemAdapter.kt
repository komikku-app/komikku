package eu.kanade.tachiyomi.ui.manga.info

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.MangaInfoItemBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.isEhBasedSource
import exh.isNamespaceSource
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata.Companion.TAG_TYPE_VIRTUAL
import exh.util.SourceTagsUtil
import exh.util.makeSearchChip
import exh.util.setChipsExtended
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import uy.kohesive.injekt.api.get

class MangaInfoItemAdapter(
    private val controller: MangaController,
    private val fromSource: Boolean
) :
    RecyclerView.Adapter<MangaInfoItemAdapter.HeaderViewHolder>() {

    private var manga: Manga = controller.presenter.manga
    private var source: Source = controller.presenter.source
    private var meta: RaisedSearchMetadata? = controller.presenter.meta

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
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
            binding.mangaSummary.longClicks()
                .onEach {
                    controller.activity?.copyToClipboard(
                        view.context.getString(R.string.description),
                        binding.mangaSummary.text.toString()
                    )
                }
                .launchIn(scope)

            binding.genreGroups.layoutManager = LinearLayoutManager(itemView.context)
            binding.genreGroups.adapter = mangaTagsInfoAdapter

            setMangaInfo(manga, source)
        }

        /**
         * Update the view with manga information.
         *
         * @param manga manga object containing information about manga.
         * @param source the source of the manga.
         */
        @ExperimentalCoroutinesApi
        private fun setMangaInfo(manga: Manga, source: Source?) {
            // Manga info section
            val hasInfoContent = !manga.description.isNullOrBlank() || !manga.genre.isNullOrBlank()
            showMangaInfo(hasInfoContent)
            if (hasInfoContent) {
                // Update description TextView.
                binding.mangaSummary.text = if (manga.description.isNullOrBlank()) {
                    view.context.getString(R.string.unknown)
                } else {
                    manga.description
                }

                if (binding.mangaSummary.text == "meta") {
                    binding.mangaSummary.isVisible = false
                    binding.mangaSummaryLabel.setText(R.string.tags)
                }

                // Update genres list
                if (!manga.genre.isNullOrBlank()) {
                    // SY -->
                    if (source != null && source.isNamespaceSource()) {
                        val metaTags = meta?.tags?.filterNot { it.type == TAG_TYPE_VIRTUAL }?.groupBy { it.namespace }
                        var namespaceTags: List<NamespaceTagsItem> = emptyList()
                        if (source.isEhBasedSource() && metaTags != null && metaTags.all { it.key != null }) {
                            namespaceTags = metaTags
                                .mapValues { values -> values.value.map { makeSearchChip(it.name, controller::performSearch, controller::performGlobalSearch, source.id, itemView.context, it.namespace, it.type) } }
                                .map { NamespaceTagsItem(it.key!!, it.value) }
                        } else {
                            val genre = manga.getGenres()
                            if (!genre.isNullOrEmpty()) {
                                namespaceTags = genre.map { SourceTagsUtil().parseTag(it) }
                                    .groupBy { it.first }
                                    .mapValues { values -> values.value.map { makeSearchChip(it.second, controller::performSearch, controller::performGlobalSearch, source.id, itemView.context, it.first) } }
                                    .map { NamespaceTagsItem(it.key, it.value) }
                            }
                        }
                        mangaTagsInfoAdapter?.updateDataSet(namespaceTags)
                    }
                    binding.mangaGenresTagsFullChips.setChipsExtended(manga.getGenres(), controller::performSearch, controller::performGlobalSearch, source?.id ?: 0)
                    binding.mangaGenresTagsCompactChips.setChipsExtended(manga.getGenres(), controller::performSearch, controller::performGlobalSearch, source?.id ?: 0)
                    // SY <--
                } else {
                    binding.mangaGenresTagsWrapper.isVisible = false
                }

                // Handle showing more or less info
                merge(view.clicks(), binding.mangaSummary.clicks(), binding.mangaInfoToggle.clicks())
                    .onEach { toggleMangaInfo(view.context) }
                    .launchIn(scope)

                // Expand manga info if navigated from source listing
                if (initialLoad && fromSource) {
                    toggleMangaInfo(view.context)
                    initialLoad = false
                }
            }
        }

        private fun showMangaInfo(visible: Boolean) {
            binding.mangaSummaryLabel.isVisible = visible
            binding.mangaSummary.isVisible = visible
            binding.mangaGenresTagsWrapper.isVisible = visible
            binding.mangaInfoToggle.isVisible = visible
        }

        private fun toggleMangaInfo(context: Context) {
            val isExpanded =
                binding.mangaInfoToggle.text == context.getString(R.string.manga_info_collapse)

            with(binding.mangaInfoToggle) {
                text = if (isExpanded) {
                    context.getString(R.string.manga_info_expand)
                } else {
                    context.getString(R.string.manga_info_collapse)
                }

                icon = if (isExpanded) {
                    context.getDrawable(R.drawable.ic_baseline_expand_more_24dp)
                } else {
                    context.getDrawable(R.drawable.ic_baseline_expand_less_24dp)
                }
            }

            with(binding.mangaSummary) {
                maxLines =
                    if (isExpanded) {
                        2
                    } else {
                        Int.MAX_VALUE
                    }

                ellipsize =
                    if (isExpanded) {
                        TextUtils.TruncateAt.END
                    } else {
                        null
                    }
            }

            binding.mangaGenresTagsCompact.isVisible = isExpanded
            // SY -->
            if (!source.isNamespaceSource()) {
                binding.mangaGenresTagsFullChips.isVisible = !isExpanded
            } else {
                binding.genreGroups.isVisible = !isExpanded
            }
            // SY <--
        }
    }
}
