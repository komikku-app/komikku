package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.MangaAllInOneHeaderBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.browse.source.SourceController
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import exh.MERGED_SOURCE_ID
import exh.util.setChipsExtended
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangaAllInOneHolder(
    view: View,
    private val adapter: MangaAllInOneAdapter,
    smartSearchConfig: SourceController.SmartSearchConfig? = null
) : BaseFlexibleViewHolder(view, adapter) {

    private val preferences: PreferencesHelper by injectLazy()

    private val gson: Gson by injectLazy()

    private val dateFormat: DateFormat by lazy {
        preferences.dateFormat()
    }

    private val sourceManager: SourceManager by injectLazy()

    var binding: MangaAllInOneHeaderBinding

    init {
        val presenter = adapter.delegate.mangaPresenter()

        binding = MangaAllInOneHeaderBinding.bind(itemView)

        // Setting this via XML doesn't work
        binding.mangaCover.clipToOutline = true

        binding.btnFavorite.clicks()
            .onEach { adapter.delegate.onFavoriteClick() }
            .launchIn(adapter.delegate.controllerScope)

        if ((Injekt.get<TrackManager>().hasLoggedServices())) {
            binding.btnTracking.visible()
        }

        adapter.delegate.controllerScope.launch(Dispatchers.IO) {
            setTrackingIcon(
                Injekt.get<DatabaseHelper>().getTracks(presenter.manga).executeAsBlocking().any {
                    val status = Injekt.get<TrackManager>().getService(it.sync_id)?.getStatus(it.status)
                    status != null
                }
            )
        }

        binding.btnTracking.clicks()
            .onEach { adapter.delegate.openTracking() }
            .launchIn(adapter.delegate.controllerScope)

        if (presenter.manga.favorite && presenter.getCategories().isNotEmpty()) {
            binding.btnCategories.visible()
        }
        binding.btnCategories.clicks()
            .onEach { adapter.delegate.onCategoriesClick() }
            .launchIn(adapter.delegate.controllerScope)

        if (presenter.source is HttpSource) {
            binding.btnWebview.visible()
            binding.btnShare.visible()

            binding.btnWebview.clicks()
                .onEach { adapter.delegate.openInWebView() }
                .launchIn(adapter.delegate.controllerScope)
            binding.btnShare.clicks()
                .onEach { adapter.delegate.shareManga() }
                .launchIn(adapter.delegate.controllerScope)
        }

        if (presenter.manga.favorite) {
            binding.btnMigrate.visible()
            binding.btnSmartSearch.visible()
        }

        binding.btnMigrate.clicks()
            .onEach {
                adapter.delegate.migrateManga()
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.btnSmartSearch.clicks()
            .onEach { adapter.delegate.openSmartSearch() }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaFullTitle.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(view.context.getString(R.string.title), binding.mangaFullTitle.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaFullTitle.clicks()
            .onEach {
                adapter.delegate.performGlobalSearch(binding.mangaFullTitle.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaArtist.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(binding.mangaArtistLabel.text.toString(), binding.mangaArtist.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaArtist.clicks()
            .onEach {
                var text = binding.mangaArtist.text.toString()
                if (adapter.delegate.isEHentaiBasedSource()) {
                    text = adapter.delegate.wrapTag("artist", text)
                }
                adapter.delegate.performGlobalSearch(text)
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaAuthor.longClicks()
            .onEach {
                // EXH Special case E-Hentai/ExHentai to ignore author field (unused)
                if (!adapter.delegate.isEHentaiBasedSource()) {
                    adapter.delegate.copyToClipboard(binding.mangaAuthorLabel.text.toString(), binding.mangaAuthor.text.toString())
                }
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaAuthor.clicks()
            .onEach {
                // EXH Special case E-Hentai/ExHentai to ignore author field (unused)
                if (!adapter.delegate.isEHentaiBasedSource()) {
                    adapter.delegate.performGlobalSearch(binding.mangaAuthor.text.toString())
                }
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaSummary.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(view.context.getString(R.string.description), binding.mangaSummary.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        binding.mangaCover.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(view.context.getString(R.string.title), presenter.manga.title)
            }
            .launchIn(adapter.delegate.controllerScope)

        // EXH -->
        if (smartSearchConfig == null) {
            binding.recommendBtn.visible()
            binding.recommendBtn.clicks()
                .onEach { adapter.delegate.openRecommends() }
                .launchIn(adapter.delegate.controllerScope)
        }
        smartSearchConfig?.let {
            if (it.origMangaId != null) { binding.mergeBtn.visible() }
            binding.mergeBtn.clicks()
                .onEach {
                    adapter.delegate.mergeWithAnother()
                }

                .launchIn(adapter.delegate.controllerScope)
        }
        // EXH <--
    }

    fun bind(manga: Manga, source: Source?) {
        binding.mangaFullTitle.text = if (manga.title.isBlank()) {
            itemView.context.getString(R.string.unknown)
        } else {
            manga.title
        }

        // Update artist TextView.
        binding.mangaArtist.text = if (manga.artist.isNullOrBlank()) {
            itemView.context.getString(R.string.unknown)
        } else {
            manga.artist
        }

        // Update author TextView.
        binding.mangaAuthor.text = if (manga.author.isNullOrBlank()) {
            itemView.context.getString(R.string.unknown)
        } else {
            manga.author
        }

        // If manga source is known update source TextView.
        val mangaSource = source?.toString()
        with(binding.mangaSource) {
            // EXH -->
            when {
                mangaSource == null -> {
                    text = itemView.context.getString(R.string.unknown)
                }
                source.id == MERGED_SOURCE_ID -> {
                    text = eu.kanade.tachiyomi.source.online.all.MergedSource.MangaConfig.readFromUrl(gson, manga.url).children.map {
                        sourceManager.getOrStub(it.source).toString()
                    }.distinct().joinToString()
                }
                else -> {
                    text = mangaSource
                    setOnClickListener {
                        val sourceManager = Injekt.get<SourceManager>()
                        adapter.delegate.performSearch(sourceManager.getOrStub(source.id).name)
                    }
                }
            }
            // EXH <--
        }

        // EXH -->
        if (source?.id == MERGED_SOURCE_ID) {
            binding.mangaSourceLabel.setText(R.string.label_sources)
        } else {
            binding.mangaSourceLabel.setText(R.string.manga_info_source_label)
        }
        // EXH <--

        // Update status TextView.
        binding.mangaStatus.setText(
            when (manga.status) {
                SManga.ONGOING -> R.string.ongoing
                SManga.COMPLETED -> R.string.completed
                SManga.LICENSED -> R.string.licensed
                else -> R.string.unknown
            }
        )

        setChapterCount(0F)
        setLastUpdateDate(Date(0L))

        // Set the favorite drawable to the correct one.
        setFavoriteButtonState(manga.favorite)

        // Set cover if it wasn't already.
        val mangaThumbnail = manga.toMangaThumbnail()

        GlideApp.with(itemView.context)
            .load(mangaThumbnail)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(binding.mangaCover)

        GlideApp.with(itemView.context)
            .load(mangaThumbnail)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(binding.backdrop)

        // Manga info section
        if (manga.description.isNullOrBlank() && manga.genre.isNullOrBlank()) {
            hideMangaInfo()
        } else {
            // Update description TextView.
            binding.mangaSummary.text = if (manga.description.isNullOrBlank()) {
                itemView.context.getString(R.string.unknown)
            } else {
                manga.description
            }

            // Update genres list
            if (!manga.genre.isNullOrBlank()) {
                binding.mangaGenresTagsCompactChips.setChipsExtended(manga.getGenres(), this::performSearch, this::performGlobalSearch, manga.source)
                binding.mangaGenresTagsFullChips.setChipsExtended(manga.getGenres(), this::performSearch, this::performGlobalSearch, manga.source)
            } else {
                binding.mangaGenresTagsWrapper.gone()
            }

            // Handle showing more or less info
            binding.mangaSummary.clicks()
                .onEach { toggleMangaInfo(itemView.context) }
                .launchIn(adapter.delegate.controllerScope)
            binding.mangaInfoToggle.clicks()
                .onEach { toggleMangaInfo(itemView.context) }
                .launchIn(adapter.delegate.controllerScope)

            // Expand manga info if navigated from source listing
            if (adapter.delegate.isInitialLoadAndFromSource()) {
                adapter.delegate.removeInitialLoad()
                toggleMangaInfo(itemView.context)
            }
        }
    }

    private fun hideMangaInfo() {
        binding.mangaSummaryLabel.gone()
        binding.mangaSummary.gone()
        binding.mangaGenresTagsWrapper.gone()
        binding.mangaInfoToggle.gone()
    }

    private fun toggleMangaInfo(context: Context) {
        val isExpanded = binding.mangaInfoToggle.text == context.getString(R.string.manga_info_collapse)

        binding.mangaInfoToggle.text =
            if (isExpanded) {
                context.getString(R.string.manga_info_expand)
            } else {
                context.getString(R.string.manga_info_collapse)
            }

        with(binding.mangaSummary) {
            maxLines =
                if (isExpanded) {
                    3
                } else {
                    Int.MAX_VALUE
                }

            ellipsize =
                if (isExpanded) {
                    android.text.TextUtils.TruncateAt.END
                } else {
                    null
                }
        }

        binding.mangaGenresTagsCompact.visibleIf { isExpanded }
        binding.mangaGenresTagsFullChips.visibleIf { !isExpanded }
    }

    /**
     * Update chapter count TextView.
     *
     * @param count number of chapters.
     */
    fun setChapterCount(count: Float) {
        if (count > 0f) {
            binding.mangaChapters.text = DecimalFormat("#.#").format(count)
        } else {
            binding.mangaChapters.text = itemView.context.getString(R.string.unknown)
        }
    }

    fun setLastUpdateDate(date: Date) {
        if (date.time != 0L) {
            binding.mangaLastUpdate.text = dateFormat.format(date)
        } else {
            binding.mangaLastUpdate.text = itemView.context.getString(R.string.unknown)
        }
    }

    /**
     * Toggles the favorite status and asks for confirmation to delete downloaded chapters.
     */
    fun toggleFavorite() {
        val presenter = adapter.delegate.mangaPresenter()

        val isNowFavorite = presenter.toggleFavorite()
        if (!isNowFavorite && presenter.hasDownloads()) {
            itemView.snack(itemView.context.getString(R.string.delete_downloads_for_manga)) {
                setAction(R.string.action_delete) {
                    presenter.deleteDownloads()
                }
            }
        }

        binding.btnCategories.visibleIf { isNowFavorite && presenter.getCategories().isNotEmpty() }
        if (isNowFavorite) {
            binding.btnSmartSearch.visible()
            binding.btnMigrate.visible()
        } else {
            binding.btnSmartSearch.gone()
            binding.btnMigrate.gone()
        }
    }

    /**
     * Update favorite button with correct drawable and text.
     *
     * @param isFavorite determines if manga is favorite or not.
     */
    fun setFavoriteButtonState(isFavorite: Boolean) {
        // Set the Favorite drawable to the correct one.
        // Border drawable if false, filled drawable if true.
        binding.btnFavorite.apply {
            icon = ContextCompat.getDrawable(context, if (isFavorite) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_border_24dp)
            text = context.getString(if (isFavorite) R.string.in_library else R.string.add_to_library)
            isChecked = isFavorite
        }
    }

    private fun performSearch(query: String) {
        adapter.delegate.performSearch(query)
    }

    private fun performGlobalSearch(query: String) {
        adapter.delegate.performGlobalSearch(query)
    }

    fun setTrackingIcon(tracked: Boolean) {
        if (tracked) {
            binding.btnTracking.setIconResource(R.drawable.ic_cloud_24dp)
        }
    }
}
