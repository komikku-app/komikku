package app.komikku.desktop.library

import app.komikku.desktop.di.DesktopInjekt
import app.komikku.shared.library.CoroutineLibraryUpdateRunner
import app.komikku.shared.library.LibraryUpdateRunner
import app.komikku.shared.library.LibraryUpdateSelection
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
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
    ) = LibraryManga(
        manga = Manga.create().copy(id = id, ogTitle = "Manga $id", favorite = true, ogStatus = status.toLong(), updateStrategy = strategy),
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
        prefs.updateCategoriesExclude().set(setOf("2"))
        LibraryUpdateSelection.select(library, LibraryUpdateRunner.Target.All, prefs, Long.MAX_VALUE)
            .toUpdate.map { it.manga.id } shouldBe listOf(1L)
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
}
