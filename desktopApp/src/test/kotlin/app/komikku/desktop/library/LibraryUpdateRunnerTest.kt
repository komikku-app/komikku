package app.komikku.desktop.library

import app.komikku.desktop.di.DesktopInjekt
import app.komikku.shared.library.CoroutineLibraryUpdateRunner
import app.komikku.shared.library.LibraryUpdateRunner
import app.komikku.shared.library.LibraryUpdateSelection
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import exh.source.PURURIN_SOURCE_ID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/** 6L.2: the shared update selection (categories, restrictions, order) and the coroutine runner. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryUpdateRunnerTest {

    // `Manga.title` resolves custom info through Injekt
    @BeforeAll
    fun boot(@TempDir dir: File) {
        DesktopInjekt.init(dir)
    }

    private fun entry(
        id: Long,
        categories: List<Long> = listOf(0),
        total: Long = 5,
        read: Long = 5,
        lastRead: Long = 0,
        status: Int = SManga.ONGOING,
        strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        source: Long = 0,
    ) = LibraryManga(
        manga = Manga.create().copy(id = id, ogTitle = "Manga $id", favorite = true, ogStatus = status.toLong(), updateStrategy = strategy, source = source),
        categories = categories,
        totalChapters = total,
        readCount = read,
        bookmarkCount = 0,
        bookmarkReadCount = 0,
        chapterFlags = 0,
        latestUpload = 0,
        chapterFetchedAt = 0,
        lastRead = lastRead,
    )

    private val prefs by lazy { Injekt.get<LibraryPreferences>() }

    @Test
    fun `selection by target, categories and restrictions`() {
        val library = listOf(
            entry(1, listOf(1), lastRead = 10),
            entry(2, listOf(2), lastRead = 20),
            entry(3, listOf(1), status = SManga.COMPLETED),
            entry(4, listOf(1), read = 2),
            entry(5, listOf(1), read = 0),
            entry(6, listOf(1), strategy = UpdateStrategy.ONLY_FETCH_ONCE),
        )
        prefs.autoUpdateMangaRestrictions().set(
            setOf(LibraryPreferences.MANGA_NON_COMPLETED, LibraryPreferences.MANGA_HAS_UNREAD, LibraryPreferences.MANGA_NON_READ),
        )
        val all = LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.All, prefs, Long.MAX_VALUE)
        all.toUpdate.map { it.manga.id } shouldBe listOf(2L, 1)
        all.skipped.map { it.manga.id }.toSet() shouldBe setOf(3L, 4, 5, 6)
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.Category(2), prefs, Long.MAX_VALUE)
            .toUpdate.map { it.manga.id } shouldBe listOf(2L)
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.Single(5), prefs, Long.MAX_VALUE)
            .toUpdate.map { it.manga.id } shouldBe listOf(5L)
        // Selected entries / a non-category group: Android's targeted update skips only "fetch once" entries
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.Entries(setOf(3, 5, 6)), prefs, Long.MAX_VALUE)
            .toUpdate.map { it.manga.id }.toSet() shouldBe setOf(3L, 5)
        prefs.updateCategoriesExclude().set(setOf("2"))
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.All, prefs, Long.MAX_VALUE)
            .toUpdate.map { it.manga.id } shouldBe listOf(1L)
        prefs.updateCategoriesExclude().set(emptySet())
    }

    @Test
    fun `a group page goes through the restrictions and groupLibraryUpdateType`() {
        val library = listOf(
            entry(1, listOf(1), lastRead = 10),
            entry(2, listOf(2), lastRead = 20),
            entry(3, listOf(1), status = SManga.COMPLETED),
            entry(4, listOf(1), read = 2),
            entry(6, listOf(1), strategy = UpdateStrategy.ONLY_FETCH_ONCE),
        )
        prefs.updateCategoriesExclude().set(emptySet())
        prefs.autoUpdateMangaRestrictions().set(setOf(LibraryPreferences.MANGA_NON_COMPLETED, LibraryPreferences.MANGA_HAS_UNREAD))
        val sourcePage = LibraryUpdateRunner.Target.Group(LibraryGroup.BY_SOURCE, setOf(1, 3, 4, 6))
        val ungroupedPage = LibraryUpdateRunner.Target.Group(LibraryGroup.UNGROUPED, setOf(1, 3))
        fun select(target: LibraryUpdateRunner.Target) = LibraryUpdateSelection.select(library, target, prefs, Long.MAX_VALUE)
        try {
            // ALL: the page's entries, with the restrictions (3 completed, 4 unread, 6 fetch once) and the update order
            prefs.groupLibraryUpdateType().set(GroupLibraryMode.ALL)
            select(sourcePage).toUpdate.map { it.manga.id } shouldBe listOf(1L)
            select(sourcePage).skipped.map { it.manga.id }.toSet() shouldBe setOf(3L, 4, 6)
            select(ungroupedPage).toUpdate.map { it.manga.id } shouldBe listOf(1L)
            // GLOBAL (Android's default): the whole library, like "update library"
            prefs.groupLibraryUpdateType().set(GroupLibraryMode.GLOBAL)
            select(sourcePage).toUpdate.map { it.manga.id } shouldBe listOf(2L, 1)
            // ALL_BUT_UNGROUPED: the group's entries, the whole library on the ungrouped page
            prefs.groupLibraryUpdateType().set(GroupLibraryMode.ALL_BUT_UNGROUPED)
            select(sourcePage).toUpdate.map { it.manga.id } shouldBe listOf(1L)
            select(ungroupedPage).toUpdate.map { it.manga.id } shouldBe listOf(2L, 1)
        } finally {
            prefs.groupLibraryUpdateType().delete()
            prefs.autoUpdateMangaRestrictions().delete()
        }
    }

    @Test
    fun `runner reports progress, new chapters and failures`() = runBlocking {
        prefs.autoUpdateMangaRestrictions().set(emptySet())
        prefs.updateCategoriesExclude().set(emptySet())
        val errors = mutableListOf<Long>()
        val finished = CompletableDeferred<Unit>()
        val runner = CoroutineLibraryUpdateRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            getLibrary = { (1L..4L).map { entry(it) } },
            preferences = prefs,
            fetchWindowUpperBound = { Long.MAX_VALUE },
            updateManga = { manga -> if (manga.id == 3L) error("boom") else manga.id.toInt() },
            onError = { manga, _ -> errors += manga.id },
            onFinished = { finished.complete(Unit) },
        )
        runner.start(LibraryUpdateRunner.Target.All) shouldBe true
        withTimeout(10_000) { finished.await() }
        val progress = runner.progress.first { it?.finished == true }!!
        progress.total shouldBe 4
        progress.done shouldBe 4
        progress.newChapters shouldBe 1 + 2 + 4
        progress.failed.map { it.first.id } shouldBe listOf(3L)
        errors shouldBe listOf(3L)
        progress.cancelled shouldBe false
        Unit
    }

    @Test
    fun `the runner waits until the sources are ready before it selects entries`() = runBlocking {
        prefs.autoUpdateMangaRestrictions().set(emptySet())
        val ready = CompletableDeferred<Unit>()
        var libraryReads = 0
        val runner = CoroutineLibraryUpdateRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            getLibrary = {
                libraryReads++
                listOf(entry(1))
            },
            preferences = prefs,
            fetchWindowUpperBound = { Long.MAX_VALUE },
            updateManga = { 1 },
            awaitReady = { ready.await() },
        )
        runner.start(LibraryUpdateRunner.Target.All) shouldBe true
        delay(200)
        libraryReads shouldBe 0
        runner.progress.value?.finished shouldBe false
        ready.complete(Unit)
        val progress = withTimeout(10_000) { runner.progress.first { it?.finished == true }!! }
        libraryReads shouldBe 1
        progress.done shouldBe 1
        // Cancelling while it waits ends the update as cancelled
        val never = CoroutineLibraryUpdateRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            getLibrary = { listOf(entry(1)) },
            preferences = prefs,
            fetchWindowUpperBound = { Long.MAX_VALUE },
            updateManga = { 1 },
            awaitReady = { awaitCancellation() },
        )
        never.start(LibraryUpdateRunner.Target.All) shouldBe true
        never.cancel()
        withTimeout(10_000) { never.progress.first { it?.finished == true }!! }.cancelled shouldBe true
        Unit
    }

    @Test
    fun `a throwing onStarted publishes no progress and starts nothing`() = runBlocking {
        var fail = true
        val runner = CoroutineLibraryUpdateRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            getLibrary = { listOf(entry(1)) },
            preferences = prefs,
            fetchWindowUpperBound = { Long.MAX_VALUE },
            updateManga = { 1 },
            onStarted = { if (fail) error("preference store broken") },
        )
        shouldThrow<IllegalStateException> { runner.start(LibraryUpdateRunner.Target.All) }
        runner.progress.value shouldBe null
        fail = false
        runner.start(LibraryUpdateRunner.Target.All) shouldBe true
        withTimeout(10_000) { runner.progress.first { it?.finished == true } }
        Unit
    }

    @Test
    fun `galleries of SY's excluded sources are never updated, but a single refresh is`() {
        prefs.autoUpdateMangaRestrictions().set(emptySet())
        prefs.updateCategories().set(emptySet())
        prefs.updateCategoriesExclude().set(emptySet())
        val library = listOf(entry(1, listOf(1)), entry(2, listOf(1), source = PURURIN_SOURCE_ID))
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.All, prefs, Long.MAX_VALUE).let {
            it.toUpdate.map { e -> e.manga.id } shouldBe listOf(1L)
            it.skipped shouldBe emptyList()
        }
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.Category(1), prefs, Long.MAX_VALUE)
            .toUpdate.map { it.manga.id } shouldBe listOf(1L)
        // Explicitly selected: not updated either, but reported as skipped (like a restriction would be)
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.Entries(setOf(1, 2)), prefs, Long.MAX_VALUE).let {
            it.toUpdate.map { e -> e.manga.id } shouldBe listOf(1L)
            it.excluded.map { e -> e.manga.id } shouldBe listOf(2L)
            it.skipped shouldBe emptyList()
        }
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.Single(2), prefs, Long.MAX_VALUE)
            .toUpdate.map { it.manga.id } shouldBe listOf(2L)
    }

    @Test
    fun `selected entries of excluded sources count as skipped, not as an empty update`() = runBlocking {
        prefs.autoUpdateMangaRestrictions().set(emptySet())
        val runner = CoroutineLibraryUpdateRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            getLibrary = { listOf(entry(2, source = PURURIN_SOURCE_ID)) },
            preferences = prefs,
            fetchWindowUpperBound = { Long.MAX_VALUE },
            updateManga = { error("must not be updated") },
        )
        runner.start(LibraryUpdateRunner.Target.Entries(setOf(2))) shouldBe true
        val progress = withTimeout(10_000) { runner.progress.first { it?.finished == true }!! }
        progress.total shouldBe 0
        progress.skipped shouldBe 1
        progress.excluded shouldBe 1
        Unit
    }

    @Test
    fun `the excluded sources are read once the sources are ready, for every target`() = runBlocking {
        prefs.autoUpdateMangaRestrictions().set(emptySet())
        prefs.updateCategories().set(emptySet())
        prefs.updateCategoriesExclude().set(emptySet())
        // The source manager publishes the delegated (NHentai) ids only once it loaded the extensions
        var published: List<Long> = emptyList()
        val ready = CompletableDeferred<Unit>()
        val updated = mutableListOf<Long>()
        fun runner() = CoroutineLibraryUpdateRunner(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            getLibrary = { listOf(entry(1, listOf(1)), entry(2, listOf(1), source = 77)) },
            preferences = prefs,
            fetchWindowUpperBound = { Long.MAX_VALUE },
            updateManga = { manga ->
                synchronized(updated) { updated += manga.id }
                0
            },
            awaitReady = { ready.await() },
            excludedSources = { published },
        )
        val targets = listOf(
            LibraryUpdateRunner.Target.All,
            LibraryUpdateRunner.Target.Category(1),
            LibraryUpdateRunner.Target.Entries(setOf(1, 2)),
            LibraryUpdateRunner.Target.Group(LibraryGroup.BY_SOURCE, setOf(1, 2)),
        )
        val runners = targets.map { target -> runner().also { it.start(target) shouldBe true } }
        delay(100)
        published = listOf(77L)
        ready.complete(Unit)
        runners.forEach { withTimeout(10_000) { it.progress.first { p -> p?.finished == true } } }
        updated.toSet() shouldBe setOf(1L)
        Unit
    }
}
