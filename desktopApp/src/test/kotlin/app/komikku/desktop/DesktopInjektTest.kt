package app.komikku.desktop

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Boots the desktop Injekt graph ([DesktopInjekt]) against a temporary data dir and runs domain
 * interactors against the freshly created (empty) database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopInjektTest {

    private lateinit var dataDir: File

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dir: File) {
        dataDir = dir
        app = DesktopInjekt.init(dataDir)
    }

    @Test
    fun `graph resolves the basics`() {
        Injekt.get<Application>() shouldBeSameInstanceAs app
        Injekt.get<PreferenceStore>() shouldBeSameInstanceAs Injekt.get<PreferenceStore>()
        Injekt.get<DatabaseHandler>() shouldBeSameInstanceAs Injekt.get<DatabaseHandler>()
        Injekt.get<NetworkHelper>().client.cache?.directory?.startsWith(dataDir) shouldBe true

        // One instance of every registered interactor family resolves.
        Injekt.get<GetManga>().shouldBeInstanceOf<GetManga>()
        Injekt.get<GetChaptersByMangaId>().shouldBeInstanceOf<GetChaptersByMangaId>()
        Injekt.get<GetHistory>().shouldBeInstanceOf<GetHistory>()
        Injekt.get<GetTracks>().shouldBeInstanceOf<GetTracks>()
        Injekt.get<GetUpdates>().shouldBeInstanceOf<GetUpdates>()
        Injekt.get<StorageManager>().shouldBeInstanceOf<StorageManager>()
        Injekt.get<LocalSourceFileSystem>().shouldBeInstanceOf<LocalSourceFileSystem>()
    }

    @Test
    fun `interactors run against the empty database`(): Unit = runBlocking {
        Injekt.get<GetLibraryManga>().await().shouldBeEmpty()
        // Only the system ("Default") category exists in a new database.
        Injekt.get<GetCategories>().await().map { it.isSystemCategory } shouldContainExactly listOf(true)

        val created = Injekt.get<CreateCategoryWithName>().await("Desktop")
        created.shouldBeInstanceOf<CreateCategoryWithName.Result.Success>()

        Injekt.get<GetCategories>().await()
            .filterNot { it.isSystemCategory }
            .map { it.name } shouldContainExactly listOf("Desktop")

        dataDir.resolve("databases/tachiyomi.db").isFile shouldBe true
    }

    @Test
    fun `preferences are stored in the data dir`() {
        Injekt.get<LibraryPreferences>().categorizedDisplaySettings().set(true)

        Injekt.get<PreferenceStore>().getBoolean("categorized_display", false).get() shouldBe true
        dataDir.resolve("preferences")
            .resolve(DesktopPreferenceStore.fileNameFor(DesktopPreferenceStore.DEFAULT_STORE_NAME))
            .isFile shouldBe true
    }
}
