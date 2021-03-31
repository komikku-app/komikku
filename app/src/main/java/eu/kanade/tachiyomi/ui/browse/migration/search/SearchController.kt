package eu.kanade.tachiyomi.ui.browse.migration.search

import android.app.Dialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListController
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationInterface
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchPresenter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.QueryTextEvent
import reactivecircus.flowbinding.appcompat.queryTextEvents
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SearchController(
    private var manga: Manga? = null,
    private var sources: List<CatalogueSource>? = null
) : GlobalSearchController(
    manga?.originalTitle,
    bundle = bundleOf(
        OLD_MANGA to manga?.id,
        SOURCES to sources?.map { it.id }?.toLongArray()
    )
) {

    private var newManga: Manga? = null
    private var progress = 1
    var totalProgress = 0

    constructor(mangaId: Long, sources: LongArray):
        this(
            Injekt.get<DatabaseHelper>().getManga(mangaId).executeAsBlocking(),
            sources.map { Injekt.get<SourceManager>().getOrStub(it) }.filterIsInstance<CatalogueSource>()
        )

    @Suppress("unused")
    constructor(bundle: Bundle): this(
        bundle.getLong(OLD_MANGA),
        bundle.getLongArray(SOURCES) ?: LongArray(0)
    )

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return if (totalProgress > 1) {
            "($progress/$totalProgress) ${super.getTitle()}"
        } else {
            super.getTitle()
        }
    }

    override fun createPresenter(): GlobalSearchPresenter {
        return SearchPresenter(
            initialQuery,
            manga!!,
            sources
        )
    }

    fun migrateManga(manga: Manga, newManga: Manga) {
        val target = targetController as? MigrationInterface ?: return

        val nextManga = target.migrateManga(manga, newManga, true)
        replaceWithNewSearchController(nextManga)
    }

    fun copyManga(manga: Manga, newManga: Manga) {
        val target = targetController as? MigrationInterface ?: return

        val nextManga = target.migrateManga(manga, newManga, false)
        replaceWithNewSearchController(nextManga)
    }

    private fun replaceWithNewSearchController(manga: Manga?) {
        if (manga != null) {
            // router.popCurrentController()
            val searchController = SearchController(manga)
            searchController.targetController = targetController
            searchController.progress = progress + 1
            searchController.totalProgress = totalProgress
            router.replaceTopController(searchController.withFadeTransaction())
        } else router.popController(this)
    }

    override fun onMangaClick(manga: Manga) {
        if (targetController is MigrationListController) {
            val migrationListController = targetController as? MigrationListController
            val sourceManager = Injekt.get<SourceManager>()
            val source = sourceManager.get(manga.source) ?: return
            migrationListController?.useMangaForMigration(manga, source)
            router.popCurrentController()
            return
        }
        newManga = manga
        val dialog =
            MigrationDialog(this.manga ?: return, newManga ?: return, this)
        dialog.targetController = this
        dialog.showDialog(router)
    }

    override fun onMangaLongClick(manga: Manga) {
        // Call parent's default click listener
        super.onMangaClick(manga)
    }

    class MigrationDialog(bundle: Bundle) : DialogController(bundle) {

        constructor(manga: Manga, newManga: Manga, callingController: Controller) : this(
            bundleOf(
                MANGA_KEY to manga,
                NEW_MANGA_KEY to newManga
            )
        ) {
            this.callingController = callingController
        }

        private val manga: Manga = args.getSerializable(MANGA_KEY) as Manga
        private val newManga: Manga = args.getSerializable(NEW_MANGA_KEY) as Manga
        private var callingController: Controller? = null

        private val preferences: PreferencesHelper by injectLazy()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val prefValue = preferences.migrateFlags().get()
            val callingController = callingController

            val preselected =
                MigrationFlags.getEnabledFlagsPositions(
                    prefValue
                )

            return MaterialDialog(activity!!)
                .title(R.string.migration_dialog_what_to_include)
                .listItemsMultiChoice(
                    items = MigrationFlags.titles.map { resources?.getString(it) as CharSequence },
                    initialSelection = preselected.toIntArray()
                ) { _, positions, _ ->
                    // Save current settings for the next time
                    val newValue =
                        MigrationFlags.getFlagsFromPositions(
                            positions.toTypedArray()
                        )
                    preferences.migrateFlags().set(newValue)
                }
                .positiveButton(R.string.migrate) {
                    if (callingController != null) {
                        if (callingController.javaClass == SourceSearchController::class.java) {
                            router.popController(callingController)
                        }
                    }
                    (targetController as? SearchController)?.migrateManga(manga, newManga)
                }
                .negativeButton(R.string.copy) {
                    if (callingController != null) {
                        if (callingController.javaClass == SourceSearchController::class.java) {
                            router.popController(callingController)
                        }
                    }
                    (targetController as? SearchController)?.copyManga(manga, newManga)
                }
                .neutralButton(android.R.string.cancel)
        }
        companion object {
            const val MANGA_KEY = "manga_key"
            const val NEW_MANGA_KEY = "new_manga_key"
        }
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu.
        inflater.inflate(R.menu.global_search, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchItem.fixExpand({
            searchView.onActionViewExpanded() // Required to show the query in the view
            searchView.setQuery(presenter.query, false)
            true
        })

        searchView.queryTextEvents()
            .filter { it is QueryTextEvent.QuerySubmitted }
            .onEach {
                presenter.search(it.queryText.toString())
                searchItem.collapseActionView()
                setTitle() // Update toolbar title
            }
            .launchIn(viewScope)
    }

    override fun onTitleClick(source: CatalogueSource) {
        presenter.preferences.lastUsedSource().set(source.id)

        router.pushController(SourceSearchController(manga!!, source, presenter.query).withFadeTransaction())
    }

    companion object {
        const val OLD_MANGA = "old_manga"
        const val SOURCES = "sources"
    }
}
