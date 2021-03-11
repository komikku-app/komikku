package exh

import android.content.Context
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.resolvers.MangaUrlPutResolver
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.updater.UpdaterJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.NHentai
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

                // Fresh install
                if (oldVersion == 0) {
                    // Set up default background tasks
                    UpdaterJob.setupTask(context)
                    ExtensionUpdateJob.setupTask(context)
                    LibraryUpdateJob.setupTask(context)
                    return false
                }
                if (oldVersion < 4) {
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
                if (oldVersion < 5) {
                    db.inTransaction {
                        // Migrate Hitomi source IDs
                        updateSourceId(Hitomi.otherId, 6910)
                    }
                }
                if (oldVersion < 6) {
                    db.inTransaction {
                        updateSourceId(PERV_EDEN_EN_SOURCE_ID, 6905)
                        updateSourceId(PERV_EDEN_IT_SOURCE_ID, 6906)
                        updateSourceId(NHentai.otherId, 6907)
                    }
                }
                if (oldVersion < 7) {
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
                if (oldVersion < 12) {
                    // Force MAL log out due to login flow change
                    val trackManager = Injekt.get<TrackManager>()
                    trackManager.myAnimeList.logout()
                }

                // if (oldVersion < 1) { } (1 is current release version)
                // do stuff here when releasing changed crap

                // TODO BE CAREFUL TO NOT FUCK UP MergedSources IF CHANGING URLs

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
