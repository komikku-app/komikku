package app.komikku.shared.library

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

/**
 * KMK: refreshes library entries from their sources. Platform-neutral interface so the desktop app
 * (coroutine runner below) and, later, Android (its WorkManager `LibraryUpdateJob`) can sit behind it.
 */
interface LibraryUpdateRunner {

    sealed interface Target {
        data class Single(val mangaId: Long) : Target
        data class Category(val categoryId: Long) : Target

        /**
         * These library entries (Android's `LibraryUpdateJob.startNow(mangaIds = …)`: an explicit library selection).
         * Like Android, of the update restrictions only "fetch once" (an entry with chapters and `ONLY_FETCH_ONCE`)
         * applies.
         */
        data class Entries(val mangaIds: Set<Long>) : Target

        /**
         * A page of a library grouped by something other than category ([groupType], a `LibraryGroup` constant;
         * [mangaIds] are the page's entries): Android SY's grouped update (`LibraryUpdateJob.addMangaToQueue`, group
         * branch). `LibraryPreferences.groupLibraryUpdateType()` decides between the whole library (as [All]: `GLOBAL`,
         * or `ALL_BUT_UNGROUPED` on the ungrouped page) and the page's entries; all update restrictions and the
         * update order apply, like a category update.
         */
        data class Group(val groupType: Int, val mangaIds: Set<Long>) : Target
        data object All : Target
    }

    /** `null` when nothing ran yet. */
    val progress: StateFlow<LibraryUpdateProgress?>

    /** Starts an update; returns `false` if one is already running. */
    fun start(target: Target): Boolean

    fun cancel()
}

data class LibraryUpdateProgress(
    val target: LibraryUpdateRunner.Target,
    val total: Int,
    val done: Int = 0,
    val current: List<String> = emptyList(),
    val newChapters: Int = 0,
    val failed: List<Pair<Manga, String>> = emptyList(),
    val skipped: Int = 0,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
)

/**
 * KMK: which library entries an update refreshes, from Android's `LibraryUpdateJob.addMangaToQueue`:
 * a category's entries, for "all" the included / excluded update categories, a group page by
 * `groupLibraryUpdateType`; then the "smart update" restrictions and "only fetch once" strategy, in the job's order
 * (due today, started, recently read, title).
 */
object LibraryUpdateSelection {

    data class Result(val toUpdate: List<LibraryManga>, val skipped: List<LibraryManga>)

    fun select(
        library: List<LibraryManga>,
        target: LibraryUpdateRunner.Target,
        preferences: LibraryPreferences,
        fetchWindowUpperBound: Long,
    ): Result {
        val candidates = when (target) {
            is LibraryUpdateRunner.Target.Single -> return Result(library.filter { it.manga.id == target.mangaId }.take(1), emptyList())
            is LibraryUpdateRunner.Target.Category -> library.filter { target.categoryId in it.categories }
            // Android's targeted update: only the "fetch once" restriction applies
            is LibraryUpdateRunner.Target.Entries -> return Result(
                library.filter {
                    it.manga.id in target.mangaIds &&
                        !(it.manga.updateStrategy == UpdateStrategy.ONLY_FETCH_ONCE && it.totalChapters > 0L)
                }.distinctBy { it.manga.id },
                emptyList(),
            )
            is LibraryUpdateRunner.Target.Group -> {
                val mode = preferences.groupLibraryUpdateType().get()
                if (
                    target.groupType == LibraryGroup.BY_DEFAULT ||
                    mode == GroupLibraryMode.GLOBAL ||
                    (mode == GroupLibraryMode.ALL_BUT_UNGROUPED && target.groupType == LibraryGroup.UNGROUPED)
                ) {
                    library.inUpdateCategories(preferences)
                } else {
                    library.filter { it.manga.id in target.mangaIds }
                }
            }
            LibraryUpdateRunner.Target.All -> library.inUpdateCategories(preferences)
        }.distinctBy { it.manga.id }

        val restrictions = preferences.autoUpdateMangaRestrictions().get()
        val (toUpdate, skipped) = candidates.partition {
            when {
                it.manga.updateStrategy == UpdateStrategy.ONLY_FETCH_ONCE && it.totalChapters > 0L -> false
                LibraryPreferences.MANGA_NON_COMPLETED in restrictions && it.manga.status.toInt() == SManga.COMPLETED -> false
                LibraryPreferences.MANGA_HAS_UNREAD in restrictions && it.unreadCount != 0L -> false
                LibraryPreferences.MANGA_NON_READ in restrictions && it.totalChapters > 0L && !it.hasStarted -> false
                LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in restrictions && it.manga.nextUpdate > fetchWindowUpperBound -> false
                else -> true
            }
        }
        return Result(
            toUpdate = toUpdate.sortedWith(
                compareByDescending<LibraryManga> { it.manga.nextUpdate <= fetchWindowUpperBound }
                    .thenByDescending { it.lastRead > 0L }
                    .thenByDescending { it.lastRead }
                    .thenByDescending { it.manga.title },
            ),
            skipped = skipped,
        )
    }

