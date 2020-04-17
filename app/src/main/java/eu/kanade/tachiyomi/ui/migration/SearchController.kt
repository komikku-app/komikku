package eu.kanade.tachiyomi.ui.migration

import android.app.Dialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import com.afollestad.materialdialogs.MaterialDialog
import com.jakewharton.rxbinding.support.v7.widget.queryTextChangeEvents
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.popControllerWithTag
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchController
import eu.kanade.tachiyomi.ui.source.global_search.GlobalSearchPresenter
import uy.kohesive.injekt.injectLazy

class SearchController(
    private var manga: Manga? = null
) : GlobalSearchController(manga?.title) {

    private var newManga: Manga? = null
    private var progress = 1
    var totalProgress = 0

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        if (totalProgress > 1) {
            return "($progress/$totalProgress) ${super.getTitle()}"
        } else
            return super.getTitle()
    }

    override fun createPresenter(): GlobalSearchPresenter {
        return SearchPresenter(initialQuery, manga!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(::manga.name, manga)
        outState.putSerializable(::newManga.name, newManga)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        manga = savedInstanceState.getSerializable(::manga.name) as? Manga
        newManga = savedInstanceState.getSerializable(::newManga.name) as? Manga
    }

    /*override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (totalProgress > 1) {
            val menuItem = menu.add(Menu.NONE, 1, Menu.NONE, R.string.action_skip_manga)
            menuItem.icon = VectorDrawableCompat.create(resources!!, R.drawable
                .baseline_skip_next_white_24, null)
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                newManga = manga
                migrateManga()
            }
        }
        return true
    }*/

    fun migrateManga() {
        val target = targetController as? MigrationInterface ?: return
        val manga = manga ?: return
        val newManga = newManga ?: return

        val nextManga = target.migrateManga(manga, newManga, true)
        replaceWithNewSearchController(nextManga)
    }

    fun copyManga() {
        val target = targetController as? MigrationInterface ?: return
        val manga = manga ?: return
        val newManga = newManga ?: return

        val nextManga = target.migrateManga(manga, newManga, false)
        replaceWithNewSearchController(nextManga)
    }

    override fun onMangaClick(manga: Manga) {
        if (targetController is MigrationListController) {
            val migrationListController = targetController as? MigrationListController
            val sourceManager: SourceManager by injectLazy()
            val source = sourceManager.get(manga.source) ?: return
            migrationListController?.useMangaForMigration(manga, source)
            router.popCurrentController()
            return
        }
        newManga = manga
        val dialog = MigrationDialog()
        dialog.targetController = this
        dialog.showDialog(router)
    }

    override fun onMangaLongClick(manga: Manga) {
        // Call parent's default click listener
        super.onMangaClick(manga)
    }

    fun renderIsReplacingManga(isReplacingManga: Boolean) {
        if (isReplacingManga) {
            if (router.getControllerWithTag(LOADING_DIALOG_TAG) == null) {
                LoadingController().showDialog(router, LOADING_DIALOG_TAG)
            }
        } else {
            router.popControllerWithTag(LOADING_DIALOG_TAG)
            router.popController(this)
        }
    }

    private fun replaceWithNewSearchController(manga: Manga?) {
        if (manga != null) {
            router.popCurrentController()
            val searchController = SearchController(manga)
            searchController.targetController = targetController
            searchController.progress = progress + 1
            searchController.totalProgress = totalProgress
            router.replaceTopController(searchController.withFadeTransaction())
        } else router.popController(this)
    }

    class MigrationDialog : DialogController() {

        private val preferences: PreferencesHelper by injectLazy()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val prefValue = preferences.migrateFlags().getOrDefault()

            val preselected = MigrationFlags.getEnabledFlagsPositions(prefValue)

            return MaterialDialog.Builder(activity!!)
                    .content(R.string.migration_dialog_what_to_include)
                    .items(MigrationFlags.titles.map { resources?.getString(it) })
                    .alwaysCallMultiChoiceCallback()
                    .itemsCallbackMultiChoice(preselected.toTypedArray()) { _, positions, _ ->
                        // Save current settings for the next time
                        val newValue = MigrationFlags.getFlagsFromPositions(positions)
                        preferences.migrateFlags().set(newValue)

                        true
                    }
                    .positiveText(R.string.migrate)
                    .negativeText(R.string.copy)
                    .neutralText(android.R.string.cancel)
                    .onPositive { _, _ ->
                        (targetController as? SearchController)?.migrateManga()
                    }
                    .onNegative { _, _ ->
                        (targetController as? SearchController)?.copyManga()
                    }
                    .build()
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
        inflater.inflate(R.menu.source_browse, menu)

        // Initialize search menu
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                searchView.onActionViewExpanded() // Required to show the query in the view
                searchView.setQuery(presenter.query, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                return true
            }
        })

        searchView.queryTextChangeEvents()
            .filter { it.isSubmitted }
            .subscribeUntilDestroy {
                presenter.search(it.queryText().toString())
                searchItem.collapseActionView()
                setTitle() // Update toolbar title
            }
    }

    class LoadingController : DialogController() {

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog.Builder(activity!!)
                    .progress(true, 0)
                    .content(R.string.migrating)
                    .cancelable(false)
                    .build()
        }
    }

    companion object {
        const val LOADING_DIALOG_TAG = "LoadingDialog"
    }
}
