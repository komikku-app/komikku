package app.komikku.desktop.backup

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.db.SqlDriver
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.platform.DesktopFileDialogs
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderLauncher
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 8.2 UI, headless, with injected file dialogs: More → Data and storage → storage location, create
 * backup (options dialog → "save" path), restore (open path → validation + options → done dialog).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackupUiTest {

    private lateinit var app: Application
    private lateinit var dir: File
    private val backupFile get() = File(dir, "picked/ui.tachibk")

    private class FixedDialogs(val file: () -> File, val folder: () -> File) : DesktopFileDialogs {
        override fun saveFile(title: String, directory: File?, suggestedName: String) = file()
        override fun openFile(title: String, directory: File?, extensions: Set<String>) = file()
        override fun chooseDirectory(title: String, directory: File?) = folder()
    }

    @BeforeAll
    fun boot() {
        dir = File(System.getProperty("komikku.dataDir"), "backup-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource("http://localhost:1"))) }
        Injekt.addSingletonFactory<DesktopFileDialogs> { FixedDialogs({ backupFile }, { File(dir, "new-storage") }) }
        val manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/ui-backup", "Backed up manga")
        TestLibrary.insertChapters(manga, "/chapter/1" to "Chapter 1")
    }

    private fun ComposeUiTest.waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `create and restore a backup from Data and storage`() = runDesktopComposeUiTest(width = 1200, height = 900) {
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-data-storage")
        onNodeWithTag("more-data-storage").performClick()
        waitForTag("data-storage")

        // Storage location through the folder chooser
        onNodeWithTag("storage-location").performClick()
        waitUntil(timeoutMillis = 5_000) {
            Injekt.get<StoragePreferences>().baseStorageDirectory().get().endsWith("/new-storage")
        }

        // Create: options dialog → "save as" → file written
        onNodeWithTag("backup-create").performClick()
        waitForTag("backup-create-confirm")
        onNodeWithTag("backup-create-confirm").performClick()
        waitForTag("backup-created")
        backupFile.isFile shouldBe true

        // Wipe and restore through the UI
        Injekt.get<SqlDriver>().execute(null, "DELETE FROM mangas", 0)
        runBlocking { Injekt.get<GetFavorites>().await() }.size shouldBe 0
        onNodeWithTag("backup-restore").performClick()
        waitForTag("backup-restore-confirm")
        onNodeWithTag("backup-restore-confirm").performClick()
        waitForTag("restore-done")
        runBlocking { Injekt.get<GetFavorites>().await() }.map { it.title } shouldBe listOf("Backed up manga")
    }
}
