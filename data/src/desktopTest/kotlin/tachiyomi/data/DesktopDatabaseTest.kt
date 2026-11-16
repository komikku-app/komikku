package tachiyomi.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File

/**
 * KMK: the shared SQLDelight schema, queries and repositories on the desktop driver
 * (bundled SQLite through `sqldelight-androidx-driver`).
 */
class DesktopDatabaseTest {

    @TempDir
    lateinit var tempDir: File

    private val drivers = mutableListOf<SqlDriver>()

    @AfterEach
    fun closeDrivers() {
        drivers.forEach { it.close() }
    }

    private fun open(file: File): Pair<SqlDriver, DatabaseHandler> {
        val driver = DesktopDatabase.createDriver(file).also(drivers::add)
        val database = DesktopDatabase.createDatabase(driver)
        return driver to DesktopDatabase.createHandler(database, driver, Dispatchers.IO)
    }

    @Test
    fun `new database is created at the current schema version`() {
        val file = tempDir.resolve("databases/tachiyomi.db")
        val (driver, _) = open(file)

        driver.userVersion() shouldBe Database.Schema.version
        file.isFile shouldBe true
        driver.pragmaLong("foreign_keys") shouldBe 1L
    }

    @Test
    fun `manga, chapter and category round trip through the repositories`() = runTest {
        val (_, handler) = open(tempDir.resolve("tachiyomi.db"))
        val mangaRepository = MangaRepositoryImpl(handler)
        val chapterRepository = ChapterRepositoryImpl(handler)
        val categoryRepository = CategoryRepositoryImpl(handler)

        val categoryId = categoryRepository.insert(
            Category(id = 0, name = "Reading", order = 1, flags = 0, hidden = false),
        )

        val inserted = mangaRepository.insertNetworkManga(
            listOf(
                Manga.create().copy(
                    source = 42L,
                    url = "/manga/1",
                    ogTitle = "Desktop manga",
                    ogAuthor = "Someone",
                    ogGenre = listOf("Action", "Comedy"),
                    favorite = true,
                    dateAdded = 1_000L,
                    initialized = true,
                ),
            ),
            updateInfo = true,
        ).single()
        mangaRepository.setMangaCategories(inserted.id, listOf(categoryId))

        chapterRepository.addAll(
            listOf(
                Chapter.create().copy(mangaId = inserted.id, url = "/c/1", name = "Chapter 1", chapterNumber = 1.0),
                Chapter.create().copy(mangaId = inserted.id, url = "/c/2", name = "Chapter 2", chapterNumber = 2.0),
            ),
        )

        val manga = mangaRepository.getMangaById(inserted.id)
        manga.title shouldBe "Desktop manga"
        manga.author shouldBe "Someone"
        manga.genre shouldBe listOf("Action", "Comedy")
        manga.favorite shouldBe true

        mangaRepository.getFavorites().map { it.id } shouldContainExactly listOf(inserted.id)
        mangaRepository.getLibraryManga() shouldHaveSize 1

        val chapters = chapterRepository.getChapterByMangaId(inserted.id, applyFilter = false)
        chapters.map { it.name }.sorted() shouldContainExactly listOf("Chapter 1", "Chapter 2")

        categoryRepository.getCategoriesByMangaId(inserted.id).map { it.name } shouldContainExactly listOf("Reading")
        categoryRepository.getAll().filterNot { it.isSystemCategory }.map { it.name } shouldContainExactly listOf("Reading")
    }

    @Test
    fun `data survives reopening the file`() = runTest {
        val file = tempDir.resolve("tachiyomi.db")
        val (firstDriver, firstHandler) = open(file)
        CategoryRepositoryImpl(firstHandler).insert(
            Category(id = 0, name = "Kept", order = 1, flags = 0, hidden = false),
        )
        firstDriver.close()
        drivers.remove(firstDriver)

        val (_, handler) = open(file)
        CategoryRepositoryImpl(handler).getAll().filterNot { it.isSystemCategory }.map { it.name } shouldContainExactly listOf("Kept")
    }

    @Test
    fun `schema file at version 28 migrates to the current schema`() {
        val schemaFile = File(System.getProperty("komikku.sqldelightDir"), "28.db")
        schemaFile.isFile shouldBe true

        // The .db snapshot carries the version-28 schema but user_version 0; mark it as version 28 so
        // the driver runs migrations 28.sqm .. instead of trying to create the schema.
        val oldFile = tempDir.resolve("old.db")
        schemaFile.copyTo(oldFile)
        BundledSQLiteDriver().open(oldFile.absolutePath).apply {
            execSQL("PRAGMA user_version = 28")
            close()
        }

        val (migrated, _) = open(oldFile)
        migrated.userVersion() shouldBe Database.Schema.version

        val (fresh, _) = open(tempDir.resolve("fresh.db"))
        fresh.userVersion() shouldBe Database.Schema.version

        migrated.schemaObjects() shouldContainExactly fresh.schemaObjects()
    }

    private fun SqlDriver.userVersion(): Long = pragmaLong("user_version")

    private fun SqlDriver.pragmaLong(name: String): Long =
        executeQuery(
            identifier = null,
            sql = "PRAGMA $name",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 0,
        ).value

    /** `type:name` of every table, view, index and trigger, sorted. */
    private fun SqlDriver.schemaObjects(): List<String> =
        executeQuery(
            identifier = null,
            sql = "SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name",
            mapper = { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next().value) {
                    result += "${cursor.getString(0)}:${cursor.getString(1)}"
                }
                QueryResult.Value(result)
            },
            parameters = 0,
        ).value

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfo() {
            // `Manga.title` & co. look up user edits through Injekt.
            Injekt.addSingleton(
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(mangaId: Long): CustomMangaInfo? = null
                        override fun set(mangaInfo: CustomMangaInfo) = Unit
                    },
                ),
            )
        }
    }
}
