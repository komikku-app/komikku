package app.komikku.desktop.reader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okio.Buffer
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Generated test images (no binary fixtures in the repository). */
object TestImages {
    fun encode(width: Int, height: Int, format: String = "png", color: Color = Color(0x33, 0x66, 0x99)): ByteArray {
        val type = if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val image = BufferedImage(width, height, type)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.color = Color.WHITE
        g.fillRect(0, 0, width / 2, height / 2)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    /** An AVIF file header (`ftyp` box, brand `avif`) followed by junk: detected as AVIF, not decodable. */
    fun avifHeader(): ByteArray =
        byteArrayOf(0, 0, 0, 0x1C) + "ftypavif".toByteArray() + ByteArray(64) { 1 }

    /** A CBZ with [count] PNG pages named `page_<n>.png` (+ a non-image entry). */
    fun cbz(file: File, count: Int, width: Int = 40, height: Int = 60) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("notes.txt"))
            zip.write("not a page".toByteArray())
            zip.closeEntry()
            (1..count).forEach { n ->
                zip.putNextEntry(ZipEntry("page_%02d.png".format(n)))
                zip.write(encode(width, height + n))
                zip.closeEntry()
            }
        }
    }
}

/**
 * A [MockWebServer] serving a manga's chapters: `/chapter/<n>` returns the image paths of that chapter
 * (one per line), `/img/<chapter>/<page>.png` a generated PNG whose height is `100 + page`.
 */
class FakeMangaServer(private val pagesPerChapter: Int = 3) : AutoCloseable {
    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<String>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                requests += path
                return when {
                    path.startsWith("/chapter/") -> {
                        val chapter = path.removePrefix("/chapter/")
                        MockResponse.Builder().code(200)
                            .body((1..pagesPerChapter).joinToString("\n") { "/img/$chapter/$it.png" })
                            .build()
                    }
                    path.startsWith("/img/") -> {
                        val page = path.substringAfterLast('/').removeSuffix(".png").toInt()
                        MockResponse.Builder().code(200)
                            .addHeader("Content-Type", "image/png")
                            .body(Buffer().write(TestImages.encode(80, 100 + page)))
                            .build()
                    }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    val baseUrl: String get() = server.url("/").toString().removeSuffix("/")

    override fun close() = server.close()
}

/**
 * An in-test online source over [FakeMangaServer]: details and chapters are fixed (no request), page
 * lists and images go through the real `HttpSource` request path and the app's OkHttp client.
 */
class FakeHttpSource(override val baseUrl: String, private val chapterCount: Int = 2) : HttpSource() {
    override val name = "Fake online"
    override val lang = "en"
    override val supportsLatest = false
    override val id: Long = ID

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = SManga.create().apply {
            url = manga.url
            title = manga.title
            author = "Fake author"
            description = "Fake description"
            initialized = true
        }
        val list = (chapterCount downTo 1).map { n ->
            SChapter.create().apply {
                url = "/chapter/$n"
                name = "Chapter $n"
                chapter_number = n.toFloat()
                date_upload = System.currentTimeMillis() - (chapterCount - n) * 60_000L
            }
        }
        return SMangaUpdate(details, list)
    }

    override fun pageListParse(response: Response): List<Page> =
        response.body.string().lines().filter { it.isNotBlank() }.mapIndexed { i, path -> Page(i, imageUrl = baseUrl + path) }

    companion object {
        const val ID = 9101L
    }
}

/** Stores a manga of [sourceId] and its chapters like browsing + `SyncChaptersWithSource` would. */
object TestLibrary {
    fun insertManga(sourceId: Long, url: String, title: String, favorite: Boolean = true): Manga = runBlocking {
        val manga = Injekt.get<NetworkToLocalManga>().invoke(
            Manga.create().copy(url = url, ogTitle = title, source = sourceId, favorite = favorite),
        )
        manga
    }

    fun insertChapters(manga: Manga, vararg urls: Pair<String, String>): List<Chapter> = runBlocking {
        Injekt.get<ChapterRepository>().addAll(
            urls.mapIndexed { i, (url, name) ->
                Chapter.create().copy(
                    mangaId = manga.id,
                    url = url,
                    name = name,
                    chapterNumber = (urls.size - i).toDouble(),
                    sourceOrder = i.toLong(),
                    dateFetch = System.currentTimeMillis(),
                )
            },
        )
    }
}
