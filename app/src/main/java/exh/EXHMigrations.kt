package exh

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.workManager
import exh.eh.EHentaiUpdateWorker
import exh.log.xLogE
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.util.nullIfBlank
import exh.util.under
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.TriState
import tachiyomi.core.preference.getAndSet
import tachiyomi.core.preference.getEnum
import tachiyomi.core.preference.minusAssign
import tachiyomi.core.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.category.CategoryMapper
import tachiyomi.data.chapter.ChapterMapper
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.chapter.interactor.DeleteChapters
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaBySource
import tachiyomi.domain.manga.interactor.InsertMergedReference
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URI
import java.net.URISyntaxException

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
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(
        context: Context,
        preferenceStore: PreferenceStore,
        basePreferences: BasePreferences,
        uiPreferences: UiPreferences,
        networkPreferences: NetworkPreferences,
        sourcePreferences: SourcePreferences,
        securityPreferences: SecurityPreferences,
        libraryPreferences: LibraryPreferences,
        readerPreferences: ReaderPreferences,
        backupPreferences: BackupPreferences,
        trackerManager: TrackerManager,
        pagePreviewCache: PagePreviewCache,
    ): Boolean {
        val lastVersionCode = preferenceStore.getInt("eh_last_version_code", 0)
        val oldVersion = lastVersionCode.get()
        try {
            if (oldVersion < BuildConfig.VERSION_CODE) {
                lastVersionCode.set(BuildConfig.VERSION_CODE)

                LibraryUpdateJob.setupTask(context)
                BackupCreateJob.setupTask(context)
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
                if (oldVersion under 6) {
                    updateSourceId(NHentai.otherId, 6907)
                }
                if (oldVersion under 7) {
                    val mergedMangas = runBlocking { getMangaBySource.await(MERGED_SOURCE_ID) }

                    if (mergedMangas.isNotEmpty()) {
                        val mangaConfigs = mergedMangas.mapNotNull { mergedManga ->
                            readMangaConfig(mergedManga)?.let { mergedManga to it }
                        }
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
                                    id = -1,
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
                                        id = -1,
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

                            val loadedMangaList = mangaConfigs
                                .map { it.second.children }
                                .flatten()
                                .mapNotNull { it.load() }
                                .distinct()
                            val chapters =
                                runBlocking {
                                    handler.awaitList {
                                        ehQueries.getChaptersByMangaIds(
                                            mergedMangas.map { it.id },
                                            ChapterMapper::mapChapter,
                                        )
                                    }
                                }
                            val mergedMangaChapters =
                                runBlocking {
                                    handler.awaitList {
                                        ehQueries.getChaptersByMangaIds(
                                            loadedMangaList.map { it.manga.id },
                                            ChapterMapper::mapChapter,
                                        )
                                    }
                                }

                            val mergedMangaChaptersMatched = mergedMangaChapters.mapNotNull { chapter ->
                                loadedMangaList.firstOrNull {
                                    it.manga.id == chapter.id
                                }?.let { it to chapter }
                            }
                            val parsedChapters = chapters.filter {
                                it.read || it.lastPageRead != 0L
                            }.mapNotNull { chapter -> readUrlConfig(chapter.url)?.let { chapter to it } }
                            val chaptersToUpdate = mutableListOf<ChapterUpdate>()
                            parsedChapters.forEach { parsedChapter ->
                                mergedMangaChaptersMatched.firstOrNull {
                                    it.second.url == parsedChapter.second.url &&
                                        it.first.source.id == parsedChapter.second.source &&
                                        it.first.manga.url == parsedChapter.second.mangaUrl
                                }?.let {
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
                    trackerManager.myAnimeList.logout()
                }
                if (oldVersion under 14) {
                    // Migrate DNS over HTTPS setting
                    val wasDohEnabled = prefs.getBoolean("enable_doh", false)
                    if (wasDohEnabled) {
                        prefs.edit {
                            putInt(networkPreferences.dohProvider().key(), PREF_DOH_CLOUDFLARE)
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
                        1 -> ReaderOrientation.FREE.flagValue
                        2 -> ReaderOrientation.PORTRAIT.flagValue
                        3 -> ReaderOrientation.LANDSCAPE.flagValue
                        4 -> ReaderOrientation.LOCKED_PORTRAIT.flagValue
                        5 -> ReaderOrientation.LOCKED_LANDSCAPE.flagValue
                        else -> ReaderOrientation.FREE.flagValue
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
                    val readerTheme = readerPreferences.readerTheme().get()
                    if (readerTheme == 4) {
                        readerPreferences.readerTheme().set(3)
                    }
                    val updateInterval = libraryPreferences.autoUpdateInterval().get()
                    if (updateInterval == 1 || updateInterval == 2) {
                        libraryPreferences.autoUpdateInterval().set(3)
                    }
                }
                if (oldVersion under 20) {
                    try {
                        val oldSortingMode = prefs.getInt(libraryPreferences.sortingMode().key(), 0 /* ALPHABETICAL */)
                        val oldSortingDirection = prefs.getBoolean("library_sorting_ascending", true)

                        val newSortingMode = when (oldSortingMode) {
                            0 -> "ALPHABETICAL"
                            1 -> "LAST_READ"
                            2 -> "LAST_MANGA_UPDATE"
                            3 -> "UNREAD_COUNT"
                            4 -> "TOTAL_CHAPTERS"
                            6 -> "LATEST_CHAPTER"
                            7 -> "DRAG_AND_DROP"
                            8 -> "DATE_ADDED"
                            9 -> "TAG_LIST"
                            10 -> "CHAPTER_FETCH_DATE"
                            else -> "ALPHABETICAL"
                        }

                        val newSortingDirection = when (oldSortingDirection) {
                            true -> "ASCENDING"
                            else -> "DESCENDING"
                        }

                        prefs.edit(commit = true) {
                            remove(libraryPreferences.sortingMode().key())
                            remove("library_sorting_ascending")
                        }

                        prefs.edit {
                            putString(libraryPreferences.sortingMode().key(), newSortingMode)
                            putString("library_sorting_ascending", newSortingDirection)
                        }
                    } catch (e: Exception) {
                        logcat(throwable = e) { "Already done migration" }
                    }
                }
                if (oldVersion under 22) {
                    // Handle removed every 3, 4, 6, and 8 hour library updates
                    val updateInterval = libraryPreferences.autoUpdateInterval().get()
                    if (updateInterval in listOf(3, 4, 6, 8)) {
                        libraryPreferences.autoUpdateInterval().set(12)
                    }
                }
                if (oldVersion under 23) {
                    val oldUpdateOngoingOnly = prefs.getBoolean("pref_update_only_non_completed_key", true)
                    if (!oldUpdateOngoingOnly) {
                        libraryPreferences.autoUpdateMangaRestrictions() -= MANGA_NON_COMPLETED
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
                        securityPreferences.secureScreen().set(SecurityPreferences.SecureScreenMode.ALWAYS)
                    }
                    if (
                        DeviceUtil.isMiui &&
                        basePreferences.extensionInstaller().get() == BasePreferences.ExtensionInstaller
                            .PACKAGEINSTALLER
                    ) {
                        basePreferences.extensionInstaller().set(BasePreferences.ExtensionInstaller.LEGACY)
                    }
                }
                if (oldVersion under 28) {
                    if (prefs.getString("pref_display_mode_library", null) == "NO_TITLE_GRID") {
                        prefs.edit(commit = true) {
                            putString("pref_display_mode_library", "COVER_ONLY_GRID")
                        }
                    }
                }
                if (oldVersion under 29) {
                    if (prefs.getString("pref_display_mode_catalogue", null) == "NO_TITLE_GRID") {
                        prefs.edit(commit = true) {
                            putString("pref_display_mode_catalogue", "COMPACT_GRID")
                        }
                    }
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
                        readerPreferences.navigationModePager().set(5)
                        readerPreferences.navigationModeWebtoon().set(5)
                    }
                }
                if (oldVersion under 38) {
                    // Handle renamed enum values
                    val newSortingMode = when (
                        val oldSortingMode = prefs.getString(libraryPreferences.sortingMode().key(), "ALPHABETICAL")
                    ) {
                        "LAST_CHECKED" -> "LAST_MANGA_UPDATE"
                        "UNREAD" -> "UNREAD_COUNT"
                        "DATE_FETCHED" -> "CHAPTER_FETCH_DATE"
                        "DRAG_AND_DROP" -> "ALPHABETICAL"
                        else -> oldSortingMode
                    }
                    prefs.edit {
                        putString(libraryPreferences.sortingMode().key(), newSortingMode)
                    }
                    runBlocking {
                        handler.await(true) {
                            categoriesQueries.getCategories(CategoryMapper::mapCategory).executeAsList()
                                .filter { (it.flags and 0b00111100L) == 0b00100000L }
                                .forEach {
                                    categoriesQueries.update(
                                        categoryId = it.id,
                                        flags = it.flags and 0b00111100L.inv(),
                                        name = null,
                                        order = null,
                                    )
                                }
                        }
                    }
                }
                if (oldVersion under 39) {
                    prefs.edit {
                        val sort = prefs.getString(libraryPreferences.sortingMode().key(), null) ?: return@edit
                        val direction = prefs.getString("library_sorting_ascending", "ASCENDING")!!
                        putString(libraryPreferences.sortingMode().key(), "$sort,$direction")
                        remove("library_sorting_ascending")
                    }
                }
                if (oldVersion under 40) {
                    if (backupPreferences.backupInterval().get() == 0) {
                        backupPreferences.backupInterval().set(12)
                    }
                }
                if (oldVersion under 41) {
                    val preferences = listOf(
                        libraryPreferences.filterChapterByRead(),
                        libraryPreferences.filterChapterByDownloaded(),
                        libraryPreferences.filterChapterByBookmarked(),
                        libraryPreferences.sortChapterBySourceOrNumber(),
                        libraryPreferences.displayChapterByNameOrNumber(),
                        libraryPreferences.sortChapterByAscendingOrDescending(),
                    )

                    prefs.edit {
                        preferences.forEach { preference ->
                            val key = preference.key()
                            val value = prefs.getInt(key, Int.MIN_VALUE)
                            if (value == Int.MIN_VALUE) return@forEach
                            remove(key)
                            putLong(key, value.toLong())
                        }
                    }
                }
                if (oldVersion under 42) {
                    if (uiPreferences.themeMode().isSet()) {
                        prefs.edit {
                            val themeMode = prefs.getString(uiPreferences.themeMode().key(), null) ?: return@edit
                            putString(uiPreferences.themeMode().key(), themeMode.uppercase())
                        }
                    }
                }
                if (oldVersion under 43) {
                    if (preferenceStore.getBoolean("start_reading_button").get()) {
                        libraryPreferences.showContinueReadingButton().set(true)
                    }
                }
                if (oldVersion under 44) {
                    val trackingQueuePref = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)
                    trackingQueuePref.all.forEach {
                        val (_, lastChapterRead) = it.value.toString().split(":")
                        trackingQueuePref.edit {
                            remove(it.key)
                            putFloat(it.key, lastChapterRead.toFloat())
                        }
                    }
                }
                if (oldVersion under 45) {
                    // Force MangaDex log out due to login flow change
                    trackerManager.mdList.logout()
                }
                if (oldVersion under 52) {
                    // Removed background jobs
                    context.workManager.cancelAllWorkByTag("UpdateChecker")
                    context.workManager.cancelAllWorkByTag("ExtensionUpdate")
                    prefs.edit {
                        remove("automatic_ext_updates")
                    }
                    val prefKeys = listOf(
                        "pref_filter_library_downloaded",
                        "pref_filter_library_unread",
                        "pref_filter_library_started",
                        "pref_filter_library_bookmarked",
                        "pref_filter_library_completed",
                        "pref_filter_library_lewd",
                    ) + trackerManager.trackers.map { "pref_filter_library_tracked_${it.id}" }

                    prefKeys.forEach { key ->
                        val pref = preferenceStore.getInt(key, 0)
                        prefs.edit {
                            remove(key)

                            val newValue = when (pref.get()) {
                                1 -> TriState.ENABLED_IS
                                2 -> TriState.ENABLED_NOT
                                else -> TriState.DISABLED
                            }

                            preferenceStore.getEnum("${key}_v2", TriState.DISABLED).set(newValue)
                        }
                    }
                }
                // if (oldVersion under 53) {
                //     // This was accidentally visible from the reader settings sheet, but should always
                //     // be disabled in release builds.
                //     if (isReleaseBuildType) {
                //         readerPreferences.longStripSplitWebtoon().set(false)
                //     }
                // }
                if (oldVersion under 56) {
                    val pref = libraryPreferences.autoUpdateDeviceRestrictions()
                    if (pref.isSet() && "battery_not_low" in pref.get()) {
                        pref.getAndSet { it - "battery_not_low" }
                    }
                }
                if (oldVersion under 57) {
                    val pref = preferenceStore.getInt("relative_time", 7)
                    if (pref.get() == 0) {
                        uiPreferences.relativeTime().set(false)
                    }
                }
                if (oldVersion under 58) {
                    pagePreviewCache.clear()
                    File(context.cacheDir, PagePreviewCache.PARAMETER_CACHE_DIRECTORY).listFiles()?.forEach {
                        if (it.name == "journal" || it.name.startsWith("journal.")) {
                            return@forEach
                        }

                        try {
                            it.delete()
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Failed to remove file from cache" }
                        }
                    }
                }
                if (oldVersion under 59) {
                    val prefsToReplace = listOf(
                        "pref_download_only",
                        "incognito_mode",
                        "last_catalogue_source",
                        "trusted_signatures",
                        "last_app_closed",
                        "library_update_last_timestamp",
                        "library_unseen_updates_count",
                        "last_used_category",
                        "last_app_check",
                        "last_ext_check",
                        "last_version_code",
                        "skip_pre_migration",
                        "eh_auto_update_stats",
                        "storage_dir",
                    )
                    replacePreferences(
                        preferenceStore = preferenceStore,
                        filterPredicate = { it.key in prefsToReplace },
                        newKey = { Preference.appStateKey(it) },
                    )

                    val privatePrefsToReplace = listOf(
                        "sql_password",
                        "encrypt_database",
                        "cbz_password",
                        "password_protect_downloads",
                        "eh_ipb_member_id",
                        "enable_exhentai",
                        "eh_ipb_member_id",
                        "eh_ipb_pass_hash",
                        "eh_igneous",
                        "eh_ehSettingsProfile",
                        "eh_exhSettingsProfile",
                        "eh_settingsKey",
                        "eh_sessionCookie",
                        "eh_hathPerksCookie",
                    )

                    replacePreferences(
                        preferenceStore = preferenceStore,
                        filterPredicate = { it.key in privatePrefsToReplace },
                        newKey = { Preference.privateKey(it) },
                    )

                    // Deleting old download cache index files, but might as well clear it all out
                    context.cacheDir.deleteRecursively()
                }
                if (oldVersion under 60) {
                    sourcePreferences.extensionRepos().getAndSet {
                        it.map { "https://raw.githubusercontent.com/$it/repo" }.toSet()
                    }
                    replacePreferences(
                        preferenceStore = preferenceStore,
                        filterPredicate = { it.key.startsWith("pref_mangasync_") || it.key.startsWith("track_token_") },
                        newKey = { Preference.privateKey(it) },
                    )
                    prefs.edit {
                        remove(Preference.appStateKey("trusted_signatures"))
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

    fun migrateBackupEntry(manga: Manga): Manga {
        var newManga = manga
        if (newManga.source == 6907L) {
            newManga = newManga.copy(
                // Migrate the old source to the delegated one
                source = NHentai.otherId,
                // Migrate nhentai URLs
                url = getUrlWithoutDomain(newManga.url),
            )
        }

        // Migrate Tsumino source IDs
        if (newManga.source == 6909L) {
            newManga = newManga.copy(
                source = TSUMINO_SOURCE_ID,
            )
        }

        if (newManga.source == 6912L) {
            newManga = newManga.copy(
                source = HBROWSE_SOURCE_ID,
                url = newManga.url + "/c00001/",
            )
        }

        // Allow importing of EHentai extension backups
        if (newManga.source in BlacklistedSources.EHENTAI_EXT_SOURCES) {
            newManga = newManga.copy(
                source = EH_SOURCE_ID,
            )
        }

        return newManga
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

    private fun readMangaConfig(manga: Manga): MangaConfig? {
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

    private data class LoadedMangaSource(val source: Source, val manga: Manga)

    private fun updateSourceId(newId: Long, oldId: Long) {
        runBlocking {
            handler.await { ehQueries.migrateSource(newId, oldId) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun replacePreferences(
        preferenceStore: PreferenceStore,
        filterPredicate: (Map.Entry<String, Any?>) -> Boolean,
        newKey: (String) -> String,
    ) {
        preferenceStore.getAll()
            .filter(filterPredicate)
            .forEach { (key, value) ->
                when (value) {
                    is Int -> {
                        preferenceStore.getInt(newKey(key)).set(value)
                        preferenceStore.getInt(key).delete()
                    }
                    is Long -> {
                        preferenceStore.getLong(newKey(key)).set(value)
                        preferenceStore.getLong(key).delete()
                    }
                    is Float -> {
                        preferenceStore.getFloat(newKey(key)).set(value)
                        preferenceStore.getFloat(key).delete()
                    }
                    is String -> {
                        preferenceStore.getString(newKey(key)).set(value)
                        preferenceStore.getString(key).delete()
                    }
                    is Boolean -> {
                        preferenceStore.getBoolean(newKey(key)).set(value)
                        preferenceStore.getBoolean(key).delete()
                    }
                    is Set<*> -> (value as? Set<String>)?.let {
                        preferenceStore.getStringSet(newKey(key)).set(value)
                        preferenceStore.getStringSet(key).delete()
                    }
                }
            }
    }
}
