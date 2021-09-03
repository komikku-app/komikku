package eu.kanade.tachiyomi.ui.manga.info

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.MangaInfoHeaderBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.base.controller.getMainAppBarHeight
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.applySystemAnimatorScale
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.view.loadAnyAutoPause
import eu.kanade.tachiyomi.util.view.setChips
import exh.merged.sql.models.MergedMangaReference
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.util.SourceTagsUtil
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import uy.kohesive.injekt.injectLazy

class MangaInfoHeaderAdapter(
    private val controller: MangaController,
    private val fromSource: Boolean,
    private val isTablet: Boolean,
) :
    RecyclerView.Adapter<MangaInfoHeaderAdapter.HeaderViewHolder>() {

    private val trackManager: TrackManager by injectLazy()

    // SY -->
    private val sourceManager: SourceManager by injectLazy()

    // SY <--

    private var manga: Manga = controller.presenter.manga
    private var source: Source = controller.presenter.source

    // SY -->
    private var meta: RaisedSearchMetadata? = controller.presenter.meta
    private var mergedMangaReferences: List<MergedMangaReference> = controller.presenter.mergedMangaReferences

    // SY <--
    private var trackCount: Int = 0
    private var metaInfoAdapter: RecyclerView.Adapter<*>? = null
    private var mangaTagsInfoAdapter: NamespaceTagsAdapter? = NamespaceTagsAdapter(controller, source)

    private lateinit var binding: MangaInfoHeaderBinding

    private var initialLoad: Boolean = true

    private val maxLines = 3

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        binding = MangaInfoHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        updateCoverPosition()
        // SY -->
        metaInfoAdapter = source.getMainSource<MetadataSource<*, *>>()?.getDescriptionAdapter(controller)
        binding.metadataView.isVisible = if (metaInfoAdapter != null) {
            binding.metadataView.layoutManager = LinearLayoutManager(binding.root.context)
            binding.metadataView.adapter = metaInfoAdapter
            true
        } else {
            false
        }

        binding.genreGroups.layoutManager = LinearLayoutManager(binding.root.context)
        binding.genreGroups.adapter = mangaTagsInfoAdapter
        // SY <--

        return HeaderViewHolder(binding.root)
    }

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
    fun update(manga: Manga, source: Source, meta: RaisedSearchMetadata?, mergedMangaReferences: List<MergedMangaReference>) {
        this.manga = manga
        this.source = source
        // SY -->
        this.meta = meta
        this.mergedMangaReferences = mergedMangaReferences
        // SY <--

        notifyDataSetChanged()
        notifyMetaAdapter()
    }

    fun setTrackingCount(trackCount: Int) {
        this.trackCount = trackCount

        notifyDataSetChanged()
    }

    private fun updateCoverPosition() {
        val appBarHeight = controller.getMainAppBarHeight()
        binding.mangaCover.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin += appBarHeight
        }
    }

    fun notifyMetaAdapter() {
        metaInfoAdapter?.notifyDataSetChanged()
    }

    inner class HeaderViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val summaryTransition = binding.mangaSummarySection.getTransition(R.id.manga_summary_section_transition)
            summaryTransition.applySystemAnimatorScale(view.context)

            // For rounded corners
            binding.mangaCover.clipToOutline = true

            // SY -->
            mangaTagsInfoAdapter?.mItemClickListener = FlexibleAdapter.OnItemClickListener { _, _ ->
                controller.viewScope.launchUI {
                    toggleMangaInfo()
                }
                false
            }
            // SY <--

            binding.btnFavorite.clicks()
                .onEach { controller.onFavoriteClick() }
                .launchIn(controller.viewScope)

            if (controller.presenter.manga.favorite && controller.presenter.getCategories().isNotEmpty()) {
                binding.btnFavorite.longClicks()
                    .onEach { controller.onCategoriesClick() }
                    .launchIn(controller.viewScope)
            }

            with(binding.btnTracking) {
                if (trackManager.hasLoggedServices()) {
                    isVisible = true

                    if (trackCount > 0) {
                        setIconResource(R.drawable.ic_done_24dp)
                        text = view.context.resources.getQuantityString(
                            R.plurals.num_trackers,
                            trackCount,
                            trackCount
                        )
                        isActivated = true
                    } else {
                        setIconResource(R.drawable.ic_sync_24dp)
                        text = view.context.getString(R.string.manga_tracking_tab)
                        isActivated = false
                    }

                    clicks()
                        .onEach { controller.onTrackingClick() }
                        .launchIn(controller.viewScope)
                } else {
                    isVisible = false
                }
            }

            if (controller.presenter.source is HttpSource) {
                binding.btnWebview.isVisible = true
                binding.btnWebview.clicks()
                    .onEach {
                        if (controller.presenter.source.id == MERGED_SOURCE_ID) {
                            controller.openMergedMangaWebview()
                        } else controller.openMangaInWebView()
                    }
                    .launchIn(controller.viewScope)
            }

            // SY -->
            binding.btnMerge.isVisible = controller.presenter.manga.favorite
            binding.btnMerge.clicks()
                .onEach { controller.openSmartSearch() }
                .launchIn(controller.viewScope)
            // SY <--

            binding.mangaFullTitle.longClicks()
                .onEach {
                    controller.activity?.copyToClipboard(
                        view.context.getString(R.string.title),
                        binding.mangaFullTitle.text.toString()
                    )
                }
                .launchIn(controller.viewScope)

            binding.mangaFullTitle.clicks()
                .onEach {
                    controller.performGlobalSearch(binding.mangaFullTitle.text.toString())
                }
                .launchIn(controller.viewScope)

            binding.mangaAuthor.longClicks()
                .onEach {
                    // SY -->
                    val author = binding.mangaAuthor.text.toString()
                    controller.activity?.copyToClipboard(
                        author,
                        SourceTagsUtil.getWrappedTag(source.id, namespace = "artist", tag = author) ?: author
                    )
                    // SY <--
                }
                .launchIn(controller.viewScope)

            binding.mangaAuthor.clicks()
                .onEach {
                    // SY -->
                    val author = binding.mangaAuthor.text.toString()
                    controller.performGlobalSearch(SourceTagsUtil.getWrappedTag(source.id, namespace = "artist", tag = author) ?: author)
                    // SY <--
                }
                .launchIn(controller.viewScope)

            binding.mangaArtist.longClicks()
                .onEach {
                    // SY -->
                    val artist = binding.mangaArtist.text.toString()
                    controller.activity?.copyToClipboard(
                        artist,
                        SourceTagsUtil.getWrappedTag(source.id, namespace = "artist", tag = artist) ?: artist
                    )
                    // SY <--
                }
                .launchIn(controller.viewScope)

            binding.mangaArtist.clicks()
                .onEach {
                    // SY -->
                    val artist = binding.mangaArtist.text.toString()
                    controller.performGlobalSearch(SourceTagsUtil.getWrappedTag(source.id, namespace = "artist", tag = artist) ?: artist)
                    // SY <--
                }
                .launchIn(controller.viewScope)

            binding.mangaSummaryText.longClicks()
                .onEach {
                    controller.activity?.copyToClipboard(
                        view.context.getString(R.string.description),
                        binding.mangaSummaryText.text.toString()
                    )
                }
                .launchIn(controller.viewScope)

            binding.mangaCover.clicks()
                .onEach {
                    controller.showFullCoverDialog()
                }
                .launchIn(controller.viewScope)

            binding.mangaCover.longClicks()
                .onEach {
                    showCoverOptionsDialog()
                }
                .launchIn(controller.viewScope)

            setMangaInfo(manga, source, meta)
        }

        private fun showCoverOptionsDialog() {
            val options = listOfNotNull(
                R.string.action_share,
                R.string.action_save,
                // Can only edit cover for library manga
                if (manga.favorite) R.string.action_edit else null
            ).map(controller.activity!!::getString).toTypedArray()

            MaterialAlertDialogBuilder(controller.activity!!)
                .setTitle(R.string.manga_cover)
                .setItems(options) { _, item ->
                    when (item) {
                        0 -> controller.shareCover()
                        1 -> controller.saveCover()
                        2 -> controller.changeCover()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        /**
         * Update the view with manga information.
         *
         * @param manga manga object containing information about manga.
         * @param source the source of the manga.
         */
        private fun setMangaInfo(manga: Manga, source: Source?, meta: RaisedSearchMetadata?) {
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
                if (source?.id == MERGED_SOURCE_ID) {
                    text = mergedMangaReferences.map {
                        sourceManager.getOrStub(it.mangaSourceId).toString()
                    }.distinct().joinToString()
                } else /* SY <-- */ if (mangaSource != null) {
                    text = mangaSource
                    setOnClickListener {
                        controller.performSearch(sourceManager.getOrStub(source.id).name)
                    }
                } else {
                    text = view.context.getString(R.string.unknown)
                }
            }

            // Update manga status.
            binding.apply {
                val (statusDrawable, statusString) = when (manga.status) {
                    SManga.ONGOING -> R.drawable.ic_status_ongoing_24dp to R.string.ongoing
                    SManga.COMPLETED -> R.drawable.ic_status_completed_24dp to R.string.completed
                    SManga.LICENSED -> R.drawable.ic_status_licensed_24dp to R.string.licensed
                    // SY --> MangaDex specific statuses
                    SManga.HIATUS -> R.drawable.ic_status_hiatus_24dp to R.string.hiatus
                    SManga.PUBLICATION_COMPLETE -> R.drawable.ic_status_publication_complete_24dp to R.string.publication_complete
                    SManga.CANCELLED -> R.drawable.ic_status_cancelled_24dp to R.string.cancelled
                    // SY <--
                    else -> R.drawable.ic_status_unknown_24dp to R.string.unknown
                }
                mangaStatusIcon.setImageResource(statusDrawable)
                mangaStatus.setText(statusString)
            }

            // Set the favorite drawable to the correct one.
            setFavoriteButtonState(manga.favorite)

            // Set cover if changed.
            binding.backdrop.loadAnyAutoPause(manga)
            binding.mangaCover.loadAnyAutoPause(manga)

            // Manga info section
            val hasInfoContent = !manga.description.isNullOrBlank() || !manga.genre.isNullOrBlank()
            showMangaInfo(hasInfoContent)
            if (hasInfoContent) {
                // Update description TextView.
                binding.mangaSummaryText.text = updateDescription(manga.description, (fromSource || isTablet).not())

                // SY -->
                if (manga.description == "meta") {
                    binding.mangaSummaryText.text = ""
                    /*binding.mangaInfoToggleLess.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        topToBottom = -1
                        bottomToBottom = binding.mangaSummaryText.id
                    }*/
                }
                // SY <--

                // Update genres list
                if (!manga.genre.isNullOrBlank()) {
                    binding.mangaGenresTagsCompactChips.setChips(
                        manga.getGenres(),
                        controller::performGenreSearch
                    )
                    // SY -->
                    // if (source?.getMainSource<NamespaceSource>() != null) {
                    setChipsWithNamespace(
                        manga.getGenres(),
                        meta
                    )
                    // binding.mangaGenresTagsFullChips.isVisible = false
                    /*} else {
                        binding.mangaGenresTagsFullChips.setChips(
                            manga.getGenres(),
                            controller::performGenreSearch
                        )
                        binding.genreGroups.isVisible = false
                    }*/
                    // SY <--
                } else {
                    binding.mangaGenresTagsCompactChips.isVisible = false
                    // binding.mangaGenresTagsFullChips.isVisible = false
                    // SY -->
                    binding.genreGroups.isVisible = false
                    // SY <--
                }

                // Handle showing more or less info
                merge(
                    binding.mangaSummaryText.clicks(),
                    binding.mangaInfoToggleMore.clicks(),
                    binding.mangaInfoToggleLess.clicks(),
                    binding.mangaSummarySection.clicks()
                )
                    .onEach { toggleMangaInfo() }
                    .launchIn(controller.viewScope)

                // Expand manga info if navigated from source listing or explicitly set to
                // (e.g. on tablets)
                if (initialLoad && (fromSource || isTablet)) {
                    toggleMangaInfo()
                    initialLoad = false
                    // wrap_content and autoFixTextSize can cause unwanted behaviour this tries to solve it
                    binding.mangaFullTitle.requestLayout()
                }

                // Refreshes will change the state and it needs to be set to correct state to display correctly
                if (binding.mangaSummaryText.maxLines == maxLines) {
                    binding.mangaSummarySection.transitionToState(R.id.start)
                } else {
                    binding.mangaSummarySection.transitionToState(R.id.end)
                }
            }
        }

        private fun showMangaInfo(visible: Boolean) {
            binding.mangaSummarySection.isVisible = visible
        }

        private fun toggleMangaInfo() {
            val isCurrentlyExpanded = binding.mangaSummaryText.maxLines != maxLines

            if (isCurrentlyExpanded) {
                binding.mangaSummarySection.transitionToStart()
            } else {
                binding.mangaSummarySection.transitionToEnd()
            }

            binding.mangaSummaryText.text = updateDescription(manga.description, isCurrentlyExpanded)

            binding.mangaSummaryText.maxLines = if (isCurrentlyExpanded) {
                maxLines
            } else {
                Int.MAX_VALUE
            }
        }

        private fun updateDescription(description: String?, isCurrentlyExpanded: Boolean): CharSequence? {
            return if (description.isNullOrBlank()) {
                view.context.getString(R.string.unknown)
            } else {
                // Max lines of 3 with a blank line looks whack so we remove
                // any line breaks that is 2 or more and replace it with 1
                // however, don't do this if already expanded because we need those blank lines
                if (!isCurrentlyExpanded) {
                    description
                } else {
                    description
                        .replace(Regex("[\\r\\n]{2,}", setOf(RegexOption.MULTILINE)), "\n")
                }
            }
        }

        // SY -->
        private fun setChipsWithNamespace(genre: List<String>?, meta: RaisedSearchMetadata?) {
            val namespaceTags = when {
                meta != null -> {
                    meta.tags
                        .filterNot { it.type == RaisedSearchMetadata.TAG_TYPE_VIRTUAL }
                        .groupBy { it.namespace }
                        .map { (namespace, tags) ->
                            NamespaceTagsItem(
                                namespace,
                                tags.map {
                                    it.name to it.type
                                }
                            )
                        }
                }
                genre != null -> {
                    if (genre.all { it.contains(':') }) {
                        genre
                            .map { tag ->
                                val index = tag.indexOf(':')
                                tag.substring(0, index).trim() to tag.substring(index + 1).trim()
                            }
                            .groupBy {
                                it.first
                            }
                            .mapValues { group ->
                                group.value.map { it.second to 0 }
                            }
                            .map { (namespace, tags) ->
                                NamespaceTagsItem(namespace, tags)
                            }
                    } else {
                        listOf(NamespaceTagsItem(null, genre.map { it to null }))
                    }
                }
                else -> emptyList()
            }

            mangaTagsInfoAdapter?.updateDataSet(namespaceTags)
        }
        // SY <--

        /**
         * Update favorite button with correct drawable and text.
         *
         * @param isFavorite determines if manga is favorite or not.
         */
        private fun setFavoriteButtonState(isFavorite: Boolean) {
            // Set the Favorite drawable to the correct one.
            // Border drawable if false, filled drawable if true.
            binding.btnFavorite.apply {
                setIconResource(if (isFavorite) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_border_24dp)
                text =
                    context.getString(if (isFavorite) R.string.in_library else R.string.add_to_library)
                isActivated = isFavorite
            }
        }
    }

    fun onDestroyView() {
        metaInfoAdapter = null
        mangaTagsInfoAdapter = null
        binding.metadataView.adapter = null
        binding.genreGroups.adapter = null
    }
}
