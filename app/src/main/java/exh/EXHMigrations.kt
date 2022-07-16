@file:Suppress("DEPRECATION")

package exh

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.category.categoryMapper
import eu.kanade.data.chapter.chapterMapper
import eu.kanade.domain.chapter.interactor.DeleteChapters
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetMangaBySource
import eu.kanade.domain.manga.interactor.InsertMergedReference
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.domain.source.interactor.InsertSavedSearch
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.logcat
import exh.eh.EHentaiUpdateWorker
import exh.log.xLogE
import exh.merged.sql.models.MergedMangaReference
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import exh.source.PERV_EDEN_EN_SOURCE_ID
import exh.source.PERV_EDEN_IT_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.util.nullIfBlank
import exh.util.under
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import eu.kanade.domain.manga.model.Manga as DomainManga

object EXHMigrations {
    private val handler: DatabaseHandler by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getMangaBySource: GetMangaBySource by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val deleteChapters: DeleteChapters by injectLazy()
    private val insertMergedReference: InsertMergedReference by injectLazy()
    private val insertSavedSearch: InsertSavedSearch by injectLazy()
    private val insertFeedSavedSearch: InsertFeedSavedSearch by injectLazy()

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
                    updateSourceId(HBROWSE_SOURCE_ID, 6912)
                    // Migrate BHrowse URLs
                    val hBrowseManga = runBlocking { getMangaBySource.await(HBROWSE_SOURCE_ID) }
                    val mangaUpdates = hBrowseManga.map {
                        MangaUpdate(it.id, url = it.url + "/c00001/")
                    }

