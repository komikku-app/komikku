package app.komikku.desktop.ui.manga

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.browse.FixedSourceManager
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException

/**
 * P18, headless: Android's single-entry migration from the details — "Migrate" opens the migration search with the
 * entry's title (the current source's row marked), a result of another source opens Mihon's "what to include" dialog,
 * and "Migrate" moves the entry: chapters' read state by chapter number (and the chapters up to the last read one),
 * categories, notes, the old entry out of the library, the new one in, its details shown; "Copy" keeps both.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrateMangaUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> {
            FixedSourceManager(listOf(MigrationSource(OLD), MigrationSource(NEW, failingTitle = BROKEN)))
        }
        // Android's default: every flag
        Injekt.get<SourcePreferences>().migrationFlags().set(MigrationFlag.entries.toSet())
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.details(manga: Manga) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(MangaDetailsScreen(manga.id)) } }
        }
    }

    private fun stored(source: Long, url: String) = runBlocking { Injekt.get<GetManga>().await(url, source) }

    /** A library entry of the old source with chapters 1 and 2 (read), a category and a note. */
    private fun libraryEntry(slug: String, title: String): Manga = runBlocking {
        val manga = TestLibrary.insertManga(OLD, "/$slug", title)
        val chapters = TestLibrary.insertChapters(manga, "/$slug/2" to "Chapter 2", "/$slug/1" to "Chapter 1")
        Injekt.get<UpdateChapter>().awaitAll(chapters.map { ChapterUpdate(id = it.id, read = true) })
        Injekt.get<CreateCategoryWithName>().await("Reading")
        val category = Injekt.get<GetCategories>().await().first { it.name == "Reading" }
        Injekt.get<SetMangaCategories>().await(manga.id, listOf(category.id))
        Injekt.get<UpdateManga>().await(MangaUpdate(id = manga.id, notes = "A note"))
        Injekt.get<GetManga>().await(manga.id)!!
    }

    private fun ComposeUiTest.pickNewSourceResult(title: String, openDialog: Boolean = true) {
        onNodeWithTag("details-migrate").performClick()
        waitForTag("migrate-search")
        val newRow = hasAnyAncestor(hasTestTag("global-search-source-$NEW"))
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(title) and newRow).fetchSemanticsNodes().isNotEmpty() }
        // The current source's row is marked like Android's ("▶ <name>")
        onAllNodesWithText("▶ ${MigrationSource.nameOf(OLD)}", substring = true).fetchSemanticsNodes().isNotEmpty() shouldBe true
        if (!openDialog) return
        onAllNodes(hasText(title) and newRow)[0].performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithText(MR.strings.migration_dialog_what_to_include.localized()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `migrate moves the read state, categories and notes to the other source's entry`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("frieren", "Frieren")
            details(current)
            waitForTag("details-migrate")
            pickNewSourceResult("Frieren")
            onNodeWithText(MR.strings.migrate.localized()).performClick()

            waitUntil(timeoutMillis = 15_000) { stored(NEW, "/frieren")?.favorite == true }
            val target = stored(NEW, "/frieren")!!
            stored(OLD, "/frieren")!!.favorite shouldBe false
            target.notes shouldBe "A note"
            runBlocking {
                Injekt.get<GetCategories>().await(target.id).map { it.name } shouldBe listOf("Reading")
                Injekt.get<GetChaptersByMangaId>().await(target.id)
                    .associate { it.chapterNumber to it.read } shouldBe mapOf(1.0 to true, 2.0 to true, 3.0 to false)
            }
            // The new entry's details replace the search
            waitForTag("migrate-search", present = false)
            waitForTag("details-migrate")
        }

    @Test
    fun `copy keeps the old entry in the library`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val current = libraryEntry("dungeon-meshi", "Dungeon Meshi")
        details(current)
        waitForTag("details-migrate")
        pickNewSourceResult("Dungeon Meshi")
        onNodeWithText(MR.strings.copy.localized()).performClick()
        waitUntil(timeoutMillis = 15_000) { stored(NEW, "/dungeon-meshi")?.favorite == true }
        stored(OLD, "/dungeon-meshi")!!.favorite shouldBe true
    }

    @Test
    fun `the entry itself is no migration target and the search has no pinned or all filter`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("vinland-saga", "Vinland Saga")
            details(current)
            waitForTag("details-migrate")
            pickNewSourceResult("Vinland Saga", openDialog = false)
            // Its own source's row finds the entry itself, which is left out (and so "no results" there)
            val oldRow = hasAnyAncestor(hasTestTag("global-search-source-$OLD"))
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasText(MR.strings.no_results_found.localized()) and oldRow).fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodes(hasText("Vinland Saga") and oldRow).fetchSemanticsNodes().isEmpty() shouldBe true
            // Android's migration search hides the source filter (`hideSourceFilter = true`)
            onAllNodesWithText(MR.strings.all.localized()).fetchSemanticsNodes().isEmpty() shouldBe true
            onAllNodesWithText(MR.strings.pinned_sources.localized()).fetchSemanticsNodes().isEmpty() shouldBe true
        }

    @Test
    fun `a failed migration is reported and the search stays`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val current = libraryEntry("broken-entry", BROKEN)
        details(current)
        waitForTag("details-migrate")
        pickNewSourceResult(BROKEN)
        onNodeWithText(MR.strings.migrate.localized()).performClick()
        val message = KMR.strings.desktop_migration_failed.localized().replace("%1\$s", BROKEN)
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithTag("migrate-search").fetchSemanticsNodes().isNotEmpty() shouldBe true
        stored(OLD, "/broken-entry")!!.favorite shouldBe true
        stored(NEW, "/broken-entry")?.favorite shouldBe false
    }

    @Test
    fun `the migration use case does not migrate an entry onto itself`() {
        val current = libraryEntry("monster", "Monster")
        runBlocking { Injekt.get<MigrateMangaUseCase>()(current, current, replace = true) } shouldBe false
        val after = stored(OLD, "/monster")!!
        after.favorite shouldBe true
        after.dateAdded shouldBe current.dateAdded
    }

    @Test
    fun `entries outside the library offer no migration`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val browsed = TestLibrary.insertManga(OLD, "/not-in-library", "Not in library", favorite = false)
        details(browsed)
        waitForTag("details-favorite")
        onAllNodesWithTag("details-migrate").fetchSemanticsNodes().isEmpty() shouldBe true
    }

    private companion object {
        const val OLD = 9_051L
        const val NEW = 9_052L
        const val BROKEN = "Broken entry"
    }
}

/** A source whose search finds the query as one entry; its chapters are 1 to 3 ([failingTitle]: its update fails). */
private class MigrationSource(override val id: Long, private val failingTitle: String? = null) : HttpSource() {
    override val name: String = nameOf(id)
    override val lang: String = "en"
    override val baseUrl: String = "https://migration-$id.example.org"
    override val supportsLatest: Boolean = false

    private fun slug(title: String) = "/" + title.lowercase().replace(' ', '-')

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(
        listOf(
            SManga.create().apply {
                url = slug(query)
                title = query
            },
        ),
        hasNextPage = false,
    )

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = if (manga.title == failingTitle) {
        throw IOException("HTTP 500")
    } else {
        SMangaUpdate(
            manga,
            (3 downTo 1).map { n ->
                SChapter.create().apply {
                    url = "${manga.url}/$n"
                    name = "Chapter $n"
                    chapter_number = n.toFloat()
                }
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    companion object {
        fun nameOf(id: Long) = "Migration source $id"
    }
}
