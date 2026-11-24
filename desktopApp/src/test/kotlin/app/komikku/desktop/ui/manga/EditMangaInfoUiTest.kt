package app.komikku.desktop.ui.manga

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.browse.FixedSourceManager
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P10, headless: SY's "Edit info" on the desktop details. Saving stores the entry's custom info (the shared
 * `MangaInfoEdits` → `SetCustomMangaInfo`, the same `edits.json` as Android), the details show it at once, a backup
 * carries it, the dialog reopens with the edited values, and "Reset info" + save removes it again. Entries outside the
 * library have no "Edit info".
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EditMangaInfoUiTest {

    private lateinit var app: Application
    private lateinit var manga: Manga
    private lateinit var browsed: Manga

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "edit-info-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(emptyList()) }
        manga = TestLibrary.insertManga(SOURCE_ID, "/frieren", "Frieren")
        TestLibrary.insertChapters(manga, "/frieren/1" to "Chapter 1")
        runBlocking {
            Injekt.get<MangaRepository>().update(
                MangaUpdate(
                    manga.id,
                    author = "Kanehito Yamada",
                    artist = "Tsukasa Abe",
                    description = "After the demon king",
                    genre = listOf("Fantasy", "Adventure"),
                    status = SManga.ONGOING.toLong(),
                    initialized = true,
                ),
            )
        }
        browsed = TestLibrary.insertManga(SOURCE_ID, "/browsed", "Browsed", favorite = false)
        TestLibrary.insertChapters(browsed, "/browsed/1" to "Chapter 1")
    }

    private fun custom() = Injekt.get<GetCustomMangaInfo>().get(manga.id)

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.titleShown(): String? =
        onAllNodesWithTag("details-title").fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    private fun ComposeUiTest.fieldText(tag: String): String? =
        onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    private fun ComposeUiTest.details(entry: Manga) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(MangaDetailsScreen(entry.id)) } }
        }
    }

    @Test
    fun `edit info stores custom info, shows it, backs it up and resets it`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        details(manga)
        waitForTag("details-edit-info")
        onNodeWithTag("details-edit-info").performClick()
        waitForTag("edit-info-dialog")
        // A source entry starts with empty fields (only edited values) and its own tags
        fieldText("edit-info-title") shouldBe ""
        fieldText("edit-info-author") shouldBe ""
        onNodeWithTag("edit-info-tag-Fantasy").assertExists()

        onNodeWithTag("edit-info-title").performTextInput("Sousou no Frieren")
        onNodeWithTag("edit-info-author").performTextInput("  Yamada  ")
        onNodeWithTag("edit-info-tag-Adventure").performClick()
        onNodeWithTag("edit-info-new-tags").performTextInput("Elves, , Magic")
        onNodeWithTag("edit-info-add-tags").performClick()
        onNodeWithTag("edit-info-status").performClick()
        onNodeWithTag("edit-info-status-2").performClick()
        onNodeWithTag("edit-info-save").performClick()
        waitForTag("edit-info-dialog", present = false)

        waitUntil(timeoutMillis = 10_000) { custom() != null }
        val info = custom().shouldNotBeNull()
        info.title shouldBe "Sousou no Frieren"
        info.author shouldBe "Yamada"
        info.artist.shouldBeNull()
        info.description.shouldBeNull()
        info.genre shouldBe listOf("Fantasy", "Elves", "Magic")
        info.status shouldBe SManga.COMPLETED.toLong()
        waitUntil(timeoutMillis = 10_000) { titleShown() == "Sousou no Frieren" }

        // Backups carry it (SY's custom fields of BackupManga)
        val stored = runBlocking { Injekt.get<GetManga>().await(manga.id)!! }
        val backup = runBlocking { MangaBackupCreator()(listOf(stored), BackupOptions()) }.single()
        backup.customTitle shouldBe "Sousou no Frieren"
        backup.customAuthor shouldBe "Yamada"
        backup.customGenre shouldBe listOf("Fantasy", "Elves", "Magic")
        backup.customStatus shouldBe SManga.COMPLETED

        // Reopened with the edited values; "Reset info" + save removes the custom info
        onNodeWithTag("details-edit-info").performClick()
        waitForTag("edit-info-dialog")
        fieldText("edit-info-title") shouldBe "Sousou no Frieren"
        fieldText("edit-info-author") shouldBe "Yamada"
        onNodeWithTag("edit-info-reset-info").performClick()
        fieldText("edit-info-title") shouldBe ""
        onNodeWithTag("edit-info-tag-Adventure").assertExists()
        onNodeWithTag("edit-info-status").performClick()
        onNodeWithTag("edit-info-status-0").performClick()
        onNodeWithTag("edit-info-save").performClick()
        waitUntil(timeoutMillis = 10_000) { custom() == null }
        waitUntil(timeoutMillis = 10_000) { titleShown() == "Frieren" }

        // Typing the source's own value again counts as an edit of the title only; whitespace-only fields do not
        onNodeWithTag("details-edit-info").performClick()
        waitForTag("edit-info-dialog")
        onNodeWithTag("edit-info-description").performTextReplacement("   ")
        onNodeWithTag("edit-info-save").performClick()
        waitForTag("edit-info-dialog", present = false)
        custom().shouldBeNull()
    }

    @Test
    fun `entries outside the library have no edit info`() = runDesktopComposeUiTest(width = 1200, height = 900) {
        details(browsed)
        waitForTag("details-favorite")
        onAllNodesWithTag("details-edit-info").fetchSemanticsNodes().size shouldBe 0
    }

    private companion object {
        const val SOURCE_ID = 9_311L
    }
}
