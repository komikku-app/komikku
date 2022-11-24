package eu.kanade.tachiyomi.ui.browse.source.browse

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.BrowseSourceScreen
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.DuplicateMangaDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesController
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter.Dialog
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput
import exh.savedsearches.EXHSavedSearch

open class BrowseSourceController(bundle: Bundle) :
    FullComposeController<BrowseSourcePresenter>(bundle) {

    constructor(
        sourceId: Long,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, sourceId)
            if (query != null) {
                putString(SEARCH_QUERY_KEY, query)
            }

            // SY -->
            if (smartSearchConfig != null) {
                putSerializable(SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
            }

            if (savedSearch != null) {
                putLong(SAVED_SEARCH_CONFIG_KEY, savedSearch)
            }

            if (filterList != null) {
                putString(FILTERS_CONFIG_KEY, filterList)
            }
            // SY <--
        },
    )

    constructor(
        source: CatalogueSource,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        source.id,
        query,
        smartSearchConfig,
        savedSearch,
        filterList,
    )

    constructor(
        source: Source,
        query: String? = null,
        // SY -->
        smartSearchConfig: SourcesController.SmartSearchConfig? = null,
        savedSearch: Long? = null,
        filterList: String? = null,
        // SY <--
    ) : this(
        source.id,
        query,
        smartSearchConfig,
        savedSearch,
        filterList,
    )

    /**
     * Sheet containing filter items.
     */
    protected var filterSheet: SourceFilterSheet? = null

    override fun createPresenter(): BrowseSourcePresenter {
        // SY -->
        return BrowseSourcePresenter(
            args.getLong(SOURCE_ID_KEY),
            args.getString(SEARCH_QUERY_KEY),
            filtersJson = args.getString(FILTERS_CONFIG_KEY),
            savedSearch = args.getLong(SAVED_SEARCH_CONFIG_KEY, 0).takeUnless { it == 0L },
        )
        // SY <--
    }

    @Composable
    override fun ComposeContent() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current

        BrowseSourceScreen(
            presenter = presenter,
            navigateUp = ::navigateUp,
            openFilterSheet = { filterSheet?.show() },
            onMangaClick = { router.pushController(MangaController(it.id, true)) },
            onMangaLongClick = { manga ->
                scope.launchIO {
                    val duplicateManga = presenter.getDuplicateLibraryManga(manga)
                    when {
                        manga.favorite -> presenter.dialog = Dialog.RemoveManga(manga)
                        duplicateManga != null -> presenter.dialog = Dialog.AddDuplicateManga(manga, duplicateManga)
                        else -> presenter.addFavorite(manga)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onWebViewClick = f@{
                val source = presenter.source as? HttpSource ?: return@f
                val intent = WebViewActivity.newIntent(context, source.baseUrl, source.id, source.name)
                context.startActivity(intent)
            },
            // SY -->
            onSettingsClick = {
                router.pushController(SourcePreferencesController(presenter.source!!.id))
            },
            // SY <--
            incognitoMode = presenter.isIncognitoMode,
            downloadedOnlyMode = presenter.isDownloadOnly,
        )

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            is Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { presenter.addFavorite(dialog.manga) },
                    onOpenManga = { router.pushController(MangaController(dialog.duplicate.id)) },
                    duplicateFrom = presenter.getSourceOrStub(dialog.duplicate),
                )
            }
            is Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        presenter.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        router.pushController(CategoryController())
                    },
                    onConfirm = { include, _ ->
                        presenter.changeMangaFavorite(dialog.manga)
                        presenter.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            null -> {}
        }

        BackHandler(onBack = ::navigateUp)

        LaunchedEffect(presenter.filters) {
            initFilterSheet()
        }
    }

    private fun navigateUp() {
        when {
            !presenter.isUserQuery && presenter.searchQuery != null -> presenter.searchQuery = null
            else -> router.popCurrentController()
        }
    }

    fun setSavedSearches(savedSearches: List<EXHSavedSearch>) {
        filterSheet?.setSavedSearches(savedSearches)
    }

    open fun initFilterSheet() {
        filterSheet = SourceFilterSheet(
            activity!!,
            // SY -->
            this,
            presenter.source!!,
            emptyList(),
            // SY <--
            onFilterClicked = {
                presenter.search(filters = presenter.filters)
            },
            onResetClicked = {
                filterSheet?.dismiss()
                presenter.reset()
            },
            // EXH -->
            onSaveClicked = {
                viewScope.launchUI {
                    filterSheet?.context?.let {
                        val names = presenter.loadSearches().map { it.name }
                        var searchName = ""
                        MaterialAlertDialogBuilder(it)
                            .setTitle(R.string.save_search)
                            .setTextInput(hint = it.getString(R.string.save_search_hint)) { input ->
                                searchName = input
                            }
                            .setPositiveButton(R.string.action_save) { _, _ ->
                                if (searchName.isNotBlank() && searchName !in names) {
                                    presenter.saveSearch(
                                        name = searchName.trim(),
                                        query = presenter.currentFilter.query,
                                        filterList = presenter.currentFilter.filters.ifEmpty { presenter.source!!.getFilterList() },
                                    )
                                } else {
                                    it.toast(R.string.save_search_invalid_name)
                                }
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                    }
                }
            },
            onSavedSearchClicked = { idOfSearch ->
                viewScope.launchUI {
                    val search = presenter.loadSearch(idOfSearch)

                    if (search == null) {
                        filterSheet?.context?.let {
                            MaterialAlertDialogBuilder(it)
                                .setTitle(R.string.save_search_failed_to_load)
                                .setMessage(R.string.save_search_failed_to_load_message)
                                .show()
                        }
                        return@launchUI
                    }

                    if (search.filterList == null && presenter.filters.isNotEmpty()) {
                        activity?.toast(R.string.save_search_invalid)
                        return@launchUI
                    }

                    val allDefault = search.filterList != null && presenter.filters == presenter.source!!.getFilterList()
                    filterSheet?.dismiss()

                    presenter.search(
                        query = search.query,
                        filters = if (allDefault) null else search.filterList,
                    )
                }
            },
            onSavedSearchDeleteClicked = { idToDelete, name ->
                filterSheet?.context?.let {
                    MaterialAlertDialogBuilder(it)
                        .setTitle(R.string.save_search_delete)
                        .setMessage(it.getString(R.string.save_search_delete_message, name))
                        .setPositiveButton(R.string.action_cancel, null)
                        .setNegativeButton(android.R.string.ok) { _, _ ->
                            presenter.deleteSearch(idToDelete)
                        }
                        .show()
                }
            },
            // EXH <--
        )
        launchUI {
            filterSheet?.setSavedSearches(presenter.loadSearches())
        }
        filterSheet?.setFilters(presenter.filterItems)
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    fun searchWithQuery(newQuery: String) {
        presenter.search(newQuery)
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String) {
        val defaultFilters = presenter.source!!.getFilterList()

        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is Filter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is Filter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is Filter.TriState -> filter.state = 1
                            is Filter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is Filter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }

        if (genreExists) {
            filterSheet?.setFilters(defaultFilters.toItems())

            presenter.search(filters = defaultFilters)
        } else {
            searchWithQuery(genreName)
        }
    }

    protected companion object {
        const val SOURCE_ID_KEY = "sourceId"
        const val SEARCH_QUERY_KEY = "searchQuery"

        // SY -->
        const val SMART_SEARCH_CONFIG_KEY = "smartSearchConfig"
        const val SAVED_SEARCH_CONFIG_KEY = "savedSearch"
        const val FILTERS_CONFIG_KEY = "filters"
        // SY <--
    }
}
