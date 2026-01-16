package mihon.feature.migration.list

import android.content.Context
import android.widget.Toast
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.system.toast
import exh.source.MERGED_SOURCE_ID
import exh.util.ThrottleManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.feature.migration.list.models.MigratingManga
import mihon.feature.migration.list.models.MigratingManga.SearchResult
import mihon.feature.migration.list.search.SmartSourceSearchEngine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicInteger

class MigrationListScreenModel(
    private var mangaIds: List<Long>,
    extraSearchQuery: String?,
    val preferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = Injekt.get(),
    // SY -->
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    // SY <--
) : ScreenModel {

    private val smartSearchEngine = SmartSourceSearchEngine(extraSearchQuery)
    // SY -->
    private val throttleManager = ThrottleManager()
    // SY <--

    val migratingItems = MutableStateFlow<ImmutableList<MigratingManga>?>(null)
    val migrationDone = MutableStateFlow(false)
    val finishedCount = MutableStateFlow(0)

    val manualMigrations = MutableStateFlow(0)

    var hideUnmatched = preferences.migrationHideUnmatched().get()
    private var hideWithoutUpdates = preferences.migrationHideWithoutUpdates().get()
    private var prioritizeByChapters = preferences.migrationPrioritizeByChapters().get()
    private var deepSearchMode = preferences.migrationDeepSearchMode().get()

    val navigateOut = MutableSharedFlow<Unit>()

    val dialog = MutableStateFlow<Dialog?>(null)

    val migratingProgress = MutableStateFlow(Float.MAX_VALUE)

    private var migrateJob: Job? = null

    init {
        screenModelScope.launchIO {
            runMigrations(
                mangaIds
                    .map {
                        async {
                            val manga = getManga.await(it) ?: return@async null
                            MigratingManga(
                                manga = manga,
                                chapterInfo = getChapterInfo(it),
                                source = sourceManager.getOrStub(manga.source).getNameForMangaInfo(
                                    if (manga.source == MERGED_SOURCE_ID) {
                                        getMergedReferencesById.await(manga.id)
                                            .map { sourceManager.getOrStub(it.mangaSourceId) }
                                    } else {
                                        null
                                    },
                                ),
                                parentContext = screenModelScope.coroutineContext,
                            )
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .also {
                        migratingItems.value = it.toImmutableList()
                    },
            )
        }
    }

    suspend fun getManga(result: SearchResult.Success) = getManga(result.id)
    suspend fun getManga(id: Long) = getManga.await(id)
    suspend fun getChapterInfo(result: SearchResult.Success) = getChapterInfo(result.id)
    private suspend fun getChapterInfo(id: Long) = getChaptersByMangaId.await(id).let { chapters ->
        MigratingManga.ChapterInfo(
            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
            chapterCount = chapters.size,
        )
    }
    fun getSourceName(manga: Manga) = sourceManager.getOrStub(manga.source).getNameForMangaInfo()

    fun getMigrationSources() = preferences.migrationSources().get().mapNotNull {
        sourceManager.get(it) as? CatalogueSource
    }

    private suspend fun runMigrations(mangas: List<MigratingManga>) {
        throttleManager.resetThrottle()
        // KMK: finishedCount.value = mangas.size

        val sources = getMigrationSources()
        for (manga in mangas) {
            if (!currentCoroutineContext().isActive) {
                break
            }
            // in case it was removed
            if (manga.manga.id !in mangaIds) {
                continue
            }

            if (manga.searchResult.value == SearchResult.Searching && manga.migrationScope.isActive) {
                val mangaObj = manga.manga
                val mangaSource = sourceManager.getOrStub(mangaObj.source)

                val result = try {
                    // KMK -->
                    manga.searchingJob = manga.migrationScope.async {
                        // KMK <--
                        val validSources = if (sources.size == 1) {
                            sources
                        } else {
                            sources.filter { it.id != mangaSource.id }
                        }
                        if (prioritizeByChapters) {
                            val sourceSemaphore = Semaphore(3)
                            val processedSources = AtomicInteger()

                            validSources.map { source ->
                                async async2@{
                                    sourceSemaphore.withPermit {
                                        val result = searchSource(manga.manga, source, deepSearchMode)
                                        if (result != null) {
                                            manga.progress.value =
                                                validSources.size to processedSources.incrementAndGet()
                                        }
                                        result
                                    }
                                }
                            }.mapNotNull { it.await() }.maxByOrNull { it.second }?.first
                        } else {
                            validSources.forEachIndexed { index, source ->
                                val result = searchSource(manga.manga, source, deepSearchMode)
                                manga.progress.value = validSources.size to (index + 1)
                                if (result != null) return@async result.first
                            }

                            null
                        }
                    }
                    // KMK -->
                    manga.searchingJob?.await()
                    // KMK <--
                } catch (_: CancellationException) {
                    // Ignore canceled migrations
                    continue
                }

                if (result != null && result.thumbnailUrl == null) {
                    try {
                        val newManga = sourceManager.getOrStub(result.source).getMangaDetails(result.toSManga())
                        updateManga.awaitUpdateFromSource(result, newManga, true)
                    } catch (e: CancellationException) {
                        // Ignore cancellations
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("MigrationListScreenModel").e(e, "Error updating manga from source")
                    }
                }

                manga.searchResult.value = if (result == null) {
                    SearchResult.NotFound
                } else {
                    SearchResult.Success(result.id)
                }
                if (result == null && hideUnmatched) {
                    removeManga(manga)
                }
                if (result != null &&
                    hideWithoutUpdates &&
                    (getChapterInfo(result.id).latestChapter ?: 0.0) <= (manga.chapterInfo.latestChapter ?: 0.0)
                ) {
                    removeManga(manga)
                }

                updateMigrationProgress()
            }
        }
    }

    private suspend fun searchSource(
        manga: Manga,
        source: CatalogueSource,
        deepSearchMode: Boolean,
    ): Pair<Manga, Int>? {
        return try {
            val searchResult = if (deepSearchMode) {
                smartSearchEngine.deepSearch(source, manga.ogTitle)
            } else {
                smartSearchEngine.regularSearch(source, manga.ogTitle)
            }

            if (searchResult != null &&
                !(searchResult.url == manga.url && source.id == manga.source)
            ) {
                val localManga = networkToLocalManga(searchResult)

                val chapters = try {
                    if (source is EHentai) {
                        source.getChapterList(localManga.toSManga(), throttleManager::throttle)
                    } else {
                        source.getChapterList(localManga.toSManga())
                    }
                } catch (e: Exception) {
                    this@MigrationListScreenModel.logcat(LogPriority.ERROR, e)
                    return null
                }

                try {
                    syncChaptersWithSource.await(chapters, localManga, source)
                } catch (_: Exception) {
                    return null
                }
                localManga to chapters.size
            } else {
                null
            }
        } catch (e: CancellationException) {
            // Ignore cancellations
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun updateMigrationProgress() {
        finishedCount.value = migratingItems.value.orEmpty().count {
            it.searchResult.value != SearchResult.Searching
        }
        if (migrationComplete()) {
            migrationDone.value = true
        }
        if (migratingItems.value?.isEmpty() == true) {
            navigateBack()
        }
    }

    private fun migrationComplete() = migratingItems.value.orEmpty().all { it.searchResult.value != SearchResult.Searching } &&
        migratingItems.value.orEmpty().any { it.searchResult.value is SearchResult.Success }

    private fun mangasSkipped() = migratingItems.value.orEmpty().count { it.searchResult.value == SearchResult.NotFound }

    /** Set a manga picked from manual search to be used as migration target */
    fun useMangaForMigration(context: Context, newMangaId: Long, selectedMangaId: Long) {
        val migratingManga = migratingItems.value.orEmpty().find { it.manga.id == selectedMangaId }
            ?: return
        migratingManga.searchResult.value = SearchResult.Searching
        screenModelScope.launchIO {
            val result = migratingManga.migrationScope.async {
                val manga = getManga(newMangaId)!!
                try {
                    val source = sourceManager.get(manga.source)!!
                    val chapters = source.getChapterList(manga.toSManga())
                    syncChaptersWithSource.await(chapters, manga, source)
                } catch (_: Exception) {
                    return@async null
                }
                manga
            }.await()

            if (result != null) {
                try {
                    val newManga = sourceManager.getOrStub(result.source).getMangaDetails(result.toSManga())
                    updateManga.awaitUpdateFromSource(result, newManga, true)
                } catch (e: CancellationException) {
                    // Ignore cancellations
                    throw e
                } catch (e: Exception) {
                    Timber.tag("MigrationListScreenModel").e(e, "Error updating manga from source")
                }

                migratingManga.searchResult.value = SearchResult.Success(result.id)
            } else {
                migratingManga.searchResult.value = SearchResult.NotFound
                withUIContext {
                    context.toast(SYMR.strings.no_chapters_found_for_migration, Toast.LENGTH_LONG)
                }
            }

            // KMK -->
            updateMigrationProgress()
            // KMK <--
        }
    }

    fun migrateMangas() {
        migrateMangas(true)
    }

    fun copyMangas() {
        migrateMangas(false)
    }

    private fun migrateMangas(replace: Boolean) {
        dialog.value = null
        migrateJob = screenModelScope.launchIO {
            migratingProgress.value = 0f
            val items = migratingItems.value.orEmpty()
            try {
                items.forEachIndexed { index, manga ->
                    try {
                        ensureActive()
                        val target = manga.searchResult.value.let {
                            if (it is SearchResult.Success) {
                                getManga.await(it.id)
                            } else {
                                null
                            }
                        }
                        if (target != null) {
                            migrateManga(manga.manga, target, replace)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    migratingProgress.value = index.toFloat() / items.size
                }

                navigateBack()
            } finally {
                migratingProgress.value = Float.MAX_VALUE
                migrateJob = null
            }
        }
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
    }

    private suspend fun navigateBack() {
        navigateOut.emit(Unit)
    }

    fun migrateNow(mangaId: Long, replace: Boolean) {
        manualMigrations.value++
        screenModelScope.launchIO {
            val manga = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO

            val target = getManga.await((manga.searchResult.value as? SearchResult.Success)?.id ?: return@launchIO)
                ?: return@launchIO

            migrateManga(manga.manga, target, replace)
            removeManga(mangaId)
        }
    }

    // KMK -->
    /** Cancel searching without remove it from list so user can perform manual search */
    fun cancelManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO
            item.searchingJob?.cancel()
            item.searchingJob = null
            item.searchResult.value = SearchResult.NotFound
            updateMigrationProgress()
        }
    }
    // KMK <--

    fun removeManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO
            removeManga(item)
            item.migrationScope.cancel()
            updateMigrationProgress()
        }
    }

    private fun removeManga(item: MigratingManga) {
        val ids = mangaIds.toMutableList()
        val index = ids.indexOf(item.manga.id)
        if (index > -1) {
            ids.removeAt(index)
            mangaIds = ids
            val index2 = migratingItems.value.orEmpty().indexOf(item)
            if (index2 > -1) migratingItems.value = (migratingItems.value.orEmpty() - item).toImmutableList()
        }
    }

    override fun onDispose() {
        super.onDispose()
        migratingItems.value.orEmpty().forEach {
            it.migrationScope.cancel()
        }
    }

    fun showMigrateDialog(copy: Boolean) {
        dialog.value = Dialog.Migrate(
            copy,
            migratingItems.value.orEmpty().size,
            mangasSkipped(),
        )
    }

    // KMK -->
    fun openOptionsDialog() {
        dialog.value = Dialog.Options
    }

    fun updateOptions() {
        hideUnmatched = preferences.migrationHideUnmatched().get()
        hideWithoutUpdates = preferences.migrationHideWithoutUpdates().get()
        prioritizeByChapters = preferences.migrationPrioritizeByChapters().get()
        deepSearchMode = preferences.migrationDeepSearchMode().get()
    }
    // KMK <--

    sealed class Dialog {
        data class Migrate(val copy: Boolean, val totalCount: Int, val skippedCount: Int) : Dialog()
        data object Exit : Dialog()

        // KMK -->
        data object Options : Dialog()
        // KMK <--
    }
}
