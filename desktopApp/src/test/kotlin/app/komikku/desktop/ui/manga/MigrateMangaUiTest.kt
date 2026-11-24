package app.komikku.desktop.ui.manga

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
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
import app.komikku.desktop.ui.browse.CatalogueScreen
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.migration.MigrationConfigScreen
import app.komikku.desktop.ui.migration.MigrationListScreen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
 * P18 / P19, headless: Android's single-entry migration from the details, through the screens users reach — the
 * details' "Migrate" → the migration configuration and its options sheet → the migration list, which (one entry, no
 * smart search: Android's default) opens the migration search at once; the search shows the entry's title in the
 * migration sources (the current source's row marked, the entry itself left out), the pick becomes the entry's match in
 * the list, and "Migrate now" moves the entry: chapters' read state by chapter number (and the chapters up to the last
 * read one), categories, notes, the old entry out of the library, the new one in, its details shown; "Copy now" keeps
 * both and shows the copy's details; a failed migration is reported and the entry stays in the list.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrateMangaUiTest {

    private lateinit var app: Application
    private val newSource = MigrationSource(NEW)
    private var navigator: Navigator? = null

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(MigrationSource(OLD), newSource)) }
        // Android's default: every flag
        Injekt.get<SourcePreferences>().migrationFlags().set(MigrationFlag.entries.toSet())
    }

    @BeforeEach
    fun reset() {
        newSource.failing = false
        // The configuration preselects these (the migration search searches the migration sources only)
        Injekt.get<SourcePreferences>().migrationSources().set(listOf(OLD, NEW))
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.show(vararg screens: Screen) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme {
                DesktopToastHost {
                    Navigator(screens.toList()) {
                        navigator = it
                        CurrentScreen()
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.details(manga: Manga) = show(MangaDetailsScreen(manga.id))

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

    /** The details' "Migrate", the configuration's "Continue" and the options sheet's: the migration search opens. */
    private fun ComposeUiTest.openMigrationSearch(manga: Manga) {
        details(manga)
        waitForTag("details-migrate")
        onNodeWithTag("details-migrate").performClick()
        waitForTag("migration-config-continue")
        onNodeWithTag("migration-config-continue").performClick()
        val label = MR.strings.migrationConfigScreen_continueButtonText.localized()
        val sheetContinue = hasText(label) and !hasTestTag("migration-config-continue")
        waitUntil(timeoutMillis = 15_000) { onAllNodes(sheetContinue).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(sheetContinue)[0].performClick()
        // One entry without smart search (Android's default): the migration search opens at once
        waitForTag("migrate-search")
    }

    /** The new source's result for [title] in the migration search; with [pick], it becomes the match in the list. */
    private fun ComposeUiTest.newSourceResult(manga: Manga, title: String, pick: Boolean = true) {
        val newRow = hasAnyAncestor(hasTestTag("global-search-source-$NEW"))
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(title) and newRow).fetchSemanticsNodes().isNotEmpty() }
        // The current source's row is marked like Android's ("▶ <name>")
        onAllNodesWithText("▶ ${MigrationSource.nameOf(OLD)}", substring = true).fetchSemanticsNodes().isNotEmpty() shouldBe true
        if (!pick) return
        onAllNodes(hasText(title) and newRow)[0].performClick()
        waitForTag("migrate-search", present = false)
        val result = hasAnyAncestor(hasTestTag("migration-result-${manga.id}"))
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(MigrationSource.nameOf(NEW), substring = true) and result).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The list entry's menu item [label] ("Migrate now", "Copy now", …). */
    private fun ComposeUiTest.entryAction(manga: Manga, label: String) {
        waitForTag("migration-menu-${manga.id}")
        onNodeWithTag("migration-menu-${manga.id}").performClick()
        waitForText(label)
        onNodeWithText(label).performClick()
    }

    /** The details of [mangaId] are shown, alone in the stack: the migration screens and the old details are gone. */
    private fun ComposeUiTest.showsOnlyDetailsOf(mangaId: Long) {
        waitForTag("migration-list", present = false)
        waitUntil(timeoutMillis = 15_000) {
            (navigator?.items?.singleOrNull() as? MangaDetailsScreen)?.mangaId == mangaId
        }
        waitForTag("details-favorite")
    }

    @Test
    fun `migrate now moves the read state, categories and notes and shows the new entry's details`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("frieren", "Frieren")
            openMigrationSearch(current)
            newSourceResult(current, "Frieren")
            entryAction(current, MR.strings.migrationListScreen_migrateNowActionLabel.localized())

            waitUntil(timeoutMillis = 15_000) { stored(NEW, "/frieren")?.favorite == true }
            val target = stored(NEW, "/frieren")!!
            stored(OLD, "/frieren")!!.favorite shouldBe false
            target.notes shouldBe "A note"
            runBlocking {
                Injekt.get<GetCategories>().await(target.id).map { it.name } shouldBe listOf("Reading")
                Injekt.get<GetChaptersByMangaId>().await(target.id)
                    .associate { it.chapterNumber to it.read } shouldBe mapOf(1.0 to true, 2.0 to true, 3.0 to false)
            }
            // Review fix (P19): the new entry's details replace the old one's, also after "Migrate now" on the entry
            showsOnlyDetailsOf(target.id)
            waitForTag("details-migrate")
        }

    @Test
    fun `copy now keeps the old entry in the library and shows the copy's details`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("dungeon-meshi", "Dungeon Meshi")
            openMigrationSearch(current)
            newSourceResult(current, "Dungeon Meshi")
            entryAction(current, MR.strings.migrationListScreen_copyNowActionLabel.localized())
            waitUntil(timeoutMillis = 15_000) { stored(NEW, "/dungeon-meshi")?.favorite == true }
            stored(OLD, "/dungeon-meshi")!!.favorite shouldBe true
            // Like Android's "copy" for one entry: the copy's details (the old entry stays in the library)
            showsOnlyDetailsOf(stored(NEW, "/dungeon-meshi")!!.id)
        }

    @Test
    fun `the entry itself is no migration target and the search has no pinned or all filter`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("vinland-saga", "Vinland Saga")
            openMigrationSearch(current)
            newSourceResult(current, "Vinland Saga", pick = false)
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
    fun `a failed migration is reported and the entry stays in the list`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val current = libraryEntry("broken-entry", "Broken entry")
        openMigrationSearch(current)
        newSourceResult(current, "Broken entry")
        // From now on the new source fails: the migration's refresh of the target fails
        newSource.failing = true
        val message = KMR.strings.desktop_migration_failed.localized().replace("%1\$s", "Broken entry")

        entryAction(current, MR.strings.migrationListScreen_migrateNowActionLabel.localized())
        waitForText(message)
        onAllNodesWithTag("migration-item-${current.id}").fetchSemanticsNodes().isNotEmpty() shouldBe true
        // The toast goes away before the next attempt
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithText(message).fetchSemanticsNodes().isEmpty() }

        // "Migrate" for the whole list: reported too, the list stays (no details swap)
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasTestTag("migration-list-migrate") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("migration-list-migrate").performClick()
        val migrateLabel = MR.strings.migrationListScreen_migrateDialog_migrateLabel.localized()
        waitForText(migrateLabel)
        onNodeWithText(migrateLabel).performClick()
        waitForText(message)
        waitForTag("migration-item-${current.id}")
        navigator!!.items.last().shouldBeInstanceOf<MigrationListScreen>()
        stored(OLD, "/broken-entry")!!.favorite shouldBe true
        stored(NEW, "/broken-entry")?.favorite shouldBe false
    }

    @Test
    fun `a source's own search from the migration search picks the match too`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("hyouka", "Hyouka")
            openMigrationSearch(current)
            newSourceResult(current, "Hyouka", pick = false)
            // P20, Android's `MigrateSourceSearchScreen`: the row header opens the source's own search with the query
            val newRow = hasAnyAncestor(hasTestTag("global-search-source-$NEW"))
            onAllNodes(hasText(MigrationSource.nameOf(NEW), substring = true) and newRow)[0].performClick()
            waitUntil(timeoutMillis = 15_000) { navigator?.lastItem is CatalogueScreen }
            val result = hasText("Hyouka") and !hasSetTextAction()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(result)[0].performClick()
            // Back on the list with the pick as the match
            waitUntil(timeoutMillis = 15_000) { navigator?.lastItem is MigrationListScreen }
            val match = hasAnyAncestor(hasTestTag("migration-result-${current.id}"))
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasText(MigrationSource.nameOf(NEW), substring = true) and match).fetchSemanticsNodes().isNotEmpty()
            }
        }

    @Test
    fun `without any installed migration source the search says so and leads to the configuration`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("blame", "Blame")
            // Configured, then the extension was removed: none of the stored ids resolves
            Injekt.get<SourcePreferences>().migrationSources().set(listOf(UNINSTALLED))
            show(MangaDetailsScreen(current.id), MigrationListScreen(listOf(current.id), extraSearchQuery = null))
            waitForTag("migrate-search-no-sources")
            onNodeWithTag("migrate-search-choose-sources").performClick()
            waitForTag("migration-config")
            navigator!!.items.map { it::class } shouldBe listOf(MangaDetailsScreen::class, MigrationConfigScreen::class)
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
    fun `adding an entry with a duplicate in the library can migrate the duplicate onto it`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val current = libraryEntry("pluto", "Pluto")
            val adding = TestLibrary.insertManga(NEW, "/pluto", "Pluto", favorite = false)
            details(adding)
            waitForTag("details-favorite")
            onNodeWithTag("details-favorite").performClick()
            waitForTag("duplicate-${current.id}")
            onNodeWithTag("duplicate-${current.id}").performClick()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithText(MR.strings.migration_dialog_what_to_include.localized()).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText(MR.strings.migrate.localized()).performClick()
            waitUntil(timeoutMillis = 15_000) { stored(NEW, "/pluto")?.favorite == true }
            stored(OLD, "/pluto")!!.favorite shouldBe false
            stored(NEW, "/pluto")!!.notes shouldBe "A note"
            // Android's: the dialog closes, these details stay
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithText(MR.strings.migration_dialog_what_to_include.localized()).fetchSemanticsNodes().isEmpty()
            }
            waitForTag("details-migrate")
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
        const val UNINSTALLED = 9_059L
    }
}

/** A source whose search finds the query as one entry; its chapters are 1 to 3 (while [failing], its update fails). */
private class MigrationSource(override val id: Long) : HttpSource() {
    @Volatile var failing = false

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
    ): SMangaUpdate = if (failing) {
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
