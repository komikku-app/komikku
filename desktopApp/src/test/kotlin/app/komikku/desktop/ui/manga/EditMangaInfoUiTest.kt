package app.komikku.desktop.ui.manga

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException

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
    private lateinit var failing: Manga
    private lateinit var local: Manga

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
        failing = TestLibrary.insertManga(SOURCE_ID, "/failing", "Failing")
        TestLibrary.insertChapters(failing, "/failing/1" to "Chapter 1")
        local = TestLibrary.insertManga(LocalSource.ID, "local-folder", "Local title")
        TestLibrary.insertChapters(local, "local-folder/1" to "Chapter 1")
        runBlocking {
            Injekt.get<MangaRepository>().update(
                MangaUpdate(local.id, author = "Old author", status = SManga.COMPLETED.toLong(), initialized = true),
            )
        }
        // Saving the custom info of [failing] fails (a full disk, say); the other entries use the real store
        val real = Injekt.get<CustomMangaRepository>()
        Injekt.addSingletonFactory {
            SetCustomMangaInfo(
                object : CustomMangaRepository by real {
                    override fun set(mangaInfo: CustomMangaInfo) {
                        if (mangaInfo.id == failing.id) throw IOException("No space left")
                        real.set(mangaInfo)
                    }
                },
            )
        }
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

    @Test
    fun `a failed save keeps the dialog with the input and shows the error`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        details(failing)
        waitForTag("details-edit-info")
        onNodeWithTag("details-edit-info").performClick()
        waitForTag("edit-info-dialog")
        onNodeWithTag("edit-info-title").performTextInput("Never stored")
        onNodeWithTag("edit-info-save").performClick()
        waitForTag("edit-info-error")
        onNodeWithTag("edit-info-dialog").assertExists()
        fieldText("edit-info-title") shouldBe "Never stored"
        Injekt.get<GetCustomMangaInfo>().get(failing.id).shouldBeNull()
        onNodeWithTag("edit-info-save").assertIsEnabled()
    }

    @Test
    fun `a local entry's edit shows the stored row and keeps the default status`() = runBlocking<Unit> {
        val model = MangaDetailsScreenModel(local.id)
        val getManga = Injekt.get<GetManga>()
        awaitCondition { model.state.value.manga != null }
        model.showEditMangaInfoDialog()
        model.updateMangaInfo("Local title", "New author", null, null, null, null, null)
        awaitCondition { model.state.value.dialog == null }
        val row = getManga.await(local.id)!!
        row.author shouldBe "New author"
        row.status shouldBe SManga.COMPLETED.toLong()
        // What is shown is the stored row as the subscription brings it, not a copy made before the save
        awaitCondition { model.state.value.manga == getManga.await(local.id) }
        delay(300)
        model.state.value.manga shouldBe getManga.await(local.id)
        model.state.value.manga!!.status shouldBe SManga.COMPLETED.toLong()
    }

    private suspend fun awaitCondition(condition: suspend () -> Boolean) {
        withTimeout(10_000) { while (!condition()) delay(20) }
    }

    private companion object {
        const val SOURCE_ID = 9_311L
    }
}