                    runBlocking {
                        updateManga.awaitAll(mangaUpdates)
                    }
                }
                if (oldVersion under 5) {
                    // Migrate Hitomi source IDs
                    updateSourceId(Hitomi.otherId, 6910)
                }
                if (oldVersion under 6) {
                    updateSourceId(PERV_EDEN_EN_SOURCE_ID, 6905)
                    updateSourceId(PERV_EDEN_IT_SOURCE_ID, 6906)
                    updateSourceId(NHentai.otherId, 6907)
                }
                if (oldVersion under 7) {
                    val mergedMangas = runBlocking { getMangaBySource.await(MERGED_SOURCE_ID) }

                    if (mergedMangas.isNotEmpty()) {
                        val mangaConfigs = mergedMangas.mapNotNull { mergedManga -> readMangaConfig(mergedManga)?.let { mergedManga to it } }
                        if (mangaConfigs.isNotEmpty()) {
                            val mangaToUpdate = mutableListOf<MangaUpdate>()
                            val mergedMangaReferences = mutableListOf<MergedMangaReference>()
                            mangaConfigs.onEach { mergedManga ->
                                val newFirst = mergedManga.second.children.firstOrNull()?.url?.let {
                                    if (runBlocking { getManga.await(it, MERGED_SOURCE_ID) } != null) return@onEach
                                    mangaToUpdate += MangaUpdate(id = mergedManga.first.id, url = it)
                                    mergedManga.first.copy(url = it)
                                } ?: mergedManga.first
                                mergedMangaReferences += MergedMangaReference(
                                    id = null,
                                    isInfoManga = false,
                                    getChapterUpdates = false,
                                    chapterSortMode = 0,
                                    chapterPriority = 0,
                                    downloadChapters = false,
                                    mergeId = newFirst.id,
                                    mergeUrl = newFirst.url,
                                    mangaId = newFirst.id,
                                    mangaUrl = newFirst.url,
                                    mangaSourceId = MERGED_SOURCE_ID,
                                )
                                mergedManga.second.children.distinct().forEachIndexed { index, mangaSource ->
                                    val load = mangaSource.load() ?: return@forEachIndexed
                                    mergedMangaReferences += MergedMangaReference(
                                        id = null,
                                        isInfoManga = index == 0,
                                        getChapterUpdates = true,
                                        chapterSortMode = 0,
                                        chapterPriority = 0,
                                        downloadChapters = true,
                                        mergeId = newFirst.id,
                                        mergeUrl = newFirst.url,
                                        mangaId = load.manga.id,
                                        mangaUrl = load.manga.url,
                                        mangaSourceId = load.source.id,
                                    )
                                }
                            }
                            runBlocking {
                                updateManga.awaitAll(mangaToUpdate)
                                insertMergedReference.awaitAll(mergedMangaReferences)
                            }

                            val loadedMangaList = mangaConfigs.map { it.second.children }.flatten().mapNotNull { it.load() }.distinct()
                            val chapters = runBlocking { handler.awaitList { ehQueries.getChaptersByMangaIds(mergedMangas.map { it.id }, chapterMapper) } }
                            val mergedMangaChapters = runBlocking { handler.awaitList { ehQueries.getChaptersByMangaIds(loadedMangaList.map { it.manga.id }, chapterMapper) } }

                            val mergedMangaChaptersMatched = mergedMangaChapters.mapNotNull { chapter -> loadedMangaList.firstOrNull { it.manga.id == chapter.id }?.let { it to chapter } }
                            val parsedChapters = chapters.filter { it.read || it.lastPageRead != 0L }.mapNotNull { chapter -> readUrlConfig(chapter.url)?.let { chapter to it } }
                            val chaptersToUpdate = mutableListOf<ChapterUpdate>()
                            parsedChapters.forEach { parsedChapter ->
                                mergedMangaChaptersMatched.firstOrNull { it.second.url == parsedChapter.second.url && it.first.source.id == parsedChapter.second.source && it.first.manga.url == parsedChapter.second.mangaUrl }?.let {
                                    chaptersToUpdate += ChapterUpdate(
                                        it.second.id,
                                        read = parsedChapter.first.read,
                                        lastPageRead = parsedChapter.first.lastPageRead,
                                    )
                                }
                            }
                            runBlocking {
                                deleteChapters.await(mergedMangaChapters.map { it.id })
                                updateChapter.awaitAll(chaptersToUpdate)
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
                    runBlocking {
                        handler.await { ehQueries.deleteBySyncId(6) }
                    }
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
                    try {
                        val oldSortingMode = prefs.getInt(PreferenceKeys.librarySortingMode, 0)
                        val oldSortingDirection = prefs.getBoolean(PreferenceKeys.librarySortingDirection, true)

                        val newSortingMode = when (oldSortingMode) {
                            LibrarySort.ALPHA -> SortModeSetting.ALPHABETICAL
                            LibrarySort.LAST_READ -> SortModeSetting.LAST_READ
                            LibrarySort.LAST_CHECKED -> SortModeSetting.LAST_MANGA_UPDATE
                            LibrarySort.UNREAD -> SortModeSetting.UNREAD_COUNT
                            LibrarySort.TOTAL -> SortModeSetting.TOTAL_CHAPTERS
                            LibrarySort.LATEST_CHAPTER -> SortModeSetting.LATEST_CHAPTER
                            LibrarySort.CHAPTER_FETCH_DATE -> SortModeSetting.CHAPTER_FETCH_DATE
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
                    } catch (e: Exception) {
                        logcat(throwable = e) { "Already done migration" }
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
                        preferences.libraryUpdateMangaRestriction() -= MANGA_NON_COMPLETED
                    }
                }
                if (oldVersion under 24) {
                    try {
                        sequenceOf(
                            "fav-sync",
                            "fav-sync.management",
                            "fav-sync.lock",
                            "fav-sync.note",
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
                if (oldVersion under 27) {
                    val oldSecureScreen = prefs.getBoolean("secure_screen", false)
                    if (oldSecureScreen) {
                        preferences.secureScreen().set(PreferenceValues.SecureScreenMode.ALWAYS)
                    }
                    if (DeviceUtil.isMiui && preferences.extensionInstaller().get() == PreferenceValues.ExtensionInstaller.PACKAGEINSTALLER) {
                        preferences.extensionInstaller().set(PreferenceValues.ExtensionInstaller.LEGACY)
                    }
                }
                if (oldVersion under 28) {
                    if (prefs.getString("pref_display_mode_library", null) == "NO_TITLE_GRID") {
                        preferences.libraryDisplayMode().set(DisplayModeSetting.COVER_ONLY_GRID)
                    }
                }
                if (oldVersion under 29) {
                    if (prefs.getString("pref_display_mode_catalogue", null) == "NO_TITLE_GRID") {
                        preferences.sourceDisplayMode().set(DisplayModeSetting.COMPACT_GRID)
                    }
                }
                if (oldVersion under 30) {
                    BackupCreatorJob.setupTask(context)
                }
                if (oldVersion under 31) {
                    runBlocking {
                        val savedSearch = prefs.getStringSet("eh_saved_searches", emptySet())?.mapNotNull {
                            runCatching {
                                val content = Json.decodeFromString<JsonObject>(it.substringAfter(':'))
                                SavedSearch(
                                    id = -1,
                                    source = it.substringBefore(':').toLongOrNull()
                                        ?: return@runCatching null,
                                    name = content["name"]!!.jsonPrimitive.content,
                                    query = content["query"]!!.jsonPrimitive.contentOrNull?.nullIfBlank(),
                                    filtersJson = Json.encodeToString(content["filters"]!!.jsonArray),
                                )
                            }.getOrNull()
                        }
                        if (!savedSearch.isNullOrEmpty()) {
                            insertSavedSearch.awaitAll(savedSearch)
                        }
                        val feedSavedSearch = prefs.getStringSet("latest_tab_sources", emptySet())?.map {
                            FeedSavedSearch(
                                id = -1,
                                source = it.toLong(),
                                savedSearch = null,
                                global = true,
                            )
                        }
                        if (!feedSavedSearch.isNullOrEmpty()) {
                            insertFeedSavedSearch.awaitAll(feedSavedSearch)
                        }
                    }
                    prefs.edit(commit = true) {
                        remove("eh_saved_searches")
                        remove("latest_tab_sources")
                    }
                }
                if (oldVersion under 32) {
                    val oldReaderTap = prefs.getBoolean("reader_tap", false)
                    if (!oldReaderTap) {
                        preferences.navigationModePager().set(5)
                        preferences.navigationModeWebtoon().set(5)
                    }
                }
                if (oldVersion under 36) {
                    // Handle renamed enum values
                    @Suppress("DEPRECATION")
                    val newSortingMode = when (val oldSortingMode = preferences.librarySortingMode().get()) {
                        SortModeSetting.LAST_CHECKED -> SortModeSetting.LAST_MANGA_UPDATE
                        SortModeSetting.UNREAD -> SortModeSetting.UNREAD_COUNT
                        SortModeSetting.DATE_FETCHED -> SortModeSetting.CHAPTER_FETCH_DATE
                        SortModeSetting.DRAG_AND_DROP -> SortModeSetting.ALPHABETICAL
                        else -> oldSortingMode
                    }
                    preferences.librarySortingMode().set(newSortingMode)
                    runBlocking {
                        handler.await(true) {
                            categoriesQueries.getCategories(categoryMapper).executeAsList()
                                .filter { SortModeSetting.fromFlag(it.flags) == SortModeSetting.DRAG_AND_DROP }
                                .forEach {
                                    categoriesQueries.update(
                                        categoryId = it.id,
                                        flags = it.flags xor SortModeSetting.DRAG_AND_DROP.flag,
                                        name = null,
                                        order = null,
                                    )
                                }
                        }
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
        val mangaUrl: String,
    )

    @Serializable
    private data class MangaConfig(
        @SerialName("c")
        val children: List<MangaSource>,
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

    private fun readMangaConfig(manga: DomainManga): MangaConfig? {
        return MangaConfig.readFromUrl(manga.url)
    }

    @Serializable
    private data class MangaSource(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
    ) {
        fun load(): LoadedMangaSource? {
            val manga = runBlocking { getManga.await(url, source) } ?: return null
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

    private data class LoadedMangaSource(val source: Source, val manga: DomainManga)

    private fun updateSourceId(newId: Long, oldId: Long) {
        runBlocking {
            handler.await { ehQueries.migrateSource(newId, oldId) }
        }
    }
}
