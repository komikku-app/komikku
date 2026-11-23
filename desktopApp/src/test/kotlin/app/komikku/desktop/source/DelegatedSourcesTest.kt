package app.komikku.desktop.source

import android.app.Application
import androidx.core.net.toUri
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.TestLibrary
import app.komikku.shared.extension.PlatformExtensionManager
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.all.mangadex.MangaDex
import eu.kanade.tachiyomi.extension.all.nhentai.NHentai
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.filterLibraryByQuery
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.pref.DelegateSourcePreferences
import exh.source.EnhancedHttpSource
import exh.source.LIBRARY_UPDATE_EXCLUDED_SOURCES
import exh.source.isMetadataSource
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import eu.kanade.tachiyomi.source.online.all.MangaDex as MangaDexDelegate
import eu.kanade.tachiyomi.source.online.all.NHentai as NHentaiDelegate

/**
 * P4: the SY delegated sources on desktop: an installed extension source with a delegate (by class name, like
 * Android) is wrapped in an `EnhancedHttpSource` over the borrowed delegate; details go through the delegate's metadata
 * parser into the metadata tables; plain searches reach the extension, URL searches go through the borrowed URL import.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelegatedSourcesTest {

    private lateinit var app: Application
    private val extensionSource = NHentai()
    private val plainSource = FakeHttpSource("http://localhost:1")
    private val mangaDexSource = MangaDex()
    private lateinit var manager: DesktopSourceManager

    private class FixedExtensions(sources: List<eu.kanade.tachiyomi.source.Source>) : PlatformExtensionManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val installedExtensionsFlow: StateFlow<List<Extension.Installed>> = MutableStateFlow(
            listOf(
                Extension.Installed(
                    name = "Test", pkgName = "eu.kanade.tachiyomi.extension.test", versionName = "1", versionCode = 1,
                    libVersion = 1.4, lang = "all", isNsfw = false, signatureHash = "", pkgFactory = null,
                    sources = sources, icon = null, isShared = false,
                ),
            ),
        )
        override val availableExtensionsFlow: StateFlow<List<Extension.Available>> = MutableStateFlow(emptyList())
        override val untrustedExtensionsFlow: StateFlow<List<Extension.Untrusted>> = MutableStateFlow(emptyList())
        override fun getExtensionPackage(sourceId: Long): String? = null
        override fun getSourceData(id: Long): StubSource? = null
        override suspend fun findAvailableExtensions() = Unit
        override fun installExtension(extension: Extension.Available): Flow<InstallStep> = emptyFlow()
        override fun updateExtension(extension: Extension.Installed): Flow<InstallStep> = emptyFlow()
        override fun cancelInstallUpdateExtension(extension: Extension) = Unit
        override fun uninstallExtension(extension: Extension) = Unit
        override suspend fun trust(extension: Extension.Untrusted) = Unit
    }

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        manager = DesktopSourceManager(
            context = app,
            fileSystem = Injekt.get(),
            coverManager = Injekt.get(),
            stubSourceRepository = Injekt.get(),
            allowHiddenFiles = { false },
            extensionManager = FixedExtensions(listOf(extensionSource, plainSource, mangaDexSource)),
        )
        // The URL import (GalleryAdder) looks the sources up through Injekt
        Injekt.addSingletonFactory<SourceManager> { manager }
        runBlocking { withTimeout(10_000) { manager.isInitialized.first { it } } }
    }

    @Test
    fun `an extension source with a delegate is wrapped, others are not`() {
        val source = manager.get(extensionSource.id)
        source.shouldBeInstanceOf<EnhancedHttpSource>()
        source.originalSource shouldBeSameInstanceAs extensionSource
        source.enhancedSource.shouldBeInstanceOf<NHentaiDelegate>()
        source.id shouldBe extensionSource.id
        source.name shouldBe "NHentai"
        manager.get(plainSource.id) shouldBeSameInstanceAs plainSource
        DesktopDelegatedSources.delegateFor(plainSource) shouldBe null
        // The shared SY lookups (Android's handleSourceLibrary)
        nHentaiSourceIds shouldBe listOf(extensionSource.id)
        mangaDexSourceIds shouldBe listOf(mangaDexSource.id)
        isMetadataSource(extensionSource.id) shouldBe true
        isMetadataSource(plainSource.id) shouldBe false
        (extensionSource.id in LIBRARY_UPDATE_EXCLUDED_SOURCES) shouldBe true
        (mangaDexSource.id in LIBRARY_UPDATE_EXCLUDED_SOURCES) shouldBe false
    }

    @Test
    fun `every desktop delegate starts over an extension source`() {
        DesktopDelegatedSources.delegates.map { it.sourceName } shouldBe listOf("Pururin", "MangaDex", "8Muses", "NHentai", "LANraragi")
        DesktopDelegatedSources.delegates.forEach { delegate ->
            val enhanced = delegate.create(plainSource, app)
            enhanced.delegate shouldBeSameInstanceAs plainSource
            enhanced.id shouldBe plainSource.id
            (enhanced is MetadataSource<*, *>) shouldBe true
        }
    }

    @Test
    fun `MangaDex is delegated and imports its title URLs`() = runBlocking<Unit> {
        val source = manager.get(mangaDexSource.id)
        source.shouldBeInstanceOf<EnhancedHttpSource>()
        val delegate = source.enhancedSource.shouldBeInstanceOf<MangaDexDelegate>()
        delegate.matchesUri("https://mangadex.org/title/0aaa0000-1111-2222-3333-444455556666/some-title".toUri()) shouldBe true
        delegate.mapUrlToMangaUrl("https://mangadex.org/title/0aaa0000-1111-2222-3333-444455556666/some-title".toUri()) shouldBe
            "/manga/0aaa0000-1111-2222-3333-444455556666"
        // Not logged in to MDList (the borrowed tracker, no desktop login yet)
        delegate.isLogged() shouldBe false
    }

    @Test
    fun `details go through the delegate's metadata parser and are stored`() = runBlocking<Unit> {
        val source = manager.get(extensionSource.id) as EnhancedHttpSource
        val manga = TestLibrary.insertManga(extensionSource.id, "/g/1/", "Gallery 1")
        val update = source.getMangaUpdate(manga.toSManga(), emptyList(), fetchDetails = true, fetchChapters = false)
        update.manga.title shouldBe "Delegated English"
        val metadata = Injekt.get<GetFlatMetadataById>().await(manga.id)!!.raise(NHentaiSearchMetadata::class)
        metadata.nhId shouldBe 1L
        metadata.mediaId shouldBe "777"
        metadata.tags.map { it.namespace to it.name }.toSet() shouldBe setOf("tag" to "full color", "artist" to "someone")

        // The library's SY text search now matches the stored namespace tags (a metadata source, Android's lookup)
        Injekt.get<UpdateManga>().awaitUpdateFavorite(manga.id, true)
        val item = LibraryItem(
            LibraryManga(
                manga = manga, categories = listOf(0), totalChapters = 0, readCount = 0, bookmarkCount = 0, bookmarkReadCount = 0,
                chapterFlags = 0, latestUpload = 0, chapterFetchedAt = 0, lastRead = 0,
            ),
        )
        search(listOf(item), "artist:someone") shouldBe listOf(manga.id)
        search(listOf(item), "artist:nobody") shouldBe emptyList()
    }

    private suspend fun search(items: List<LibraryItem>, query: String): List<Long> = filterLibraryByQuery(
        unfiltered = items,
        query = query,
        loggedInTrackServices = emptyMap(),
        searchEngine = Injekt.get(),
        getIdsOfFavoriteMangaWithMetadata = Injekt.get(),
        getTracks = Injekt.get(),
        sourceManager = manager,
        getSearchTags = Injekt.get(),
        getSearchTitles = Injekt.get(),
        trackerManager = Injekt.get<TrackerManager>(),
    ).map { it.id }

    @Test
    fun `plain searches reach the extension, URL searches go through the URL import`() = runBlocking<Unit> {
        val source = manager.get(extensionSource.id) as EnhancedHttpSource
        source.getSearchManga(1, "query", FilterList()).mangas.single().title shouldBe "Plain result for query"
        // A gallery URL of the source: imported (details fetched, entry stored) and returned as the only result
        val imported = source.getSearchManga(1, "https://nhentai.net/g/1/", FilterList()).mangas
        imported.single().url shouldBe "/g/1/"
        imported.single().title shouldBe "Delegated English"
        // A URL no source handles: no results
        source.getSearchManga(1, "https://unknown.example.org/g/1/", FilterList()).mangas shouldBe emptyList()

        // With "use enhanced sources" off, the extension itself answers (no URL import)
        val preference = Injekt.get<DelegateSourcePreferences>().delegateSources()
        preference.set(false)
        try {
            source.getSearchManga(1, "https://nhentai.net/g/1/", FilterList()).mangas.single().title shouldBe
                "Plain result for https://nhentai.net/g/1/"
        } finally {
            preference.set(true)
        }
    }
}
