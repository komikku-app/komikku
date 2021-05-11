package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MigrationListControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.MigrationMangaDialog
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.migration.search.SearchController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.toast
import exh.eh.EHentaiThrottleManager
import exh.smartsearch.SmartSearchEngine
import exh.util.executeOnIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.atomic.AtomicInteger

class MigrationListController(bundle: Bundle? = null) :
    BaseController<MigrationListControllerBinding>(bundle),
    MigrationProcessAdapter.MigrationProcessInterface {

    init {
        setHasOptionsMenu(true)
    }

    private var adapter: MigrationProcessAdapter? = null

    val config: MigrationProcedureConfig? = args.getParcelable(CONFIG_EXTRA)

    private val db: DatabaseHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    private val smartSearchEngine = SmartSearchEngine(config?.extraSearchParams)

    private val migrationScope = CoroutineScope(Job() + Dispatchers.IO)
    var migrationsJob: Job? = null
        private set
    private var migratingManga: MutableList<MigratingManga>? = null
    private var selectedPosition: Int? = null
    private var manualMigrations = 0

    private val throttleManager = EHentaiThrottleManager()

    override fun getTitle(): String {
        return resources?.getString(R.string.migration) + " (${adapter?.items?.count {
            it.manga.migrationStatus != MigrationStatus.RUNNING
        }}/${adapter?.itemCount ?: 0})"
    }

    override fun createBinding(inflater: LayoutInflater) = MigrationListControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        setTitle()
        val config = this.config ?: return

        val newMigratingManga = migratingManga ?: run {
            val new = config.mangaIds.map {
                MigratingManga(db, sourceManager, it, migrationScope.coroutineContext)
            }
            migratingManga = new.toMutableList()
            new
        }

        adapter = MigrationProcessAdapter(this)

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)

        adapter?.updateDataSet(newMigratingManga.map { it.toModal() })

        if (migrationsJob == null) {
            migrationsJob = migrationScope.launch {
                runMigrations(newMigratingManga)
            }
        }
    }

    private suspend fun runMigrations(mangas: List<MigratingManga>) {
        throttleManager.resetThrottle()
        if (config == null) return
        val useSourceWithMost = preferences.useSourceWithMost().get()
        val useSmartSearch = preferences.smartMigration().get()

        val sources = preferences.migrationSources().get().split("/").mapNotNull {
            val value = it.toLongOrNull() ?: return@mapNotNull null
            sourceManager.get(value) as? CatalogueSource
        }
        for (manga in mangas) {
            if (migrationsJob?.isCancelled == true) {
                break
            }
            // in case it was removed
            if (manga.mangaId !in config.mangaIds) {
                continue
            }
            if (!manga.searchResult.initialized && manga.migrationJob.isActive) {
                val mangaObj = manga.manga()

                if (mangaObj == null) {
                    manga.searchResult.initialize(null)
                    continue
                }

                val mangaSource = manga.mangaSource()

                val result = try {
                    CoroutineScope(manga.migrationJob).async {
                        val validSources = if (sources.size == 1) {
                            sources
                        } else {
                            sources.filter { it.id != mangaSource.id }
                        }
                        if (useSourceWithMost) {
                            val sourceSemaphore = Semaphore(3)
                            val processedSources = AtomicInteger()

                            validSources.map { source ->
                                async async2@{
                                    sourceSemaphore.withPermit {
                                        try {
                                            val searchResult = if (useSmartSearch) {
                                                smartSearchEngine.smartSearch(source, mangaObj.originalTitle)
                                            } else {
                                                smartSearchEngine.normalSearch(source, mangaObj.originalTitle)
                                            }

                                            if (searchResult != null && !(searchResult.url == mangaObj.url && source.id == mangaObj.source)) {
                                                val localManga = smartSearchEngine.networkToLocalManga(
                                                    searchResult,
                                                    source.id
                                                )

                                                val chapters = if (source is EHentai) {
                                                    source.getChapterList(localManga.toMangaInfo(), throttleManager::throttle)
                                                } else {
                                                    source.getChapterList(localManga.toMangaInfo())
                                                }

                                                try {
                                                    syncChaptersWithSource(db, chapters.map { it.toSChapter() }, localManga, source)
                                                } catch (e: Exception) {
                                                    return@async2 null
                                                }
                                                manga.progress.value = validSources.size to processedSources.incrementAndGet()
                                                localManga to chapters.size
                                            } else {
                                                null
                                            }
                                        } catch (e: CancellationException) {
                                            // Ignore cancellations
                                            throw e
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                }
                            }.mapNotNull { it.await() }.maxByOrNull { it.second }?.first
                        } else {
                            validSources.forEachIndexed { index, source ->
                                val searchResult = try {
                                    val searchResult = if (useSmartSearch) {
                                        smartSearchEngine.smartSearch(source, mangaObj.originalTitle)
                                    } else {
                                        smartSearchEngine.normalSearch(source, mangaObj.originalTitle)
                                    }

                                    if (searchResult != null) {
                                        val localManga = smartSearchEngine.networkToLocalManga(searchResult, source.id)
                                        val chapters = try {
                                            if (source is EHentai) {
                                                source.getChapterList(localManga.toMangaInfo(), throttleManager::throttle)
                                            } else {
                                                source.getChapterList(localManga.toMangaInfo())
                                            }.map { it.toSChapter() }
                                        } catch (e: java.lang.Exception) {
                                            Timber.e(e)
                                            emptyList()
                                        }
                                        withIOContext {
                                            syncChaptersWithSource(db, chapters, localManga, source)
                                        }
                                        localManga
                                    } else null
                                } catch (e: CancellationException) {
                                    // Ignore cancellations
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                                manga.progress.value = validSources.size to (index + 1)
                                if (searchResult != null) return@async searchResult
                            }

                            null
                        }
                    }.await()
                } catch (e: CancellationException) {
                    // Ignore canceled migrations
                    continue
                }

                if (result != null && result.thumbnail_url == null) {
                    try {
                        val newManga = sourceManager.getOrStub(result.source).getMangaDetails(result.toMangaInfo())
                        result.copyFrom(newManga.toSManga())

                        db.insertManga(result).executeOnIO()
                    } catch (e: CancellationException) {
                        // Ignore cancellations
                        throw e
                    } catch (e: Exception) {
                    }
                }

                manga.migrationStatus = if (result == null) MigrationStatus.MANGA_NOT_FOUND else MigrationStatus.MANGA_FOUND
                adapter?.sourceFinished()
                manga.searchResult.initialize(result?.id)
            }
        }
    }

    override fun updateCount() {
        launchUI {
            if (router.backstack.lastOrNull()?.controller() == this@MigrationListController) {
                setTitle()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        migrationScope.cancel()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun enableButtons() {
        activity?.invalidateOptionsMenu()
    }

    override fun removeManga(item: MigrationProcessItem) {
        val ids = config?.mangaIds?.toMutableList() ?: return
        val index = ids.indexOf(item.manga.mangaId)
        if (index > -1) {
            ids.removeAt(index)
            config.mangaIds = ids
            val index2 = migratingManga?.indexOf(item.manga) ?: return
            if (index2 > -1) migratingManga?.removeAt(index2)
        }
    }

    override fun noMigration() {
        launchUI {
            val res = resources
            if (res != null) {
                activity?.toast(
                    res.getQuantityString(
                        R.plurals.manga_migrated,
                        manualMigrations,
                        manualMigrations
                    )
                )
            }
            router.popCurrentController()
        }
    }

    override fun onMenuItemClick(position: Int, item: MenuItem) {
        when (item.itemId) {
            R.id.action_search_manually -> {
                launchUI {
                    val manga = adapter?.getItem(position)?.manga?.manga() ?: return@launchUI
                    selectedPosition = position
                    val sources = preferences.migrationSources().get().split("/").mapNotNull {
                        val value = it.toLongOrNull() ?: return@mapNotNull null
                        sourceManager.get(value) as? CatalogueSource
                    }
                    val validSources = if (sources.size == 1) {
                        sources
                    } else {
                        sources.filter { it.id != manga.source }
                    }
                    val searchController = SearchController(manga, validSources)
                    searchController.targetController = this@MigrationListController
                    router.pushController(searchController.withFadeTransaction())
                }
            }
            R.id.action_skip -> adapter?.removeManga(position)
            R.id.action_migrate_now -> {
                adapter?.migrateManga(position, false)
                manualMigrations++
            }
            R.id.action_copy_now -> {
                adapter?.migrateManga(position, true)
                manualMigrations++
            }
        }
    }

    fun useMangaForMigration(manga: Manga, source: Source) {
        val firstIndex = selectedPosition ?: return
        val migratingManga = adapter?.getItem(firstIndex) ?: return
        migratingManga.manga.migrationStatus = MigrationStatus.RUNNING
        adapter?.notifyItemChanged(firstIndex)
        launchUI {
            val result = CoroutineScope(migratingManga.manga.migrationJob).async {
                val localManga = smartSearchEngine.networkToLocalManga(manga, source.id)
                try {
                    val chapters = source.getChapterList(localManga.toMangaInfo())
                        .map { it.toSChapter() }
                    syncChaptersWithSource(db, chapters, localManga, source)
                } catch (e: Exception) {
                    return@async null
                }
                localManga
            }.await()

            if (result != null) {
                try {
                    val newManga = sourceManager.getOrStub(result.source).getMangaDetails(result.toMangaInfo())
                    result.copyFrom(newManga.toSManga())

                    db.insertManga(result).executeOnIO()
                } catch (e: CancellationException) {
                    // Ignore cancellations
                    throw e
                } catch (e: Exception) {
                }

                migratingManga.manga.migrationStatus = MigrationStatus.MANGA_FOUND
                migratingManga.manga.searchResult.set(result.id)
                adapter?.notifyDataSetChanged()
            } else {
                migratingManga.manga.migrationStatus = MigrationStatus.MANGA_NOT_FOUND
                activity?.toast(R.string.no_chapters_found_for_migration, Toast.LENGTH_LONG)
                adapter?.notifyDataSetChanged()
            }
        }
    }

    fun migrateMangas() {
        launchUI {
            adapter?.performMigrations(false)
            navigateOut()
        }
    }

    fun copyMangas() {
        launchUI {
            adapter?.performMigrations(true)
            navigateOut()
        }
    }

    private fun navigateOut() {
        if (migratingManga?.size == 1) {
            launchUI {
                val hasDetails = router.backstack.any { it.controller() is MangaController }
                if (hasDetails) {
                    val manga = migratingManga?.firstOrNull()?.searchResult?.get()?.let {
                        db.getManga(it).executeOnIO()
                    }
                    if (manga != null) {
                        val newStack = router.backstack.filter {
                            it.controller() !is MangaController &&
                                it.controller() !is MigrationListController &&
                                it.controller() !is PreMigrationController
                        } + MangaController(manga).withFadeTransaction()
                        router.setBackstack(newStack, FadeChangeHandler())
                        return@launchUI
                    }
                }
                router.popCurrentController()
            }
        } else router.popCurrentController()
    }

    override fun handleBack(): Boolean {
        activity?.let {
            MaterialDialog(it).show {
                title(R.string.stop_migrating)
                positiveButton(R.string.action_stop) {
                    router.popCurrentController()
                    migrationsJob?.cancel()
                }
                negativeButton(android.R.string.cancel)
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.migration_list, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        // Initialize menu items.

        val allMangasDone = adapter?.allMangasDone() ?: return

        val menuCopy = menu.findItem(R.id.action_copy_manga)
        val menuMigrate = menu.findItem(R.id.action_migrate_manga)

        if (adapter?.itemCount == 1) {
            menuMigrate.icon = VectorDrawableCompat.create(
                resources!!,
                R.drawable.ic_done_24dp,
                null
            )
        }

        menuCopy.icon.mutate()
        menuMigrate.icon.mutate()
        val tintColor = activity?.getResourceColor(R.attr.colorOnPrimary) ?: Color.WHITE
        val translucentWhite = ColorUtils.setAlphaComponent(tintColor, 127)
        menuCopy.icon?.setTint(if (allMangasDone) tintColor else translucentWhite)
        menuMigrate?.icon?.setTint(if (allMangasDone) tintColor else translucentWhite)
        menuCopy.isEnabled = allMangasDone
        menuMigrate.isEnabled = allMangasDone
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val totalManga = adapter?.itemCount ?: 0
        val mangaSkipped = adapter?.mangasSkipped() ?: 0
        when (item.itemId) {
            R.id.action_copy_manga -> MigrationMangaDialog(
                this,
                true,
                totalManga,
                mangaSkipped
            ).showDialog(router)
            R.id.action_migrate_manga -> MigrationMangaDialog(
                this,
                false,
                totalManga,
                mangaSkipped
            ).showDialog(router)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
    /*
    override fun canChangeTabs(block: () -> Unit): Boolean {
        if (migrationsJob?.isCancelled == false || adapter?.allMangasDone() == true) {
            activity?.let {
                MaterialDialog(it).show {
                    title(R.string.stop_migrating)
                    positiveButton(R.string.action_stop) {
                        block()
                        migrationsJob?.cancel()
                    }
                    negativeButton(android.R.string.cancel)
                }
            }
            return false
        }
        return true
    }*/

    companion object {
        const val CONFIG_EXTRA = "config_extra"
        const val TAG = "migration_list"

        fun create(config: MigrationProcedureConfig): MigrationListController {
            return MigrationListController(
                bundleOf(
                    CONFIG_EXTRA to config
                )
            )
        }
    }
}
