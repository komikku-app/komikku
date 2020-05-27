package eu.kanade.tachiyomi.ui.manga.info

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.elvishew.xlog.XLog
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.databinding.MangaInfoControllerBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.library.ChangeMangaCategoriesDialog
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.recent.history.HistoryController
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesController
import eu.kanade.tachiyomi.ui.source.SourceController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.lang.truncateCenter
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.visible
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.HITOMI_SOURCE_ID
import exh.MERGED_SOURCE_ID
import exh.NHENTAI_SOURCE_ID
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import reactivecircus.flowbinding.swiperefreshlayout.refreshes
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * Fragment that shows manga information.
 * Uses R.layout.manga_info_controller.
 * UI related actions should be called from here.
 */
class MangaInfoController(private val fromSource: Boolean = false) :
    NucleusController<MangaInfoControllerBinding, MangaInfoPresenter>(),
    ChangeMangaCategoriesDialog.Listener,
    CoroutineScope {

    private val preferences: PreferencesHelper by injectLazy()

    private val dateFormat: DateFormat by lazy {
        preferences.dateFormat()
    }

    // EXH -->
    private var lastMangaThumbnail: String? = null

    private val smartSearchConfig get() = (parentController as MangaController).smartSearchConfig

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Main

    private val gson: Gson by injectLazy()

    private val sourceManager: SourceManager by injectLazy()
    // EXH <--

    init {
        setHasOptionsMenu(true)
        setOptionsMenuHidden(true)
    }

    override fun createPresenter(): MangaInfoPresenter {
        val ctrl = parentController as MangaController
        return MangaInfoPresenter(
            ctrl.manga!!, ctrl.source!!, ctrl.smartSearchConfig,
            ctrl.chapterCountRelay, ctrl.lastUpdateRelay, ctrl.mangaFavoriteRelay
        )
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = MangaInfoControllerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Set onclickListener to toggle favorite when FAB clicked.
        binding.fabFavorite.clicks()
            .onEach { onFabClick() }
            .launchIn(scope)

        // Set onLongClickListener to manage categories when FAB is clicked.
        binding.fabFavorite.longClicks()
            .onEach { onFabLongClick() }
            .launchIn(scope)

        // Set SwipeRefresh to refresh manga data.
        binding.swipeRefresh.refreshes()
            .onEach { fetchMangaFromSource(manualFetch = true) }
            .launchIn(scope)

        binding.mangaFullTitle.longClicks()
            .onEach {
                copyToClipboard(view.context.getString(R.string.title), binding.mangaFullTitle.text.toString())
            }
            .launchIn(scope)

        binding.mangaFullTitle.clicks()
            .onEach {
                performGlobalSearch(binding.mangaFullTitle.text.toString())
            }
            .launchIn(scope)

        binding.mangaArtist.longClicks()
            .onEach {
                copyToClipboard(binding.mangaArtistLabel.text.toString(), binding.mangaArtist.text.toString())
            }
            .launchIn(scope)

        binding.mangaArtist.clicks()
            .onEach {
                var text = binding.mangaArtist.text.toString()
                if (isEHentaiBasedSource()) {
                    text = wrapTag("artist", text)
                }
                performGlobalSearch(text)
            }
            .launchIn(scope)

        binding.mangaAuthor.longClicks()
            .onEach {
                // EXH Special case E-Hentai/ExHentai to ignore author field (unused)
                if (!isEHentaiBasedSource()) {
                    copyToClipboard(binding.mangaAuthor.text.toString(), binding.mangaAuthor.text.toString())
                }
            }
            .launchIn(scope)

        binding.mangaAuthor.clicks()
            .onEach {
                // EXH Special case E-Hentai/ExHentai to ignore author field (unused)
                if (!isEHentaiBasedSource()) {
                    performGlobalSearch(binding.mangaAuthor.text.toString())
                }
            }
            .launchIn(scope)

        binding.mangaSummary.longClicks()
            .onEach {
                copyToClipboard(view.context.getString(R.string.description), binding.mangaSummary.text.toString())
            }
            .launchIn(scope)

        binding.mangaCover.longClicks()
            .onEach {
                copyToClipboard(view.context.getString(R.string.title), presenter.manga.title)
            }
            .launchIn(scope)

        // EXH -->
        if (smartSearchConfig == null) {
            binding.recommendBtn.visible()
            binding.recommendBtn.clicks()
                .onEach { openRecommends() }
                .launchIn(scope)
        }
        smartSearchConfig?.let { smartSearchConfig ->
            if (smartSearchConfig.origMangaId != null) { binding.mergeBtn.visible() }
            binding.mergeBtn.clicks()
                .onEach {
                    // Init presenter here to avoid threading issues
                    presenter

                    launch {
                        try {
                            val mergedManga = withContext(Dispatchers.IO + NonCancellable) {
                                presenter.smartSearchMerge(presenter.manga, smartSearchConfig.origMangaId!!)
                            }

                            parentController?.router?.pushController(
                                MangaController(
                                    mergedManga,
                                    true,
                                    update = true
                                ).withFadeTransaction()
                            )
                            applicationContext?.toast("Manga merged!")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            else {
                                applicationContext?.toast("Failed to merge manga: ${e.message}")
                            }
                        }
                    }
                }
                .launchIn(scope)
        }
        // EXH <--
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.manga_info, menu)

        if (presenter.source !is HttpSource) {
            menu.findItem(R.id.action_share).isVisible = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // EXH -->
            R.id.action_merge -> openSmartSearch()
            // EXH <--
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_share -> shareManga()
            R.id.action_migrate ->
                PreMigrationController.navigateToMigration(
                    preferences.skipPreMigration().get(),
                    router,
                    listOf(presenter.manga.id!!)
                )
        }
        return super.onOptionsItemSelected(item)
    }

    // EXH -->
    private fun openSmartSearch() {
        val smartSearchConfig = SourceController.SmartSearchConfig(presenter.manga.title, presenter.manga.id!!)

        parentController?.router?.pushController(
            SourceController(
                Bundle().apply {
                    putParcelable(SourceController.SMART_SEARCH_CONFIG, smartSearchConfig)
                }
            ).withFadeTransaction()
        )
    }
    // EXH <--

    // AZ -->
    private fun openRecommends() {
        val recommendsConfig = BrowseSourceController.RecommendsConfig(presenter.manga.title, presenter.manga.source)

        parentController?.router?.pushController(
            BrowseSourceController(
                Bundle().apply {
                    putParcelable(BrowseSourceController.RECOMMENDS_CONFIG, recommendsConfig)
                }
            ).withFadeTransaction()
        )
    }
    // AZ <--

    /**
     * Check if manga is initialized.
     * If true update view with manga information,
     * if false fetch manga information
     *
     * @param manga manga object containing information about manga.
     * @param source the source of the manga.
     */
    fun onNextManga(manga: Manga, source: Source) {
        if (manga.initialized) {
            // Update view.
            setMangaInfo(manga, source)

            if ((parentController as MangaController).update) fetchMangaFromSource()
        } else {
            // Initialize manga.
            fetchMangaFromSource()
        }
    }

    /**
     * Update the view with manga information.
     *
     * @param manga manga object containing information about manga.
     * @param source the source of the manga.
     */
    private fun setMangaInfo(manga: Manga, source: Source?) {
        val view = view ?: return

        // TODO Duplicated in MigrationProcedureAdapter

        // update full title TextView.
        binding.mangaFullTitle.text = if (manga.title.isBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.title
        }

        // Update artist TextView.
        binding.mangaArtist.text = if (manga.artist.isNullOrBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.artist
        }

        // Update author TextView.
        binding.mangaAuthor.text = if (manga.author.isNullOrBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.author
        }

        // If manga source is known update source TextView.
        if (source == null) {
            binding.mangaSource.text = view.context.getString(R.string.unknown)
            // EXH -->
        } else if (source.id == MERGED_SOURCE_ID) {
            binding.mangaSource.text = MergedSource.MangaConfig.readFromUrl(gson, manga.url).children.map {
                sourceManager.getOrStub(it.source).toString()
            }.distinct().joinToString()
            // EXH <--
        } else {
            val mangaSource = source.toString()
            with(binding.mangaSource) {
                text = mangaSource
                setOnClickListener {
                    val sourceManager = Injekt.get<SourceManager>()
                    performSearch(sourceManager.getOrStub(source.id).name)
                }
            }
        }

        // EXH -->
        if (source?.id == MERGED_SOURCE_ID) {
            binding.mangaSourceLabel.setText(R.string.label_sources)
        } else {
            binding.mangaSourceLabel.setText(R.string.manga_info_source_label)
        }
        // EXH <--

        // Update genres list
        if (!manga.genre.isNullOrBlank()) {
            binding.mangaGenresTags.removeAllViews()

            manga.getGenres()?.forEach { genre ->
                val chip = Chip(view.context).apply {
                    text = genre
                    setOnClickListener {
                        var text = genre
                        if (isEHentaiBasedSource() || presenter.source.id == NHENTAI_SOURCE_ID || presenter.source.id == HITOMI_SOURCE_ID) {
                            val parsed = parseTag(text)
                            text = wrapTag(parsed.first, parsed.second.substringBefore('|').trim())
                        }
                        performSearch(text)
                    }

                    setOnLongClickListener {
                        var text = genre
                        if (isEHentaiBasedSource() || presenter.source.id == NHENTAI_SOURCE_ID || presenter.source.id == HITOMI_SOURCE_ID) {
                            val parsed = parseTag(text)
                            text = wrapTag(parsed.first, parsed.second.substringBefore('|').trim())
                        }
                        performGlobalSearch(text)
                        false
                    }
                }

                binding.mangaGenresTags.addView(chip)
            }
        }

        // Update description TextView.
        binding.mangaSummary.text = if (manga.description.isNullOrBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.description
        }

        // Update status TextView.
        binding.mangaStatus.setText(
            when (manga.status) {
                SManga.ONGOING -> R.string.ongoing
                SManga.COMPLETED -> R.string.completed
                SManga.LICENSED -> R.string.licensed
                else -> R.string.unknown
            }
        )

        // Set the favorite drawable to the correct one.
        setFavoriteDrawable(manga.favorite)

        binding.mangaCoverCard.radius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            preferences.eh_library_corner_radius().getOrDefault().toFloat(),
            view.context.resources.displayMetrics
        )

        // Set cover if it wasn't already.
        val mangaThumbnail = manga.toMangaThumbnail()

        GlideApp.with(view.context)
            .load(mangaThumbnail)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(binding.mangaCover)

        binding.backdrop?.let {
            GlideApp.with(view.context)
                .load(mangaThumbnail)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .into(it)
        }
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
            binding.mangaChapters.text = resources?.getString(R.string.unknown)
        }
    }

    fun setLastUpdateDate(date: Date) {
        if (date.time != 0L) {
            binding.mangaLastUpdate.text = dateFormat.format(date)
        } else {
            binding.mangaLastUpdate.text = resources?.getString(R.string.unknown)
        }
    }

    /**
     * Toggles the favorite status and asks for confirmation to delete downloaded chapters.
     */
    private fun toggleFavorite() {
        val view = view

        val isNowFavorite = presenter.toggleFavorite()
        if (view != null && !isNowFavorite && presenter.hasDownloads()) {
            view.snack(view.context.getString(R.string.delete_downloads_for_manga)) {
                setAction(R.string.action_delete) {
                    presenter.deleteDownloads()
                }
            }
        }
    }

    private fun openInWebView() {
        val source = presenter.source as? HttpSource ?: return

        val url = try {
            source.mangaDetailsRequest(presenter.manga).url.toString()
        } catch (e: Exception) {
            return
        }

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(activity, url, source.id, presenter.manga.title)
        startActivity(intent)
    }

    /**
     * Called to run Intent with [Intent.ACTION_SEND], which show share dialog.
     */
    private fun shareManga() {
        val context = view?.context ?: return

        val source = presenter.source as? HttpSource ?: return
        try {
            val url = source.mangaDetailsRequest(presenter.manga).url.toString()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Update FAB with correct drawable.
     *
     * @param isFavorite determines if manga is favorite or not.
     */
    private fun setFavoriteDrawable(isFavorite: Boolean) {
        // Set the Favorite drawable to the correct one.
        // Border drawable if false, filled drawable if true.
        binding.fabFavorite.setImageResource(
            if (isFavorite) {
                R.drawable.ic_bookmark_24dp
            } else {
                R.drawable.ic_add_to_library_24dp
            }
        )
    }

    /**
     * Start fetching manga information from source.
     */
    private fun fetchMangaFromSource(manualFetch: Boolean = false) {
        setRefreshing(true)
        // Call presenter and start fetching manga information
        presenter.fetchMangaFromSource(manualFetch)
    }

    /**
     * Update swipe refresh to stop showing refresh in progress spinner.
     */
    fun onFetchMangaDone() {
        setRefreshing(false)
    }

    /**
     * Update swipe refresh to start showing refresh in progress spinner.
     */
    fun onFetchMangaError(error: Throwable) {
        setRefreshing(false)
        activity?.toast(error.message)

        // [EXH]
        XLog.w("> Failed to fetch manga details!", error)
        XLog.w(
            "> (source.id: %s, source.name: %s, manga.id: %s, manga.url: %s)",
            presenter.source.id,
            presenter.source.name,
            presenter.manga.id,
            presenter.manga.url
        )
    }

    /**
     * Set swipe refresh status.
     *
     * @param value whether it should be refreshing or not.
     */
    private fun setRefreshing(value: Boolean) {
        binding.swipeRefresh.isRefreshing = value
    }

    private fun onFabClick() {
        val manga = presenter.manga

        if (manga.favorite) {
            toggleFavorite()
            activity?.toast(activity?.getString(R.string.manga_removed_library))
        } else {
            val categories = presenter.getCategories()
            val defaultCategoryId = preferences.defaultCategory()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    toggleFavorite()
                    presenter.moveMangaToCategory(manga, defaultCategory)
                    activity?.toast(activity?.getString(R.string.manga_added_library))
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    toggleFavorite()
                    presenter.moveMangaToCategory(manga, null)
                    activity?.toast(activity?.getString(R.string.manga_added_library))
                }

                // Choose a category
                else -> {
                    val ids = presenter.getMangaCategoryIds(manga)
                    val preselected = ids.mapNotNull { id ->
                        categories.indexOfFirst { it.id == id }.takeIf { it != -1 }
                    }.toTypedArray()

                    ChangeMangaCategoriesDialog(this, listOf(manga), categories, preselected)
                        .showDialog(router)
                }
            }
        }
    }

    private fun onFabLongClick() {
        val manga = presenter.manga

        if (manga.favorite && presenter.getCategories().isNotEmpty()) {
            val categories = presenter.getCategories()

            val ids = presenter.getMangaCategoryIds(manga)
            val preselected = ids.mapNotNull { id ->
                categories.indexOfFirst { it.id == id }.takeIf { it != -1 }
            }.toTypedArray()

            ChangeMangaCategoriesDialog(this, listOf(manga), categories, preselected)
                .showDialog(router)
        } else {
            onFabClick()
        }
    }

    override fun updateCategoriesForMangas(mangas: List<Manga>, categories: List<Category>) {
        val manga = mangas.firstOrNull() ?: return

        if (!manga.favorite) {
            toggleFavorite()
        }

        presenter.moveMangaToCategories(manga, categories)
        activity?.toast(activity?.getString(R.string.manga_added_library))
    }

    /**
     * Copies a string to clipboard
     *
     * @param label Label to show to the user describing the content
     * @param content the actual text to copy to the board
     */
    private fun copyToClipboard(label: String, content: String) {
        if (content.isBlank()) return

        val activity = activity ?: return
        val view = view ?: return

        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))

        activity.toast(
            view.context.getString(R.string.copied_to_clipboard, content.truncateCenter(20)),
            Toast.LENGTH_SHORT
        )
    }

    /**
     * Perform a global search using the provided query.
     *
     * @param query the search query to pass to the search controller
     */
    fun performGlobalSearch(query: String) {
        val router = parentController?.router ?: return
        router.pushController(GlobalSearchController(query).withFadeTransaction())
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private fun performSearch(query: String) {
        val router = parentController?.router ?: return

        if (router.backstackSize < 2) {
            return
        }

        when (val previousController = router.backstack[router.backstackSize - 2].controller()) {
            is LibraryController -> {
                router.handleBack()
                previousController.search(query)
            }
            is UpdatesController,
            is HistoryController -> {
                // Manually navigate to LibraryController
                router.handleBack()
                (router.activity as MainActivity).setSelectedDrawerItem(R.id.nav_drawer_library)
                val controller = router.getControllerWithTag(R.id.nav_drawer_library.toString()) as LibraryController
                controller.search(query)
            }
            is BrowseSourceController -> {
                router.handleBack()
                previousController.searchWithQuery(query)
            }
        }
    }

    // --> EH
    private fun wrapTag(namespace: String, tag: String) = if (tag.contains(' ')) {
        "$namespace:\"$tag$\""
    } else {
        "$namespace:$tag$"
    }

    private fun parseTag(tag: String) = tag.substringBefore(':').trim() to tag.substringAfter(':').trim()

    private fun isEHentaiBasedSource(): Boolean {
        val sourceId = presenter.source.id
        return sourceId == EH_SOURCE_ID ||
            sourceId == EXH_SOURCE_ID
    }
    // <-- EH
}
