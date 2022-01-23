package exh

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.resolvers.MangaUrlPutResolver
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.database.tables.TrackTable
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.MANGA_ONGOING
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.util.preference.minusAssign
import exh.eh.EHentaiUpdateWorker
import exh.log.xLogE
import exh.log.xLogW
import exh.merged.sql.models.MergedMangaReference
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import exh.source.PERV_EDEN_EN_SOURCE_ID
import exh.source.PERV_EDEN_IT_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.util.under
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URI
import java.net.URISyntaxException

object EXHMigrations {
    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(preferences: PreferencesHelper): Boolean {
        val context = preferences.context
        val oldVersion = preferences.ehLastVersionCode().get()
        try {
            if (oldVersion < BuildConfig.VERSION_CODE) {
                preferences.ehLastVersionCode().set(BuildConfig.VERSION_CODE)

                if (BuildConfig.INCLUDE_UPDATER) {
                    AppUpdateJob.setupTask(context)
                }
                ExtensionUpdateJob.setupTask(context)
                LibraryUpdateJob.setupTask(context)
                BackupCreatorJob.setupTask(context)
                EHentaiUpdateWorker.scheduleBackground(context)

                // Fresh install
                if (oldVersion == 0) {
                    return false
                }

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                if (oldVersion under 4) {
                    db.inTransaction {
                        updateSourceId(HBROWSE_SOURCE_ID, 6912)
                        // Migrate BHrowse URLs
                        val hBrowseManga = db.db.get()
                            .listOfObjects(Manga::class.java)
                            .withQuery(
                                Query.builder()
                                    .table(MangaTable.TABLE)
                                    .where("${MangaTable.COL_SOURCE} = $HBROWSE_SOURCE_ID")
                                    .build()
                            )
                            .prepare()
                            .executeAsBlocking()
                        hBrowseManga.forEach {
                            it.url = it.url + "/c00001/"
                        }

                        db.db.put()
                            .objects(hBrowseManga)
                            // Extremely slow without the resolver :/
                            .withPutResolver(MangaUrlPutResolver())
                            .prepare()
                            .executeAsBlocking()
                    }
                }
                if (oldVersion under 5) {
                    db.inTransaction {
                        // Migrate Hitomi source IDs
                        updateSourceId(Hitomi.otherId, 6910)
                    }
                }
                if (oldVersion under 6) {
                    db.inTransaction {
                        updateSourceId(PERV_EDEN_EN_SOURCE_ID, 6905)
                        updateSourceId(PERV_EDEN_IT_SOURCE_ID, 6906)
                        updateSourceId(NHentai.otherId, 6907)
                    }
                }
                if (oldVersion under 7) {
                    db.inTransaction {
                        val mergedMangas = db.db.get()
                            .listOfObjects(Manga::class.java)
                            .withQuery(
                                Query.builder()
                                    .table(MangaTable.TABLE)
                                    .where("${MangaTable.COL_SOURCE} = $MERGED_SOURCE_ID")
                                    .build()
                            )
                            .prepare()
                            .executeAsBlocking()

                        if (mergedMangas.isNotEmpty()) {
                            val mangaConfigs = mergedMangas.mapNotNull { mergedManga -> readMangaConfig(mergedManga)?.let { mergedManga to it } }
                            if (mangaConfigs.isNotEmpty()) {
                                val mangaToUpdate = mutableListOf<Manga>()
                                val mergedMangaReferences = mutableListOf<MergedMangaReference>()
                                mangaConfigs.onEach { mergedManga ->
                                    mergedManga.second.children.firstOrNull()?.url?.let {
                                        if (db.getManga(it, MERGED_SOURCE_ID).executeAsBlocking() != null) return@onEach
                                        mergedManga.first.url = it
                                    }
                                    mangaToUpdate += mergedManga.first
                                    mergedMangaReferences += MergedMangaReference(
                                        id = null,
                                        isInfoManga = false,
                                        getChapterUpdates = false,
                                        chapterSortMode = 0,
                                        chapterPriority = 0,
                                        downloadChapters = false,
                                        mergeId = mergedManga.first.id!!,
                                        mergeUrl = mergedManga.first.url,
                                        mangaId = mergedManga.first.id!!,
                                        mangaUrl = mergedManga.first.url,
                                        mangaSourceId = MERGED_SOURCE_ID
                                    )
                                    mergedManga.second.children.distinct().forEachIndexed { index, mangaSource ->
                                        val load = mangaSource.load(db, sourceManager) ?: return@forEachIndexed
                                        mergedMangaReferences += MergedMangaReference(
                                            id = null,
                                            isInfoManga = index == 0,
                                            getChapterUpdates = true,
                                            chapterSortMode = 0,
                                            chapterPriority = 0,
                                            downloadChapters = true,
                                            mergeId = mergedManga.first.id!!,
                                            mergeUrl = mergedManga.first.url,
                                            mangaId = load.manga.id!!,
                                            mangaUrl = load.manga.url,
                                            mangaSourceId = load.source.id
                                        )
                                    }
                                }
                                db.db.put()
                                    .objects(mangaToUpdate)
                                    // Extremely slow without the resolver :/
                                    .withPutResolver(MangaUrlPutResolver())
                                    .prepare()
                                    .executeAsBlocking()
                                db.insertMergedMangas(mergedMangaReferences).executeAsBlocking()

                                val loadedMangaList = mangaConfigs.map { it.second.children }.flatten().mapNotNull { it.load(db, sourceManager) }.distinct()
                                val chapters = db.db.get()
                                    .listOfObjects(Chapter::class.java)
                                    .withQuery(
                                        Query.builder()
                                            .table(ChapterTable.TABLE)
                                            .where("${ChapterTable.COL_MANGA_ID} IN (${mergedMangas.filter { it.id != null }.joinToString { it.id.toString() }})")
                                            .build()
                                    )
                                    .prepare()
                                    .executeAsBlocking()
                                val mergedMangaChapters = db.db.get()
                                    .listOfObjects(Chapter::class.java)
                                    .withQuery(
                                        Query.builder()
                                            .table(ChapterTable.TABLE)
                                            .where("${ChapterTable.COL_MANGA_ID} IN (${loadedMangaList.filter { it.manga.id != null }.joinToString { it.manga.id.toString() }})")
                                            .build()
                                    )
                                    .prepare()
                                    .executeAsBlocking()
                                val mergedMangaChaptersMatched = mergedMangaChapters.mapNotNull { chapter -> loadedMangaList.firstOrNull { it.manga.id == chapter.id }?.let { it to chapter } }
                                val parsedChapters = chapters.filter { it.read || it.last_page_read != 0 }.mapNotNull { chapter -> readUrlConfig(chapter.url)?.let { chapter to it } }
                                val chaptersToUpdate = mutableListOf<Chapter>()
                                parsedChapters.forEach { parsedChapter ->
                                    mergedMangaChaptersMatched.firstOrNull { it.second.url == parsedChapter.second.url && it.first.source.id == parsedChapter.second.source && it.first.manga.url == parsedChapter.second.mangaUrl }?.let {
                                        chaptersToUpdate += it.second.apply {
                                            read = parsedChapter.first.read
                                            last_page_read = parsedChapter.first.last_page_read
                                        }
                                    }
                                }
                                db.deleteChapters(mergedMangaChapters).executeAsBlocking()
                                db.updateChaptersProgress(chaptersToUpdate).executeAsBlocking()
                            }
                        }
                    }
                }
                if (oldVersion under 12) {
                    // Force MAL log out due to login flow change
                    val trackManager = Injekt.get<TrackManager>()
                    trackManager.myAnimeList.logout()
                }
                if (oldVersion under 14) {
                    // Migrate DNS over HTTPS setting
                    val wasDohEnabled = prefs.getBoolean("enable_doh", false)
                    if (wasDohEnabled) {
                        prefs.edit {
                            putInt(PreferenceKeys.dohProvider, PREF_DOH_CLOUDFLARE)
                            remove("enable_doh")
                        }
                    }
                }
                if (oldVersion under 16) {
                    // Reset rotation to Free after replacing Lock
                    if (prefs.contains("pref_rotation_type_key")) {
                        prefs.edit {
                            putInt("pref_rotation_type_key", 1)
                        }
                    }
                    // Disable update check for Android 5.x users
                    // if (BuildConfig.INCLUDE_UPDATER && Build.VERSION.SDK_INT under Build.VERSION_CODES.M) {
                    //   UpdaterJob.cancelTask(context)
                    // }
                }
                if (oldVersion under 17) {
                    // Migrate Rotation and Viewer values to default values for viewer_flags
                    val newOrientation = when (prefs.getInt("pref_rotation_type_key", 1)) {
                        1 -> OrientationType.FREE.flagValue
                        2 -> OrientationType.PORTRAIT.flagValue
                        3 -> OrientationType.LANDSCAPE.flagValue
                        4 -> OrientationType.LOCKED_PORTRAIT.flagValue
                        5 -> OrientationType.LOCKED_LANDSCAPE.flagValue
                        else -> OrientationType.FREE.flagValue
                    }

                    // Reading mode flag and prefValue is the same value
                    val newReadingMode = prefs.getInt("pref_default_viewer_key", 1)

                    prefs.edit {
                        putInt("pref_default_orientation_type_key", newOrientation)
                        remove("pref_rotation_type_key")
                        putInt("pref_default_reading_mode_key", newReadingMode)
                        remove("pref_default_viewer_key")
                    }

                    // Delete old mangadex trackers
                    db.db.lowLevel().delete(
                        DeleteQuery.builder()
                            .table(TrackTable.TABLE)
                            .where("${TrackTable.COL_SYNC_ID} = ?")
                            .whereArgs(6)
                            .build()
                    )
                }
                if (oldVersion under 18) {
                    val readerTheme = preferences.readerTheme().get()
                    if (readerTheme == 4) {
                        preferences.readerTheme().set(3)
                    }
                    val updateInterval = preferences.libraryUpdateInterval().get()
                    if (updateInterval == 1 || updateInterval == 2) {
                        preferences.libraryUpdateInterval().set(3)
                        LibraryUpdateJob.setupTask(context, 3)
                    }
                }
                if (oldVersion under 20) {
                    val oldSortingMode = prefs.getInt(PreferenceKeys.librarySortingMode, 0)
                    val oldSortingDirection = prefs.getBoolean(PreferenceKeys.librarySortingDirection, true)

                    @Suppress("DEPRECATION")
                    val newSortingMode = when (oldSortingMode) {
                        LibrarySort.ALPHA -> SortModeSetting.ALPHABETICAL
                        LibrarySort.LAST_READ -> SortModeSetting.LAST_READ
                        LibrarySort.LAST_CHECKED -> SortModeSetting.LAST_CHECKED
                        LibrarySort.UNREAD -> SortModeSetting.UNREAD
                        LibrarySort.TOTAL -> SortModeSetting.TOTAL_CHAPTERS
                        LibrarySort.LATEST_CHAPTER -> SortModeSetting.LATEST_CHAPTER
                        LibrarySort.CHAPTER_FETCH_DATE -> SortModeSetting.DATE_FETCHED
                        LibrarySort.DATE_ADDED -> SortModeSetting.DATE_ADDED
                        LibrarySort.DRAG_AND_DROP -> SortModeSetting.DRAG_AND_DROP
                        LibrarySort.TAG_LIST -> SortModeSetting.TAG_LIST
                        else -> SortModeSetting.ALPHABETICAL
                    }

                    val newSortingDirection = when (oldSortingDirection) {
                        true -> SortDirectionSetting.ASCENDING
                        else -> SortDirectionSetting.DESCENDING
                    }

                    prefs.edit(commit = true) {
                        remove(PreferenceKeys.librarySortingMode)
                        remove(PreferenceKeys.librarySortingDirection)
                    }

                    prefs.edit {
                        putString(PreferenceKeys.librarySortingMode, newSortingMode.name)
                        putString(PreferenceKeys.librarySortingDirection, newSortingDirection.name)
                    }
                }
                if (oldVersion under 21) {
                    // Setup EH updater task after migrating to WorkManager
                    EHentaiUpdateWorker.scheduleBackground(context)

                    // if (preferences.lang().get() in listOf("en-US", "en-GB")) {
                    //    preferences.lang().set("en")
                    // }
                }
                if (oldVersion under 22) {
                    // Handle removed every 3, 4, 6, and 8 hour library updates
                    val updateInterval = preferences.libraryUpdateInterval().get()
                    if (updateInterval in listOf(3, 4, 6, 8)) {
                        preferences.libraryUpdateInterval().set(12)
                        LibraryUpdateJob.setupTask(context, 12)
                    }
                }
                if (oldVersion under 23) {
                    val oldUpdateOngoingOnly = prefs.getBoolean("pref_update_only_non_completed_key", true)
                    if (!oldUpdateOngoingOnly) {
                        preferences.libraryUpdateMangaRestriction() -= MANGA_ONGOING
                    }
                }
                if (oldVersion under 24) {
                    try {
                        sequenceOf(
                            "fav-sync",
                            "fav-sync.management",
                            "fav-sync.lock",
                            "fav-sync.note"
                        ).map {
                            File(context.filesDir, it)
                        }.filter(File::exists).forEach {
                            if (it.isDirectory) {
                                it.deleteRecursively()
                            } else {
                                it.delete()
                            }
                        }
                    } catch (e: Exception) {
                        xLogE("Failed to delete old favorites database", e)
                    }
                }

                // if (oldVersion under 1) { } (1 is current release version)
                // do stuff here when releasing changed crap

                return true
            }
        } catch (e: Exception) {
            xLogE("Failed to migrate app from $oldVersion -> ${BuildConfig.VERSION_CODE}!", e)
        }
        return false
    }

