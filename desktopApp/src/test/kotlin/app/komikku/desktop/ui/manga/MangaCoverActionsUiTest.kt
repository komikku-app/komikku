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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.platform.DesktopFileDialogs
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.shared.coil.DesktopImageLoader
import cafe.adriel.voyager.navigator.Navigator
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.decode.DataSource
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * P10, headless: the cover dialog of the desktop details on Android's cover logic. The source's cover is saved as its
 * own bytes (loaded through Coil from a stand-in server) under the title with the image's extension; "Edit cover" sets
 * a custom cover in [CoverCache] (the cover loader then uses that file, the server is not asked), "Save" then saves the
 * custom cover, "Delete" removes it; a file that is no image fails with Android's message; a cancelled chooser changes
 * nothing; entries outside the library cannot get a cover.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MangaCoverActionsUiTest {

    private lateinit var app: Application
    private lateinit var dir: File
    private lateinit var manga: Manga
    private lateinit var browsed: Manga
    private val server = MockWebServer()
    private val png = image("png")
    private val jpeg = image("jpg")
    private val dialogs = FakeDialogs()

    private class FakeDialogs : DesktopFileDialogs {
        var saveTarget: File? = null
        var openResult: File? = null
        val suggested = mutableListOf<String>()
        override fun saveFile(title: String, directory: File?, suggestedName: String): File? {
            suggested += suggestedName
            return saveTarget
        }
        override fun openFile(title: String, directory: File?, extensions: Set<String>): File? = openResult
        override fun chooseDirectory(title: String, directory: File?): File? = null
    }

    @BeforeAll
    fun boot() {
        dir = File(System.getProperty("komikku.dataDir"), "cover-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(emptyList()) }
        Injekt.addSingleton<DesktopFileDialogs>(dialogs)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse.Builder().code(200).body(Buffer().write(png)).build()
        }
        server.start()
        manga = TestLibrary.insertManga(SOURCE_ID, "/frieren", "Frieren: Beyond")
        browsed = TestLibrary.insertManga(SOURCE_ID, "/browsed", "Browsed", favorite = false)
        runBlocking {
            listOf(manga, browsed).forEach {
                TestLibrary.insertChapters(it, "${it.url}/1" to "Chapter 1")
                Injekt.get<MangaRepository>().update(
                    MangaUpdate(it.id, thumbnailUrl = server.url("/cover-${it.id}.png").toString(), initialized = true),
                )
            }
        }
    }

    @AfterAll
    fun stop() = server.close()

    private fun image(format: String, width: Int = 4): ByteArray {
        val image = BufferedImage(width, 6, BufferedImage.TYPE_INT_RGB).apply { setRGB(1, 1, 0xFF0000) }
        return ByteArrayOutputStream().also { ImageIO.write(image, if (format == "jpg") "jpeg" else format, it) }.toByteArray()
    }

    private fun stored(entry: Manga) = runBlocking { Injekt.get<GetManga>().await(entry.id)!! }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.waitForMessage(text: String) = waitUntil(timeoutMillis = 15_000) {
        onAllNodesWithTag("cover-message").fetchSemanticsNodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
        }
    }

    private fun ComposeUiTest.details(entry: Manga) = setContent {
        setSingletonImageLoaderFactory { context ->
            DesktopImageLoader.create(context, Injekt.get<NetworkHelper>(), Injekt.get<SourceManager>(), dir.resolve("cache"), Injekt.get<CoverCache>())
        }
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(MangaDetailsScreen(entry.id)) } }
        }
    }

    @Test
    fun `save, edit, save the custom cover, delete it`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        val coverCache = Injekt.get<CoverCache>()
        details(manga)
        waitForTag("details-cover")
        onNodeWithTag("details-cover").performClick()
        waitForTag("cover-dialog")
        waitForTag("cover-edit")
        onAllNodesWithTag("cover-delete").fetchSemanticsNodes().size shouldBe 0

        // A cancelled chooser: no file, no message
        dialogs.saveTarget = null
        onNodeWithTag("cover-save").performClick()
        waitUntil(timeoutMillis = 15_000) { dialogs.suggested.size == 1 }
        dialogs.suggested.last() shouldBe "Frieren_ Beyond.png"

        // Save: the source cover's own bytes
        val saved = dir.resolve("saved-cover.png")
        dialogs.saveTarget = saved
        onNodeWithTag("cover-save").performClick()
        waitForMessage(MR.strings.cover_saved.localized())
        saved.readBytes().toList() shouldBe png.toList()

        // Edit: a custom cover from a file; the loader serves it without asking the server
        val before = stored(manga).coverLastModified
        val picked = dir.resolve("picked.jpg").apply { writeBytes(jpeg) }
        dialogs.openResult = picked
        onNodeWithTag("cover-edit").performClick()
        waitForMessage(MR.strings.cover_updated.localized())
        coverCache.getCustomCoverFile(manga.id).readBytes().toList() shouldBe jpeg.toList()
        waitForTag("cover-delete")
        (stored(manga).coverLastModified > before) shouldBe true
        val requests = server.requestCount
        val result = runBlocking {
            SingletonImageLoader.get(PlatformContext.INSTANCE).execute(
                ImageRequest.Builder(PlatformContext.INSTANCE).data(stored(manga).asMangaCover())
                    .memoryCachePolicy(CachePolicy.DISABLED).build(),
            )
        }
        (result as SuccessResult).dataSource shouldBe DataSource.DISK
        server.requestCount shouldBe requests

        // Save now saves the custom cover, named with its own extension
        val savedCustom = dir.resolve("saved-custom.jpg")
        dialogs.saveTarget = savedCustom
        onNodeWithTag("cover-save").performClick()
        // The file exists before its bytes are written
        waitUntil(timeoutMillis = 15_000) { savedCustom.length() == jpeg.size.toLong() }
        savedCustom.readBytes().toList() shouldBe jpeg.toList()
        dialogs.suggested.last() shouldBe "Frieren_ Beyond.jpg"

        // A file that is no image fails with Android's message and keeps the custom cover
        dialogs.openResult = dir.resolve("notes.png").apply { writeText("not an image") }
        onNodeWithTag("cover-edit").performClick()
        waitForMessage(MR.strings.notification_cover_update_failed.localized())
        coverCache.getCustomCoverFile(manga.id).exists() shouldBe true

        // Delete the custom cover
        onNodeWithTag("cover-delete").performClick()
        waitForMessage(MR.strings.cover_updated.localized())
        waitForTag("cover-delete", present = false)
        coverCache.getCustomCoverFile(manga.id).exists() shouldBe false
        onNodeWithText(MR.strings.action_close.localized()).performClick()
        waitForTag("cover-dialog", present = false)

        // Removing the entry from the library drops a custom cover again (Android `removeCovers`)
        coverCache.getCustomCoverFile(manga.id).writeBytes(jpeg)
        onNodeWithTag("details-favorite").performClick()
        waitUntil(timeoutMillis = 10_000) { !stored(manga).favorite }
        waitUntil(timeoutMillis = 10_000) { !coverCache.getCustomCoverFile(manga.id).exists() }
    }

    @Test
    fun `entries outside the library can save but not edit their cover`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        details(browsed)
        waitForTag("details-cover")
        onNodeWithTag("details-cover").performClick()
        waitForTag("cover-save")
        onAllNodesWithTag("cover-edit").fetchSemanticsNodes().size shouldBe 0
    }

    // Review fixes (P10): cache keys, saving a cover that is only in the memory cache, refused covers

    private fun loader(name: String) = DesktopImageLoader.create(
        PlatformContext.INSTANCE,
        Injekt.get<NetworkHelper>(),
        Injekt.get<SourceManager>(),
        dir.resolve("cache-$name"),
        Injekt.get<CoverCache>(),
    )

    private fun ImageLoader.width(entry: Manga) = runBlocking {
        (execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(entry.asMangaCover()).build()) as SuccessResult).image.width
    }

    @Test
    fun `a custom cover deleted without a new cover time is not served from the memory cache`() {
        val coverCache = Injekt.get<CoverCache>()
        val entry = TestLibrary.insertManga(SOURCE_ID, "/keyed", "Keyed")
        runBlocking {
            Injekt.get<MangaRepository>().update(MangaUpdate(entry.id, thumbnailUrl = server.url("/keyed.png").toString()))
        }
        val loader = loader("keyer")
        coverCache.getCustomCoverFile(entry.id).writeBytes(image("jpg", width = 8))
        loader.width(stored(entry)) shouldBe 8
        // Like the shared `removeMangas`: the custom cover goes, `coverLastModified` stays
        coverCache.deleteCustomCover(entry.id)
        loader.width(stored(entry)) shouldBe 4
    }

    @Test
    fun `save loads a cover that is only in the memory cache`() {
        val entry = TestLibrary.insertManga(SOURCE_ID, "/memory", "Memory only")
        runBlocking {
            Injekt.get<MangaRepository>().update(MangaUpdate(entry.id, thumbnailUrl = server.url("/memory.png").toString()))
        }
        val loader = loader("memory")
        val cover = stored(entry).asMangaCover()
        runBlocking {
            loader.execute(
                ImageRequest.Builder(PlatformContext.INSTANCE).data(cover).diskCachePolicy(CachePolicy.DISABLED).build(),
            ) as SuccessResult
        }
        loader.diskCache!!.openSnapshot(cover.url!!) shouldBe null
        val target = dir.resolve("memory-saved.png")
        val actions = MangaCoverActions(imageLoader = { loader })
        runBlocking { actions.saveCover(stored(entry)) { target } } shouldBe true
        target.readBytes().toList() shouldBe png.toList()
    }

    @Test
    fun `save copies a local entry's cover file`() {
        val file = dir.resolve("local-cover.jpg").apply { writeBytes(jpeg) }
        val entry = TestLibrary.insertManga(LocalSource.ID, "local-folder", "Local")
        runBlocking { Injekt.get<MangaRepository>().update(MangaUpdate(entry.id, thumbnailUrl = file.absolutePath)) }
        val target = dir.resolve("local-saved.jpg")
        runBlocking { MangaCoverActions().saveCover(stored(entry)) { target } } shouldBe true
        target.readBytes().toList() shouldBe jpeg.toList()
    }

    @Test
    fun `a cover this machine cannot decode or an entry outside the library is refused`() {
        val coverCache = Injekt.get<CoverCache>()
        val entry = TestLibrary.insertManga(SOURCE_ID, "/refused", "Refused")
        val picked = dir.resolve("undecodable.jpg").apply { writeBytes(jpeg) }
        val before = stored(entry).coverLastModified
        shouldThrow<IOException> {
            runBlocking { MangaCoverActions(decodes = { false }).setCover(stored(entry), picked) }
        }
        coverCache.getCustomCoverFile(entry.id).exists() shouldBe false
        stored(entry).coverLastModified shouldBe before
        // Not in the library: Android's `editCover` would do nothing; here the caller gets a failure
        shouldThrow<IOException> { runBlocking { MangaCoverActions().setCover(stored(browsed), picked) } }
        coverCache.getCustomCoverFile(browsed.id).exists() shouldBe false
        // The real check: Skia decodes the JPEG, not a PNG signature followed by garbage
        MangaCoverActions.decodesWithSkia(picked) shouldBe true
        val broken = dir.resolve("broken.png").apply { writeBytes(png.copyOf(16) + ByteArray(64)) }
        MangaCoverActions.decodesWithSkia(broken) shouldBe false
    }

    private companion object {
        const val SOURCE_ID = 9_321L
    }
}
