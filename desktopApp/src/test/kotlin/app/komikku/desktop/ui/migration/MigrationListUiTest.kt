package app.komikku.desktop.ui.migration

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
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
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
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
import kotlinx.coroutines.runBlocking
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P19, headless: Android's migration list on desktop — the configuration screen (the sources and their order, saved in
 * `migrationSources`; the options sheet), the list's automatic matching through the borrowed screen model (the entry's
 * own source finds the entry itself, which is no match; a title no source finds has no match), skip, migrate all with
 * Android's confirmation; and one entry from its details: the migration search opens at once, its pick is the entry's
 * match in the list, copy, the new entry's details.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationListUiTest {

    private lateinit var app: Application
    private val preferences by lazy { Injekt.get<SourcePreferences>() }
    private val allSources = listOf(ListSource(OLD), ListSource(NEW))
    private val sourceManager = FixedSourceManager(allSources)

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { sourceManager }
        preferences.migrationFlags().set(MigrationFlag.entries.toSet())
    }

    @BeforeEach
    fun reset() {
        sourceManager.setSources(allSources)
        sourceManager.initialized.value = true
        // No migration sources yet: the configuration preselects the enabled sources
        preferences.migrationSources().delete()
    }

    /** Extensions still loading: no sources yet. */
    private fun sourcesLoading() {
        sourceManager.initialized.value = false
        sourceManager.setSources(emptyList())
    }

    private fun sourcesLoaded() {
        sourceManager.setSources(allSources)
        sourceManager.initialized.value = true
    }

    /** Lets the screens' models start (they would read the sources now) before the sources are loaded. */
    private fun ComposeUiTest.settle() {
        Thread.sleep(1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private var navigator: Navigator? = null

    private fun ComposeUiTest.show(vararg screens: Screen) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme {
                DesktopToastHost {
                    Navigator(listOf(RootScreen) + screens) {
                        navigator = it
                        CurrentScreen()
                    }
                }
            }
        }
    }

    private fun stored(source: Long, url: String) = runBlocking { Injekt.get<GetManga>().await(url, source) }

    private fun libraryEntry(slug: String, title: String): Manga = runBlocking {
        val manga = TestLibrary.insertManga(OLD, "/$slug", title)
        val chapters = TestLibrary.insertChapters(manga, "/$slug/1" to "Chapter 1")
        Injekt.get<UpdateChapter>().awaitAll(chapters.map { ChapterUpdate(id = it.id, read = true) })
        Injekt.get<GetManga>().await(manga.id)!!
    }

    /** The configuration's "Continue", then the options sheet's. */
    private fun ComposeUiTest.continueToList(showsList: Boolean = true) {
        waitForTag("migration-config-continue")
        onNodeWithTag("migration-config-continue").performClick()
        val label = MR.strings.migrationConfigScreen_continueButtonText.localized()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(label) and !hasTestTag("migration-config-continue")).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodes(hasText(label) and !hasTestTag("migration-config-continue"))[0].performClick()
        if (showsList) waitForTag("migration-list")
    }

    @Test
    fun `several entries are matched, one is skipped and the rest migrated to the sources in the configured order`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val berserk = libraryEntry("berserk", "Berserk")
            val unknown = libraryEntry("unknown-title", "Unknown title")
            val vinland = libraryEntry("vinland", "Vinland")
            show(MigrationConfigScreen(listOf(berserk.id, unknown.id, vinland.id)))
            waitForTag("migration-config")
            // Both sources preselected; the new one moved to the front
            waitForTag("migration-config-up-$NEW")
            onNodeWithTag("migration-config-up-$NEW").performClick()
            waitUntil(timeoutMillis = 5_000) { preferences.migrationSources().get() == listOf(NEW, OLD) }
            continueToList()

            // The old source finds each entry itself (no match), the new one finds the known titles
            val berserkResult = hasAnyAncestor(hasTestTag("migration-result-${berserk.id}"))
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasText(ListSource.nameOf(NEW), substring = true) and berserkResult).fetchSemanticsNodes().isNotEmpty()
            }
            val unknownResult = hasAnyAncestor(hasTestTag("migration-result-${unknown.id}"))
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasText(MR.strings.migrationListScreen_noMatchFoundText.localized()) and unknownResult)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // Skip Vinland: out of the list, left alone
            waitForTag("migration-menu-${vinland.id}")
            onNodeWithTag("migration-menu-${vinland.id}").performClick()
            waitForText(MR.strings.migrationListScreen_skipActionLabel.localized())
            onNodeWithText(MR.strings.migrationListScreen_skipActionLabel.localized()).performClick()
            waitForTag("migration-item-${vinland.id}", present = false)

            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasTestTag("migration-list-migrate") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("migration-list-migrate").performClick()
            val migrateLabel = MR.strings.migrationListScreen_migrateDialog_migrateLabel.localized()
            waitForText(migrateLabel)
            onNodeWithText(migrateLabel).performClick()

            waitUntil(timeoutMillis = 15_000) { stored(NEW, "/berserk")?.favorite == true }
            stored(OLD, "/berserk")!!.favorite shouldBe false
            // No match: not migrated; skipped: not migrated
            stored(OLD, "/unknown-title")!!.favorite shouldBe true
            stored(OLD, "/vinland")!!.favorite shouldBe true
            stored(NEW, "/vinland")?.favorite shouldBe false
            // Done: back to where the migration started
            waitForTag("root")
        }

    @Test
    fun `one entry from its details is matched by the migration search, copied, and the new entry's details shown`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val vagabond = libraryEntry("vagabond", "Vagabond")
            show(MangaDetailsScreen(vagabond.id))
            waitForTag("details-migrate")
            onNodeWithTag("details-migrate").performClick()
            continueToList(showsList = false)
            // One entry without smart search (Android's default): the migration search opens at once
            waitForTag("migrate-search")
            val newRow = hasAnyAncestor(hasTestTag("global-search-source-$NEW"))
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("Vagabond") and newRow).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(hasText("Vagabond") and newRow)[0].performClick()

            // Back on the list with the pick as the match
            waitForTag("migrate-search", present = false)
            val result = hasAnyAncestor(hasTestTag("migration-result-${vagabond.id}"))
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasText(ListSource.nameOf(NEW), substring = true) and result).fetchSemanticsNodes().isNotEmpty()
            }
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasTestTag("migration-list-copy") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("migration-list-copy").performClick()
            val copyLabel = MR.strings.migrationListScreen_migrateDialog_copyLabel.localized()
            waitForText(copyLabel)
            onNodeWithText(copyLabel).performClick()

            waitUntil(timeoutMillis = 15_000) { stored(NEW, "/vagabond")?.favorite == true }
            stored(OLD, "/vagabond")!!.favorite shouldBe true
            // The new entry's details took the old one's place
            waitForTag("migration-list", present = false)
            waitForTag("details-migrate")
            val stack = navigator!!.items
            stack.size shouldBe 2
            (stack.last() as MangaDetailsScreen).mangaId shouldBe stored(NEW, "/vagabond")!!.id
        }

    @Test
    fun `the configuration waits for the sources to be loaded`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val entry = libraryEntry("pluto", "Pluto")
        sourcesLoading()
        show(MigrationConfigScreen(listOf(entry.id)))
        settle()
        onAllNodesWithTag("migration-config").fetchSemanticsNodes().isEmpty() shouldBe true
        sourcesLoaded()
        // Both sources listed and preselected (the enabled ones)
        waitForTag("migration-config-up-$NEW")
        waitForTag("migration-config-up-$OLD")
        waitForTag("migration-config-continue")
    }

    @Test
    fun `the list waits for the sources to be loaded before it matches`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val monster = libraryEntry("monster", "Monster")
        val akira = libraryEntry("akira", "Akira")
        preferences.migrationSources().set(listOf(NEW))
        sourcesLoading()
        show(MigrationListScreen(listOf(monster.id, akira.id), extraSearchQuery = null))
        settle()
        onAllNodesWithTag("migration-list").fetchSemanticsNodes().isEmpty() shouldBe true
        sourcesLoaded()
        val monsterResult = hasAnyAncestor(hasTestTag("migration-result-${monster.id}"))
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(ListSource.nameOf(NEW), substring = true) and monsterResult).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `leaving the list replaces only the migrated entry's details and keeps unrelated screens`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            val other = libraryEntry("blue-period", "Blue Period")
            val migrated = libraryEntry("planetes", "Planetes")
            // Details(other) under Details(migrated); the migration starts from the latter
            show(MangaDetailsScreen(other.id), MangaDetailsScreen(migrated.id))
            waitForTag("details-migrate")
            onNodeWithTag("details-migrate").performClick()
            continueToList(showsList = false)
            waitForTag("migrate-search")
            val newRow = hasAnyAncestor(hasTestTag("global-search-source-$NEW"))
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("Planetes") and newRow).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(hasText("Planetes") and newRow)[0].performClick()
            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasTestTag("migration-list-migrate") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("migration-list-migrate").performClick()
            val migrateLabel = MR.strings.migrationListScreen_migrateDialog_migrateLabel.localized()
            waitForText(migrateLabel)
            onNodeWithText(migrateLabel).performClick()

            waitUntil(timeoutMillis = 15_000) { stored(NEW, "/planetes")?.favorite == true }
            val target = stored(NEW, "/planetes")!!
            waitUntil(timeoutMillis = 15_000) { navigator!!.items.none { it is MigrationListScreen } }
            val stack = navigator!!.items
            stack.size shouldBe 3
            stack[0] shouldBe RootScreen
            (stack[1] as MangaDetailsScreen).mangaId shouldBe other.id
            (stack[2] as MangaDetailsScreen).mangaId shouldBe target.id
        }

    private companion object {
        const val OLD = 9_061L
        const val NEW = 9_062L
    }
}

/** Where the migration starts in these tests. */
private object RootScreen : Screen() {
    @Composable
    override fun Content() {
        Text("root", modifier = Modifier.testTag("root"))
    }
}

/** A source whose search finds the query as one entry (nothing for "Unknown …"); its chapters are 1 to 3. */
private class ListSource(override val id: Long) : HttpSource() {
    override val name: String = nameOf(id)
    override val lang: String = "en"
    override val baseUrl: String = "https://migration-list-$id.example.org"
    override val supportsLatest: Boolean = false

    private fun slug(title: String) = "/" + title.lowercase().replace(' ', '-')

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(
        if (query.startsWith("Unknown")) {
            emptyList()
        } else {
            listOf(
                SManga.create().apply {
                    url = slug(query)
                    title = query
                },
            )
        },
        hasNextPage = false,
    )

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga,
        (3 downTo 1).map { n ->
            SChapter.create().apply {
                url = "${manga.url}/$n"
                name = "Chapter $n"
                chapter_number = n.toFloat()
            }
        },
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    companion object {
        fun nameOf(id: Long) = "List source $id"
    }
}