    /** "All": the entries of the included update categories (all when none) that are in no excluded one. */
    private fun List<LibraryManga>.inUpdateCategories(preferences: LibraryPreferences): List<LibraryManga> {
        val included = preferences.updateCategories().get().map { it.toLong() }.toSet()
        val excluded = preferences.updateCategoriesExclude().get().map { it.toLong() }.toSet()
        return filter {
            (included.isEmpty() || it.categories.intersect(included).isNotEmpty()) &&
                it.categories.intersect(excluded).isEmpty()
        }
    }
}

/**
 * KMK: [LibraryUpdateRunner] as a coroutine job in [scope] (desktop; Android keeps WorkManager): selects
 * entries with [LibraryUpdateSelection], refreshes up to [parallelism] at a time through [updateManga]
 * (returns the number of new chapters; the desktop passes `UpdateMangaFromRemote`), reports progress
 * and per-entry errors (also handed to [onError], e.g. to store them like Android's update errors).
 */
class CoroutineLibraryUpdateRunner(
    private val scope: CoroutineScope,
    private val getLibrary: suspend () -> List<LibraryManga>,
    private val preferences: LibraryPreferences,
    private val fetchWindowUpperBound: () -> Long,
    private val updateManga: suspend (Manga) -> Int,
    private val onError: suspend (Manga, Throwable) -> Unit = { _, _ -> },
    private val onFinished: suspend (LibraryUpdateProgress) -> Unit = {},
    private val parallelism: Int = 3,
    /** Called when an update starts, before it selects entries (e.g. to store the "last updated" time). */
    private val onStarted: (LibraryUpdateRunner.Target) -> Unit = {},
) : LibraryUpdateRunner {

    private val _progress = MutableStateFlow<LibraryUpdateProgress?>(null)
    override val progress: StateFlow<LibraryUpdateProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    @Synchronized
    override fun start(target: LibraryUpdateRunner.Target): Boolean {
        if (job?.isActive == true) return false
        _progress.value = LibraryUpdateProgress(target = target, total = 0)
        onStarted(target)
        job = scope.launch {
            try {
                val selection = LibraryUpdateSelection.select(getLibrary(), target, preferences, fetchWindowUpperBound())
                _progress.update { it?.copy(total = selection.toUpdate.size, skipped = selection.skipped.size) }
                val semaphore = Semaphore(parallelism)
                coroutineScope {
                    selection.toUpdate.forEach { item ->
                        launch {
                            semaphore.withPermit { updateOne(item.manga) }
                        }
                    }
                }
                _progress.update { it?.copy(finished = true, current = emptyList()) }
            } catch (e: CancellationException) {
                _progress.update { it?.copy(finished = true, cancelled = true, current = emptyList()) }
                throw e
            } finally {
                withContext(NonCancellable) { _progress.value?.let { onFinished(it) } }
            }
        }
        return true
    }

    private suspend fun updateOne(manga: Manga) {
        _progress.update { it?.copy(current = it.current + manga.title) }
        try {
            val added = updateManga(manga)
            _progress.update { it?.copy(newChapters = it.newChapters + added) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _progress.update { it?.copy(failed = it.failed + (manga to (e.message ?: e::class.simpleName.orEmpty()))) }
            onError(manga, e)
        } finally {
            _progress.update { it?.copy(done = it.done + 1, current = it.current - manga.title) }
        }
    }

    override fun cancel() {
        job?.cancel()
    }
}
