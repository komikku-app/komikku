package app.komikku.desktop.backup

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.shared.backup.BackupProgressListener
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.extension.interactor.GetExtensionStores
import okio.buffer
import okio.gzip
import okio.sink
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 8.2: backups with the borrowed Android creator / restorer on desktop.
 *
 * 1. A backup assembled in-test with the Android backup models (as an Android device writes it:
 *    protobuf, gzip) restores library manga, chapters, categories, history, tracking, app and source
 *    preferences, extension stores, saved searches and feeds.
 * 2. Round trip: create a backup on desktop → wipe the database and the restored preferences → restore →
 *    the same state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BackupRestoreTest {

    private lateinit var app: Application
    private lateinit var dir: File
    private val service get() = Injekt.get<DesktopBackupService>()
    private val preferenceStore get() = Injekt.get<PreferenceStore>()

    @BeforeAll
    fun boot() {
        dir = File(System.getProperty("komikku.dataDir"), "backup").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource("http://localhost:1"))) }
    }

    /** What a restored library looks like, independent of database ids. */
    data class Snapshot(
        val manga: List<String>,
        val chapters: List<String>,
        val categories: List<String>,
        val mangaCategories: List<String>,
        val history: List<String>,
        val tracks: List<String>,
        val savedSearches: List<String>,
        val feeds: List<String>,
        val stores: List<String>,
        val preferences: Map<String, Any?>,
    )

    private fun snapshot(): Snapshot = runBlocking {
        val favorites = Injekt.get<GetFavorites>().await().sortedBy { it.url }
        val chapters = favorites.flatMap { manga ->
            Injekt.get<GetChaptersByMangaId>().await(manga.id).sortedBy { it.url }
                .map { "${manga.url}|${it.url}|${it.name}|read=${it.read}|bm=${it.bookmark}|page=${it.lastPageRead}" }
        }
        val categories = Injekt.get<GetCategories>().await().filterNot { it.isSystemCategory }
        val mangaCategories = favorites.flatMap { manga ->
            Injekt.get<GetCategories>().await(manga.id).map { "${manga.url}|${it.name}" }
        }
        val history = favorites.flatMap { manga ->
            val byId = Injekt.get<GetChaptersByMangaId>().await(manga.id).associateBy { it.id }
            Injekt.get<GetHistory>().await(manga.id).map { "${byId[it.chapterId]?.url}|${it.readAt?.time}|${it.readDuration}" }
        }
        val tracks = favorites.flatMap { manga ->
            Injekt.get<GetTracks>().await(manga.id)
                .map { "${manga.url}|${it.trackerId}|${it.remoteId}|${it.title}|${it.lastChapterRead}|${it.status}|${it.score}" }
        }
        val handler = Injekt.get<DatabaseHandler>()
        val savedSearches = handler.awaitList { saved_searchQueries.selectAll { _, source, name, query, filters -> "$source|$name|$query|$filters" } }
        val feeds = handler.awaitList {
            feed_saved_searchQueries.selectAllFeedWithSavedSearch { source, global, _, name, _, _ -> "$source|$global|$name" }
        }
        val stores = Injekt.get<GetExtensionStores>().get().map { "${it.indexUrl}|${it.name}|${it.signingKey}" }
        Snapshot(
            manga = favorites.map { "${it.source}|${it.url}|${it.ogTitle}|${it.favorite}|${it.author}" },
            chapters = chapters,
            categories = categories.sortedBy { it.order }.map { "${it.name}|${it.order}" },
            mangaCategories = mangaCategories.sorted(),
            history = history.sorted(),
            tracks = tracks.sorted(),
            savedSearches = savedSearches.sorted(),
            feeds = feeds.sorted(),
            stores = stores.sorted(),
            preferences = PREF_KEYS.associateWith { preferenceStore.getAll()[it] },
        )
    }

    private fun androidBackup(): Backup = Backup(
        backupManga = listOf(
            BackupManga(
                source = FakeHttpSource.ID,
                url = "/manga/a",
                title = "Alpha",
                author = "Author A",
                favorite = true,
                dateAdded = 1_700_000_000_000,
                // Android stores category *orders* here (MangaRestorer matches them with BackupCategory.order)
                categories = listOf(0, 1),
                chapters = listOf(
                    BackupChapter(url = "/chapter/a1", name = "A1", read = true, chapterNumber = 1f, sourceOrder = 1),
                    BackupChapter(url = "/chapter/a2", name = "A2", bookmark = true, lastPageRead = 4, chapterNumber = 2f, sourceOrder = 0),
                ),
                history = listOf(BackupHistory(url = "/chapter/a1", lastRead = 1_700_000_100_000, readDuration = 60_000)),
                tracking = listOf(
                    BackupTracking(syncId = 2, libraryId = 0, mediaId = 4242, title = "Alpha", lastChapterRead = 1f, status = 1, score = 8f),
                ),
            ),
            BackupManga(
                source = FakeHttpSource.ID,
                url = "/manga/b",
                title = "Beta",
                favorite = true,
                chapters = listOf(BackupChapter(url = "/chapter/b1", name = "B1", chapterNumber = 1f)),
            ),
        ),
        backupCategories = listOf(
            BackupCategory(name = "Reading", order = 0, id = 1),
            BackupCategory(name = "Later", order = 1, id = 2),
        ),
        backupSources = listOf(BackupSource(name = "Fake online", sourceId = FakeHttpSource.ID)),
        backupPreferences = listOf(
            BackupPreference(LIBRARY_COLUMNS_KEY, IntPreferenceValue(5)),
            BackupPreference(SHOW_UNREAD_KEY, BooleanPreferenceValue(false)),
            BackupPreference("pref_test_string", StringPreferenceValue("from android")),
            // Category preferences are mapped from backup category ids to the restored ids (Android rule)
            BackupPreference(LibraryPreferences.DEFAULT_CATEGORY_PREF_KEY, IntPreferenceValue(2)),
            BackupPreference(UPDATE_CATEGORIES_KEY, StringSetPreferenceValue(setOf("1"))),
        ),
        backupSourcePreferences = listOf(
            BackupSourcePreferences("source_${FakeHttpSource.ID}", listOf(BackupPreference("quality", StringPreferenceValue("high")))),
        ),
        backupExtensionStores = listOf(
            BackupExtensionStore(
                indexUrl = "https://example.org/store/repo.json",
                name = "Example store",
                badgeLabel = "EX",
                signingKey = "ab".repeat(32),
                contactWebsite = "https://example.org",
                contactDiscord = null,
                isLegacy = false,
                extensionListUrl = null,
            ),
        ),
        backupSavedSearches = listOf(BackupSavedSearch(name = "Saved", query = "query", filterList = "[]", source = FakeHttpSource.ID)),
        backupFeeds = listOf(BackupFeed(source = FakeHttpSource.ID, global = true)),
    )

    private fun writeAndroidBackup(file: File, backup: Backup) {
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        file.sink().gzip().buffer().use { it.write(bytes) }
    }

    /** Empties the tables a backup restores and the restored preferences (foreign keys cascade). */
    private fun wipe() {
        val driver = Injekt.get<SqlDriver>()
        listOf(
            "DELETE FROM mangas",
            "DELETE FROM categories WHERE _id > 0",
            "DELETE FROM feed_saved_search",
            "DELETE FROM saved_search",
            "DELETE FROM extension_store",
        ).forEach { driver.execute(null, it, 0) }
        PREF_KEYS.forEach { preferenceStore.getString(it).delete() }
        preferenceStore.getString(Preference.privateKey("secret_token")).delete()
        app.getSharedPreferences("source_${FakeHttpSource.ID}", 0).edit().clear().commit()
    }

    /** Restores the Android-model backup into an empty database; returns the progress events. */
    private suspend fun restoreAndroidBackup(): List<String> {
        wipe()
        val file = File(dir, "android.tachibk")
        writeAndroidBackup(file, androidBackup())
        service.validate(file).missingSources shouldBe emptyList()

        val events = CopyOnWriteArrayList<String>()
        var completedErrors = -1
        service.restore(
            file,
            RestoreOptions(),
            object : BackupProgressListener {
                override fun onRestoreProgress(content: String, progress: Int, max: Int) {
                    events += "$progress/$max $content"
                }

                override fun onRestoreComplete(timeMillis: Long, errorCount: Int, errorLog: File?) {
                    completedErrors = errorCount
                }
            },
        )
        completedErrors shouldBe 0
        return events
    }

    @Test
    @Order(1)
    fun `restores a backup written with the Android backup models`() = runBlocking<Unit> {
        val events = restoreAndroidBackup()
        // 2 manga + categories + saved searches / feeds + app settings + 1 store + source settings
        events.size shouldBe 7
        events.last().substringBefore(' ') shouldBe "7/7"

        val s = snapshot()
        s.manga shouldContainExactly listOf(
            "${FakeHttpSource.ID}|/manga/a|Alpha|true|Author A",
            "${FakeHttpSource.ID}|/manga/b|Beta|true|null",
        )
        s.chapters shouldContainExactly listOf(
            "/manga/a|/chapter/a1|A1|read=true|bm=false|page=0",
            "/manga/a|/chapter/a2|A2|read=false|bm=true|page=4",
            "/manga/b|/chapter/b1|B1|read=false|bm=false|page=0",
        )
        s.categories shouldContainExactly listOf("Reading|0", "Later|1")
        s.mangaCategories shouldContainExactly listOf("/manga/a|Later", "/manga/a|Reading")
        s.history shouldContainExactly listOf("/chapter/a1|1700000100000|60000")
        s.tracks shouldContainExactly listOf("/manga/a|2|4242|Alpha|1.0|1|8.0")
        // The SY restorer stores an empty filter list ("[]") as null, like on Android
        s.savedSearches shouldContainExactly listOf("${FakeHttpSource.ID}|Saved|query|null")
        s.feeds shouldContainExactly listOf("${FakeHttpSource.ID}|true|null")
        s.stores shouldContainExactly listOf("https://example.org/store/repo.json|Example store|${"ab".repeat(32)}")

        preferenceStore.getInt(LIBRARY_COLUMNS_KEY, 0).get() shouldBe 5
        preferenceStore.getBoolean(SHOW_UNREAD_KEY, true).get() shouldBe false
        preferenceStore.getString("pref_test_string", "").get() shouldBe "from android"
        val later = Injekt.get<GetCategories>().await().single { it.name == "Later" }
        val reading = Injekt.get<GetCategories>().await().single { it.name == "Reading" }
        preferenceStore.getInt(LibraryPreferences.DEFAULT_CATEGORY_PREF_KEY, -1).get() shouldBe later.id.toInt()
        preferenceStore.getStringSet(UPDATE_CATEGORIES_KEY).get() shouldBe setOf(reading.id.toString())
        app.getSharedPreferences("source_${FakeHttpSource.ID}", 0).getString("quality", null) shouldBe "high"
    }

    @Test
    @Order(2)
    fun `round trip - create on desktop, wipe, restore, same state`() = runBlocking<Unit> {
        // Seed: the same library as test 1 (independent of it)
        restoreAndroidBackup()
        // Private / app-state preferences: excluded unless asked for, like on Android
        preferenceStore.getString(Preference.privateKey("secret_token")).set("t0ken")
        preferenceStore.getBoolean(Preference.appStateKey("first_run_done")).set(true)
        val before = snapshot()
        before.manga.size shouldBe 2

        // An existing file at the target is replaced only by a complete backup
        val target = File(dir, "desktop.tachibk").apply { writeText("old") }
        val completed = CopyOnWriteArrayList<String>()
        val file = service.create(
            target,
            BackupOptions(),
            object : BackupProgressListener {
                override fun onBackupComplete(path: String) {
                    completed += path
                }
            },
        )
        completed shouldContainExactly listOf(target.absolutePath)
        file.length() shouldNotBe 0L
        // Gzipped protobuf like Android's
        file.inputStream().use { (it.read() to it.read()) } shouldBe (0x1f to 0x8b)
        dir.listFiles()!!.none { it.name.endsWith(".tmp") } shouldBe true

        wipe()
        snapshot().manga shouldBe emptyList()

        service.restore(file, RestoreOptions())

        val after = snapshot()
        after.copy(preferences = emptyMap()) shouldBe before.copy(preferences = emptyMap())
        // Category ids changed, so category preferences are compared by name
        after.preferences.filterKeys { it !in CATEGORY_PREF_KEYS } shouldBe before.preferences.filterKeys { it !in CATEGORY_PREF_KEYS }
        val categoriesById = Injekt.get<GetCategories>().await().associateBy { it.id }
        categoriesById[(after.preferences[LibraryPreferences.DEFAULT_CATEGORY_PREF_KEY] as Int).toLong()]!!.name shouldBe "Later"
        (after.preferences[UPDATE_CATEGORIES_KEY] as Set<*>).map { categoriesById[(it as String).toLong()]!!.name } shouldContainExactlyInAnyOrder listOf("Reading")
        // Private preference not in the backup without "private settings"
        preferenceStore.getString(Preference.privateKey("secret_token")).isSet() shouldBe false
    }

    companion object {
        const val LIBRARY_COLUMNS_KEY = "pref_library_columns_portrait_key"
        const val SHOW_UNREAD_KEY = "display_unread_badge"
        const val UPDATE_CATEGORIES_KEY = "library_update_categories"
        val CATEGORY_PREF_KEYS = setOf(LibraryPreferences.DEFAULT_CATEGORY_PREF_KEY, UPDATE_CATEGORIES_KEY)
        val PREF_KEYS = listOf(LIBRARY_COLUMNS_KEY, SHOW_UNREAD_KEY, "pref_test_string") + CATEGORY_PREF_KEYS
    }
}
