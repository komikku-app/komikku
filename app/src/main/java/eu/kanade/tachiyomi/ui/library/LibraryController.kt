package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.core.prefs.CheckboxState
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.library.model.LibraryGroup
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.DeleteLibraryMangaDialog
import eu.kanade.presentation.library.LibraryScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.toast
import exh.favorites.FavoritesIntroDialog
import exh.favorites.FavoritesSyncStatus
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LibraryController(
    bundle: Bundle? = null,
) : FullComposeController<LibraryPresenter>(bundle), RootController {

    /**
     * Sheet containing filter/sort/display items.
     */
    private var settingsSheet: LibrarySettingsSheet? = null

    // --> EH
    // Sync dialog
    private var favSyncDialog: AlertDialog? = null

    // Old sync status
    private var oldSyncStatus: FavoritesSyncStatus? = null

    // Favorites
    private var favoritesSyncJob: Job? = null
    // <-- EH

    override fun createPresenter(): LibraryPresenter = LibraryPresenter()

    @Composable
    override fun ComposeContent() {
        val context = LocalContext.current
        LibraryScreen(
            presenter = presenter,
            onMangaClicked = ::openManga,
            onGlobalSearchClicked = {
                router.pushController(GlobalSearchController(presenter.searchQuery))
            },
            onChangeCategoryClicked = ::showMangaCategoriesDialog,
            onMarkAsReadClicked = { markReadStatus(true) },
            onMarkAsUnreadClicked = { markReadStatus(false) },
            onDownloadClicked = ::downloadUnreadChapters,
            onDeleteClicked = ::showDeleteMangaDialog,
            onClickFilter = ::showSettingsSheet,
            onClickRefresh = {
                // SY -->
                val groupType = presenter.groupType
                // SY -->
                val started = LibraryUpdateService.start(
                    context = context,
                    category = if (groupType == LibraryGroup.BY_DEFAULT) it else null,
                    group = groupType,
                    groupExtra = when (groupType) {
                        LibraryGroup.BY_DEFAULT -> null
                        LibraryGroup.BY_SOURCE, LibraryGroup.BY_STATUS, LibraryGroup.BY_TRACK_STATUS -> it?.id?.toString()
                        else -> null
                    },
                )
                // SY <--
                context.toast(if (started) R.string.updating_library else R.string.update_already_running)
                started
            },
            onClickInvertSelection = { presenter.invertSelection(presenter.activeCategory) },
            onClickSelectAll = { presenter.selectAll(presenter.activeCategory) },
            onClickUnselectAll = ::clearSelection,
            // SY -->
            onClickCleanTitles = ::cleanTitles,
            onClickMigrate = {
                val selectedMangaIds = presenter.selection
                    .filterNot { it.manga.source == MERGED_SOURCE_ID }
                    .map { it.manga.id }
                presenter.clearSelection()
                if (selectedMangaIds.isNotEmpty()) {
                    PreMigrationController.navigateToMigration(
                        Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                        router,
                        selectedMangaIds,
                    )
                } else {
                    activity?.toast(R.string.no_valid_manga)
                }
            },
            onClickAddToMangaDex = ::pushToMdList,
            onOpenReader = {
                startReading(it.manga)
            },
            onClickSyncExh = {
                // TODO
                if (Injekt.get<UnsortedPreferences>().exhShowSyncIntro().get()) {
                    activity?.let { FavoritesIntroDialog().show(it) }
                } else {
                    MaterialAlertDialogBuilder(activity!!)
                        .setTitle(R.string.favorites_sync)
                        .setMessage(R.string.favorites_sync_conformation_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            presenter.runSync()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            },
            // SY <--
        )

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            is LibraryPresenter.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        presenter.clearSelection()
                        router.pushController(CategoryController())
                    },
                    onConfirm = { include, exclude ->
                        presenter.clearSelection()
                        presenter.setMangaCategories(dialog.manga, include, exclude)
                    },
                )
            }
            is LibraryPresenter.Dialog.DeleteManga -> {
                DeleteLibraryMangaDialog(
                    containsLocalManga = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        presenter.removeMangas(dialog.manga.map { it.toDbManga() }, deleteManga, deleteChapter)
                        presenter.clearSelection()
                    },
                )
            }
            null -> {}
        }

        LaunchedEffect(presenter.selectionMode) {
            // Could perhaps be removed when navigation is in a Compose world
            if (router.backstackSize == 1) {
                (activity as? MainActivity)?.showBottomNav(presenter.selectionMode.not())
            }
        }
        LaunchedEffect(presenter.isLoading) {
            if (!presenter.isLoading) {
                (activity as? MainActivity)?.ready = true
            }
        }
    }

    override fun handleBack(): Boolean {
        return when {
            presenter.selection.isNotEmpty() -> {
                presenter.clearSelection()
                true
            }
            presenter.searchQuery != null -> {
                presenter.searchQuery = null
                true
            }
            else -> false
        }
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        settingsSheet = LibrarySettingsSheet(router) { group ->
            when (group) {
                is LibrarySettingsSheet.Filter.FilterGroup -> onFilterChanged()
                is LibrarySettingsSheet.Sort.SortGroup -> onSortChanged()
                is LibrarySettingsSheet.Display.DisplayGroup -> {}
                is LibrarySettingsSheet.Display.BadgeGroup -> onBadgeSettingChanged()
                is LibrarySettingsSheet.Display.TabsGroup -> {} // onTabsSettingsChanged()
                // SY -->
                is LibrarySettingsSheet.Grouping.InternalGroup -> onGroupSettingChanged()
                is LibrarySettingsSheet.Display.ButtonsGroup -> onButtonSettingChanged()
                // SY -->
            }
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            presenter.subscribeLibrary()
        }
    }

    override fun onDestroyView(view: View) {
        settingsSheet?.sheetScope?.cancel()
        settingsSheet = null
        super.onDestroyView(view)
    }

    fun showSettingsSheet() {
        presenter.categories.getOrNull(presenter.activeCategory)?.let { category ->
            settingsSheet?.show(category)
        }
    }

    private fun onFilterChanged() {
        presenter.requestFilterUpdate()
        activity?.invalidateOptionsMenu()
    }

    private fun onBadgeSettingChanged() {
        presenter.requestBadgesUpdate()
    }

    // SY -->
    private fun onButtonSettingChanged() {
        presenter.requestButtonsUpdate()
    }

    private fun onGroupSettingChanged() {
        presenter.requestGroupsUpdate()
    }
    // SY <--

    private fun onSortChanged() {
        presenter.requestSortUpdate()
    }

    fun search(query: String) {
        presenter.searchQuery = query
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val settingsSheet = settingsSheet ?: return
        presenter.hasActiveFilters = settingsSheet.filters.hasActiveFilters()
    }

    private fun openManga(mangaId: Long) {
        // Notify the presenter a manga is being opened.
        presenter.onOpenManga()

        router.pushController(MangaController(mangaId))
    }

    /**
     * Clear all of the manga currently selected, and
     * invalidate the action mode to revert the top toolbar
     */
    fun clearSelection() {
        presenter.clearSelection()
    }

    /**
     * Move the selected manga to a list of categories.
     */
    private fun showMangaCategoriesDialog() {
        viewScope.launchIO {
            // Create a copy of selected manga
            val mangaList = presenter.selection.map { it.manga }

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = presenter.ogCategories.filter { it.id != 0L } // SY <--

            // Get indexes of the common categories to preselect.
            val common = presenter.getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = presenter.getMixCategories(mangaList)
            val preselected = categories.map {
                when (it) {
                    in common -> CheckboxState.State.Checked(it)
                    in mix -> CheckboxState.TriState.Exclude(it)
                    else -> CheckboxState.State.None(it)
                }
            }
            presenter.dialog = LibraryPresenter.Dialog.ChangeCategory(mangaList, preselected)
        }
    }

    private fun downloadUnreadChapters() {
        val mangaList = presenter.selection.toList()
        presenter.downloadUnreadChapters(mangaList.map { it.manga })
        presenter.clearSelection()
    }

    private fun markReadStatus(read: Boolean) {
        val mangaList = presenter.selection.toList()
        presenter.markReadStatus(mangaList.map { it.manga }, read)
        presenter.clearSelection()
    }

    private fun showDeleteMangaDialog() {
        val mangaList = presenter.selection.map { it.manga }
        presenter.dialog = LibraryPresenter.Dialog.DeleteManga(mangaList)
    }

    // SY -->
    private fun cleanTitles() {
        val mangas = presenter.selection.filter {
            it.manga.isEhBasedManga() ||
                it.manga.source in nHentaiSourceIds
        }
        presenter.cleanTitles(mangas)
        presenter.clearSelection()
    }

    private fun pushToMdList() {
        val mangas = presenter.selection.filter {
            it.manga.source in mangaDexSourceIds
        }
        presenter.syncMangaToDex(mangas)
        presenter.clearSelection()
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        // --> EXH
        cleanupSyncState()
        favoritesSyncJob =
            presenter.favoritesSync.status
                .sample(100.milliseconds)
                .mapLatest {
                    updateSyncStatus(it)
                }
                .launchIn(viewScope)
        // <-- EXH
    }

    override fun onDetach(view: View) {
        super.onDetach(view)

        // EXH
        cleanupSyncState()
    }
    // SY <--

    // --> EXH
    private fun cleanupSyncState() {
        favoritesSyncJob?.cancel()
        favoritesSyncJob = null
        // Close sync status
        favSyncDialog?.dismiss()
        favSyncDialog = null
        oldSyncStatus = null
        // Clear flags
        releaseSyncLocks()
    }

    private fun buildDialog() = activity?.let {
        MaterialAlertDialogBuilder(it)
    }

    private fun showSyncProgressDialog() {
        favSyncDialog?.dismiss()
        favSyncDialog = buildDialog()
            ?.setTitle(R.string.favorites_syncing)
            ?.setMessage("")
            ?.setCancelable(false)
            ?.create()
        favSyncDialog?.show()
    }

    private fun takeSyncLocks() {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun releaseSyncLocks() {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private suspend fun updateSyncStatus(status: FavoritesSyncStatus) {
        when (status) {
            is FavoritesSyncStatus.Idle -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = null
            }
            is FavoritesSyncStatus.BadLibraryState.MangaInMultipleCategories -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = buildDialog()
                    ?.setTitle(R.string.favorites_sync_error)
                    ?.setMessage(activity!!.getString(R.string.favorites_sync_bad_library_state, status.message))
                    ?.setCancelable(false)
                    ?.setPositiveButton(R.string.show_gallery) { _, _ ->
                        openManga(status.manga.id)
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.setNegativeButton(android.R.string.ok) { _, _ ->
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.create()
                favSyncDialog?.show()
            }
            is FavoritesSyncStatus.Error -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = buildDialog()
                    ?.setTitle(R.string.favorites_sync_error)
                    ?.setMessage(activity!!.getString(R.string.favorites_sync_error_string, status.message))
                    ?.setCancelable(false)
                    ?.setPositiveButton(android.R.string.ok) { _, _ ->
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.create()
                favSyncDialog?.show()
            }
            is FavoritesSyncStatus.CompleteWithErrors -> {
                releaseSyncLocks()

                favSyncDialog?.dismiss()
                favSyncDialog = buildDialog()
                    ?.setTitle(R.string.favorites_sync_done_errors)
                    ?.setMessage(activity!!.getString(R.string.favorites_sync_done_errors_message, status.message))
                    ?.setCancelable(false)
                    ?.setPositiveButton(android.R.string.ok) { _, _ ->
                        presenter.favoritesSync.status.value = FavoritesSyncStatus.Idle(activity!!)
                    }
                    ?.create()
                favSyncDialog?.show()
            }
            is FavoritesSyncStatus.Processing,
            is FavoritesSyncStatus.Initializing,
            -> {
                takeSyncLocks()

                if (favSyncDialog == null || (
                    oldSyncStatus != null &&
                        oldSyncStatus !is FavoritesSyncStatus.Initializing &&
                        oldSyncStatus !is FavoritesSyncStatus.Processing
                    )
                ) {
                    showSyncProgressDialog()
                }

                favSyncDialog?.setMessage(status.message)
            }
        }
        oldSyncStatus = status
        if (status is FavoritesSyncStatus.Processing && status.delayedMessage != null) {
            delay(5.seconds)
            favSyncDialog?.setMessage(status.delayedMessage)
        }
    }

    private fun startReading(manga: Manga) {
        val activity = activity ?: return
        val chapter = presenter.getFirstUnread(manga) ?: return
        val intent = ReaderActivity.newIntent(activity, manga.id, chapter.id)
        presenter.clearSelection()
        startActivity(intent)
    }
    // <-- EXH
}
