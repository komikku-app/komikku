package eu.kanade.tachiyomi.ui.manga.info

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.MangaThumbnail
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.MangaInfoHeaderBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.setTooltip
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import exh.MERGED_SOURCE_ID
import exh.isNamespaceSource
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.util.SourceTagsUtil
import exh.util.makeSearchChip
import exh.util.setChipsExtended
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaInfoHeaderAdapter(
    private val controller: MangaController,
    private val fromSource: Boolean
) :
    RecyclerView.Adapter<MangaInfoHeaderAdapter.HeaderViewHolder>() {

    private var manga: Manga = controller.presenter.manga
    private var source: Source = controller.presenter.source
    private var meta: RaisedSearchMetadata? = controller.presenter.meta

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: MangaInfoHeaderBinding

    private var initialLoad: Boolean = true
    private var currentMangaThumbnail: MangaThumbnail? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        binding = MangaInfoHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    val tagsAdapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter(null)

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
            // For rounded corners
            binding.mangaCover.clipToOutline = true

            binding.btnFavorite.clicks()
                .onEach { controller.onFavoriteClick() }
                .launchIn(scope)

            if (controller.presenter.manga.favorite && Injekt.get<TrackManager>().hasLoggedServices()) {
                binding.btnTracking.visible()
                binding.btnTracking.clicks()
                    .onEach { controller.onTrackingClick() }
                    .launchIn(scope)
            } else {
                binding.btnTracking.gone()
            }

            if (controller.presenter.source is HttpSource) {
                binding.btnWebview.visible()
                binding.btnWebview.clicks()
                    .onEach { controller.openMangaInWebView() }
                    .launchIn(scope)
                binding.btnWebview.setTooltip(R.string.action_open_in_web_view)

                binding.btnShare.visible()
                binding.btnShare.clicks()
                    .onEach { controller.shareManga() }
                    .launchIn(scope)
                binding.btnShare.setTooltip(R.string.action_share)
            }

            // SY -->
            if (controller.presenter.manga.favorite) {
                binding.btnMigrate.visible()
                binding.btnMigrate.clicks()
                    .onEach { controller.migrateManga() }
                    .launchIn(scope)
                binding.btnMigrate.setTooltip(R.string.migrate)

                binding.btnSmartSearch.visible()
                binding.btnSmartSearch.clicks()
                    .onEach { controller.openSmartSearch() }
                    .launchIn(scope)
                binding.btnSmartSearch.setTooltip(R.string.merge_with_another_source)
            }
            // SY <--

            binding.mangaFullTitle.longClicks()
                .onEach {
                    controller.activity?.copyToClipboard(
                        view.context.getString(R.string.title),
                        binding.mangaFullTitle.text.toString()
                    )
                }
                .launchIn(scope)

            binding.mangaFullTitle.clicks()
                .onEach {
                    controller.performGlobalSearch(binding.mangaFullTitle.text.toString())
                }
                .launchIn(scope)

            binding.mangaAuthor.longClicks()
                .onEach {
                    // SY -->
                    val author = binding.mangaAuthor.text.toString()
                    controller.activity?.copyToClipboard(
                        author,
                        SourceTagsUtil().getWrappedTag(source.id, namespace = "artist", tag = author) ?: author
                    )
                    // SY <--
                }
                .launchIn(scope)

            binding.mangaAuthor.clicks()
                .onEach {
                    // SY -->
                    val author = binding.mangaAuthor.text.toString()
                    controller.performGlobalSearch(SourceTagsUtil().getWrappedTag(source.id, namespace = "artist", tag = author) ?: author)
                    // SY <--
                }
                .launchIn(scope)

            binding.mangaArtist.longClicks()
                .onEach {
                    // SY -->
                    val artist = binding.mangaArtist.text.toString()
                    controller.activity?.copyToClipboard(
                        artist,
                        SourceTagsUtil().getWrappedTag(source.id, namespace = "artist", tag = artist) ?: artist
                    )
                    // SY <--
                }
                .launchIn(scope)

            binding.mangaArtist.clicks()
                .onEach {
                    // SY -->
                    val artist = binding.mangaArtist.text.toString()
                    controller.performGlobalSearch(SourceTagsUtil().getWrappedTag(source.id, namespace = "artist", tag = artist) ?: artist)
                    // SY <--
                }
                .launchIn(scope)

            binding.mangaSummary.longClicks()
                .onEach {
                    controller.activity?.copyToClipboard(
                        view.context.getString(R.string.description),
                        binding.mangaSummary.text.toString()
                    )
                }
                .launchIn(scope)

            binding.mangaCover.longClicks()
                .onEach {
                    controller.activity?.copyToClipboard(
                        view.context.getString(R.string.title),
                        controller.presenter.manga.title
                    )
                }
                .launchIn(scope)

            // EXH -->
            if (controller.smartSearchConfig == null) {
                binding.recommendBtn.visible()
                binding.recommendBtn.clicks()
                    .onEach { controller.openRecommends() }
                    .launchIn(scope)
            } else {
                if (controller.smartSearchConfig.origMangaId != null) { binding.mergeBtn.visible() }
                binding.mergeBtn.clicks()
                    .onEach {
                        controller.mergeWithAnother()
                    }

                    .launchIn(scope)
            }
            // EXH <--

            // SY -->
            binding.mangaNamespaceTagsRecycler.layoutManager = LinearLayoutManager(itemView.context)
            binding.mangaNamespaceTagsRecycler.adapter = tagsAdapter
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
            // Update full title TextView.
            binding.mangaFullTitle.text = if (manga.title.isBlank()) {
                view.context.getString(R.string.unknown)
            } else {
                manga.title
            }

            // Update author TextView.
            binding.mangaAuthor.text = if (manga.author.isNullOrBlank()) {
                view.context.getString(R.string.unknown_author)
            } else {
                manga.author
            }

            // Update artist TextView.
            val hasArtist = !manga.artist.isNullOrBlank() && manga.artist != manga.author
            binding.mangaArtist.isVisible = hasArtist
            if (hasArtist) {
                binding.mangaArtist.text = manga.artist
            }

            // If manga source is known update source TextView.
            val mangaSource = source?.toString()
            with(binding.mangaSource) {
                // SY -->
                if (source != null && source.id == MERGED_SOURCE_ID) {
                    text = MergedSource.MangaConfig.readFromUrl(Injekt.get(), manga.url).children.map {
                        Injekt.get<SourceManager>().getOrStub(it.source).toString()
                    }.distinct().joinToString()
                } else /* SY <-- */ if (mangaSource != null) {
                    text = mangaSource
                    setOnClickListener {
                        val sourceManager = Injekt.get<SourceManager>()
                        controller.performSearch(sourceManager.getOrStub(source.id).name)
                    }
                } else {
                    text = view.context.getString(R.string.unknown)
                }
            }

            // Update status TextView.
            binding.mangaStatus.setText(
                when (manga.status) {
                    SManga.ONGOING -> R.string.ongoing
                    SManga.COMPLETED -> R.string.completed
                    SManga.LICENSED -> R.string.licensed
                    else -> R.string.unknown_status
                }
            )

            // Set the favorite drawable to the correct one.
            setFavoriteButtonState(manga.favorite)

            // Set cover if changed.
            val mangaThumbnail = manga.toMangaThumbnail()
            if (mangaThumbnail != currentMangaThumbnail) {
                currentMangaThumbnail = mangaThumbnail
                listOf(binding.mangaCover, binding.backdrop)
                    .forEach {
                        GlideApp.with(view.context)
                            .load(mangaThumbnail)
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .centerCrop()
                            .into(it)
                    }
            }

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

                // Update genres list
                if (!manga.genre.isNullOrBlank()) {
                    // SY -->
                    if (source != null && source.isNamespaceSource()) {
                        val genre = manga.getGenres()
                        if (!genre.isNullOrEmpty()) {
                            val namespaceTags = genre.map { SourceTagsUtil().parseTag(it) }
                                .groupBy { it.first }
                                .mapValues { values -> values.value.map { makeSearchChip(it.second, controller::performSearch, controller::performGlobalSearch, source.id, itemView.context, it.first) } }
                                .map { NamespaceTagsItem(it.key, it.value) }
                            tagsAdapter.updateDataSet(namespaceTags)
                        }
                    }
                    binding.mangaGenresTagsFullChips.setChipsExtended(manga.getGenres(), controller::performSearch, controller::performGlobalSearch, source?.id ?: 0)
                    binding.mangaGenresTagsCompactChips.setChipsExtended(manga.getGenres(), controller::performSearch, controller::performGlobalSearch, source?.id ?: 0)
                    // SY <--
                } else {
                    binding.mangaGenresTagsWrapper.gone()
                }

                // Handle showing more or less info
                binding.mangaSummary.clicks()
                    .onEach { toggleMangaInfo(view.context) }
                    .launchIn(scope)
                binding.mangaInfoToggle.clicks()
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
            binding.mangaSummaryLabel.visibleIf { visible }
            binding.mangaSummary.visibleIf { visible }
            binding.mangaGenresTagsWrapper.visibleIf { visible }
            binding.mangaInfoToggle.visibleIf { visible }
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

            binding.mangaGenresTagsCompact.visibleIf { isExpanded }
            // SY -->
            if (source.isNamespaceSource()) {
                binding.mangaNamespaceTagsRecycler.visibleIf { !isExpanded }
            } else {
                binding.mangaGenresTagsFullChips.visibleIf { !isExpanded }
            }
            // SY <--
        }

        /**
         * Update favorite button with correct drawable and text.
         *
         * @param isFavorite determines if manga is favorite or not.
         */
        private fun setFavoriteButtonState(isFavorite: Boolean) {
            // Set the Favorite drawable to the correct one.
            // Border drawable if false, filled drawable if true.
            binding.btnFavorite.apply {
                icon = ContextCompat.getDrawable(
                    context,
                    if (isFavorite) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_border_24dp
                )
                text =
                    context.getString(if (isFavorite) R.string.in_library else R.string.add_to_library)
                isChecked = isFavorite
            }
        }
    }
}