    fun migrateBackupEntry(manga: Manga) {
        if (manga.source == 6905L) {
            manga.source = PERV_EDEN_EN_SOURCE_ID
        }

        if (manga.source == 6906L) {
            manga.source = PERV_EDEN_IT_SOURCE_ID
        }

        if (manga.source == 6907L) {
            // Migrate the old source to the delegated one
            manga.source = NHentai.otherId
            // Migrate nhentai URLs
            manga.url = getUrlWithoutDomain(manga.url)
        }

        // Migrate Tsumino source IDs
        if (manga.source == 6909L) {
            manga.source = TSUMINO_SOURCE_ID
        }

        if (manga.source == 6910L) {
            manga.source = Hitomi.otherId
        }

        if (manga.source == 6912L) {
            manga.source = HBROWSE_SOURCE_ID
            manga.url = manga.url + "/c00001/"
        }

        // Allow importing of EHentai extension backups
        if (manga.source in BlacklistedSources.EHENTAI_EXT_SOURCES) {
            manga.source = EH_SOURCE_ID
        }
    }

    private fun backupDatabase(context: Context, oldMigrationVersion: Int) {
        val backupLocation = File(File(context.filesDir, "exh_db_bck"), "$oldMigrationVersion.bck.db")
        if (backupLocation.exists()) return // Do not backup same version twice

        val dbLocation = context.getDatabasePath(db.lowLevel().sqliteOpenHelper().databaseName)
        try {
            dbLocation.copyTo(backupLocation, overwrite = true)
        } catch (t: Throwable) {
            xLogW("Failed to backup database!")
        }
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    @Serializable
    private data class UrlConfig(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
        @SerialName("m")
        val mangaUrl: String
    )

    @Serializable
    private data class MangaConfig(
        @SerialName("c")
        val children: List<MangaSource>
    ) {
        companion object {
            fun readFromUrl(url: String): MangaConfig? {
                return try {
                    Json.decodeFromString(url)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun readMangaConfig(manga: SManga): MangaConfig? {
        return MangaConfig.readFromUrl(manga.url)
    }

    @Serializable
    private data class MangaSource(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String
    ) {
        fun load(db: DatabaseHelper, sourceManager: SourceManager): LoadedMangaSource? {
            val manga = db.getManga(url, source).executeAsBlocking() ?: return null
            val source = sourceManager.getOrStub(source)
            return LoadedMangaSource(source, manga)
        }
    }

    private fun readUrlConfig(url: String): UrlConfig? {
        return try {
            Json.decodeFromString(url)
        } catch (e: Exception) {
            null
        }
    }

    private data class LoadedMangaSource(val source: Source, val manga: Manga)

    private fun updateSourceId(newId: Long, oldId: Long) {
        db.lowLevel().executeSQL(
            RawQuery.builder()
                .query(
                    """
                    UPDATE ${MangaTable.TABLE}
                        SET ${MangaTable.COL_SOURCE} = $newId
                        WHERE ${MangaTable.COL_SOURCE} = $oldId
                    """.trimIndent()
                )
                .affectsTables(MangaTable.TABLE)
                .build()
        )
    }
}
