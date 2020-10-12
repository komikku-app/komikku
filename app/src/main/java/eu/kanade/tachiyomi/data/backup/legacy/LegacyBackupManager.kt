package eu.kanade.tachiyomi.data.backup.legacy

import android.content.Context
import android.net.Uri
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.registerTypeHierarchyAdapter
import com.github.salomonbrys.kotson.set
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CATEGORY
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CATEGORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CHAPTER
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CHAPTER_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_HISTORY
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_HISTORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_TRACK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_TRACK_MASK
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.CATEGORIES
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.CHAPTERS
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.CURRENT_VERSION
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.EXTENSIONS
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.HISTORY
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.MANGA
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.MERGEDMANGAREFERENCES
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.SAVEDSEARCHES
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup.TRACK
import eu.kanade.tachiyomi.data.backup.legacy.models.DHistory
import eu.kanade.tachiyomi.data.backup.models.AbstractBackupManager
import eu.kanade.tachiyomi.data.backup.legacy.serializer.CategoryTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.ChapterTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.HistoryTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.MangaTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.MergedMangaReferenceTypeAdapter
import eu.kanade.tachiyomi.data.backup.legacy.serializer.TrackTypeAdapter
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import exh.MERGED_SOURCE_ID
import exh.eh.EHentaiThrottleManager
import exh.merged.sql.models.MergedMangaReference
import exh.savedsearches.JsonSavedSearch
import exh.util.asObservable
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rx.Observable
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.lang.RuntimeException
import kotlin.math.max

class LegacyBackupManager(val context: Context, version: Int = CURRENT_VERSION) : AbstractBackupManager() {

    internal val databaseHelper: DatabaseHelper by injectLazy()
    internal val sourceManager: SourceManager by injectLazy()
    internal val trackManager: TrackManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Version of parser
     */
    var version: Int = version
        private set

    /**
     * Json Parser
     */
    var parser: Gson = initParser()

    /**
     * Set version of parser
     *
     * @param version version of parser
     */
    internal fun setVersion(version: Int) {
        this.version = version
        parser = initParser()
    }

    private fun initParser(): Gson = when (version) {
        2 ->
            GsonBuilder()
                .registerTypeAdapter<MangaImpl>(MangaTypeAdapter.build())
                .registerTypeHierarchyAdapter<ChapterImpl>(ChapterTypeAdapter.build())
                .registerTypeAdapter<CategoryImpl>(CategoryTypeAdapter.build())
                .registerTypeAdapter<DHistory>(HistoryTypeAdapter.build())
                .registerTypeHierarchyAdapter<TrackImpl>(TrackTypeAdapter.build())
                // SY -->
                .registerTypeAdapter<MergedMangaReference>(MergedMangaReferenceTypeAdapter.build())
                // SY <--
                .create()
        else -> throw Exception("Unknown backup version")
    }

    /**
     * Create backup Json file from database
     *
     * @param uri path of Uri
     * @param isJob backup called from job
     */
    override fun createBackup(uri: Uri, flags: Int, isJob: Boolean): String? {
        // Create root object
        val root = JsonObject()

        // Create manga array
        val mangaEntries = JsonArray()

        // Create category array
        val categoryEntries = JsonArray()

        // Create extension ID/name mapping
        val extensionEntries = JsonArray()

        // Merged Manga References
        val mergedMangaReferenceEntries = JsonArray()

        // Add value's to root
        root[Backup.VERSION] = CURRENT_VERSION
        root[Backup.MANGAS] = mangaEntries
        root[CATEGORIES] = categoryEntries
        root[EXTENSIONS] = extensionEntries
        // SY -->
        root[MERGEDMANGAREFERENCES] = mergedMangaReferenceEntries
        // SY <--

        databaseHelper.inTransaction {
            // Get manga from database
            val mangas = getFavoriteManga()/* SY --> */.filterNot { it.source == MERGED_SOURCE_ID } + getMergedManga().filterNot { it.source == MERGED_SOURCE_ID } /* SY <-- */

            val extensions: MutableSet<String> = mutableSetOf()

            // Backup library manga and its dependencies
            mangas.forEach { manga ->
                mangaEntries.add(backupMangaObject(manga, flags))

                // Maintain set of extensions/sources used (excludes local source)
                if (manga.source != LocalSource.ID) {
                    sourceManager.get(manga.source)?.let {
                        extensions.add("${manga.source}:${it.name}")
                    }
                }
            }

            // Backup categories
            if ((flags and BACKUP_CATEGORY_MASK) == BACKUP_CATEGORY) {
                backupCategories(categoryEntries)
            }

            // Backup extension ID/name mapping
            backupExtensionInfo(extensionEntries, extensions)
            // SY -->
            root[SAVEDSEARCHES] =
                Injekt.get<PreferencesHelper>().eh_savedSearches().get().joinToString(separator = "***")

            backupMergedMangaReferences(mergedMangaReferenceEntries)
            // SY <--
        }

        try {
            // When BackupCreatorJob
            if (isJob) {
                // Get dir of file and create
                var dir = UniFile.fromUri(context, uri)
                dir = dir.createDirectory("automatic")

                // Delete older backups
                val numberOfBackups = numberOfBackups()
                val backupRegex = Regex("""tachiyomi_\d+-\d+-\d+_\d+-\d+.json""")
                dir.listFiles { _, filename -> backupRegex.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(numberOfBackups - 1)
                    .forEach { it.delete() }

                // Create new file to place backup
                val newFile = dir.createFile(Backup.getDefaultFilename())
                    ?: throw Exception("Couldn't create backup file")

                newFile.openOutputStream().bufferedWriter().use {
                    parser.toJson(root, it)
                }

                return newFile.uri.toString()
            } else {
                val file = UniFile.fromUri(context, uri)
                    ?: throw Exception("Couldn't create backup file")
                file.openOutputStream().bufferedWriter().use {
                    parser.toJson(root, it)
                }

                return file.uri.toString()
            }
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
    }

    private fun backupExtensionInfo(root: JsonArray, extensions: Set<String>) {
        extensions.sorted().forEach {
            root.add(it)
        }
    }

    // SY -->
    private fun backupMergedMangaReferences(root: JsonArray) {
        val mergedMangaReferences = databaseHelper.getMergedMangaReferences().executeAsBlocking()
        mergedMangaReferences.forEach { root.add(parser.toJsonTree(it)) }
    }
    // SY <--

    /**
     * Backup the categories of library
     *
     * @param root root of categories json
     */
    internal fun backupCategories(root: JsonArray) {
        val categories = databaseHelper.getCategories().executeAsBlocking()
        categories.forEach { root.add(parser.toJsonTree(it)) }
    }

    /**
     * Convert a manga to Json
     *
     * @param manga manga that gets converted
     * @return [JsonElement] containing manga information
     */
    internal fun backupMangaObject(manga: Manga, options: Int): JsonElement {
        // Entry for this manga
        val entry = JsonObject()

        // Backup manga fields
        entry[MANGA] = parser.toJsonTree(manga)

        // Check if user wants chapter information in backup
        if (options and BACKUP_CHAPTER_MASK == BACKUP_CHAPTER) {
            // Backup all the chapters
            val chapters = databaseHelper.getChapters(manga).executeAsBlocking()
            if (chapters.isNotEmpty()) {
                val chaptersJson = parser.toJsonTree(chapters)
                if (chaptersJson.asJsonArray.size() > 0) {
                    entry[CHAPTERS] = chaptersJson
                }
            }
        }

        // Check if user wants category information in backup
        if (options and BACKUP_CATEGORY_MASK == BACKUP_CATEGORY) {
            // Backup categories for this manga
            val categoriesForManga = databaseHelper.getCategoriesForManga(manga).executeAsBlocking()
            if (categoriesForManga.isNotEmpty()) {
                val categoriesNames = categoriesForManga.map { it.name }
                entry[CATEGORIES] = parser.toJsonTree(categoriesNames)
            }
        }

        // Check if user wants track information in backup
        if (options and BACKUP_TRACK_MASK == BACKUP_TRACK) {
            val tracks = databaseHelper.getTracks(manga).executeAsBlocking()
            if (tracks.isNotEmpty()) {
                entry[TRACK] = parser.toJsonTree(tracks)
            }
        }

        // Check if user wants history information in backup
        if (options and BACKUP_HISTORY_MASK == BACKUP_HISTORY) {
            val historyForManga = databaseHelper.getHistoryByMangaId(manga.id!!).executeAsBlocking()
            if (historyForManga.isNotEmpty()) {
                val historyData = historyForManga.mapNotNull { history ->
                    val url = databaseHelper.getChapter(history.chapter_id).executeAsBlocking()?.url
                    url?.let { DHistory(url, history.last_read) }
                }
                val historyJson = parser.toJsonTree(historyData)
                if (historyJson.asJsonArray.size() > 0) {
                    entry[HISTORY] = historyJson
                }
            }
        }

        return entry
    }

    fun restoreMangaNoFetch(manga: Manga, dbManga: Manga) {
        manga.id = dbManga.id
        manga.copyFrom(dbManga)
        manga.favorite = true
        insertManga(manga)
    }

    /**
     * [Observable] that fetches manga information
     *
     * @param source source of manga
     * @param manga manga that needs updating
     * @return [Observable] that contains manga
     */
    fun restoreMangaFetchObservable(source: Source, manga: Manga): Observable<Manga> {
        return source.fetchMangaDetails(manga)
            .map { networkManga ->
                manga.copyFrom(networkManga)
                manga.favorite = true
                manga.initialized = true
                manga.id = insertManga(manga)
                manga
            }
    }

    /**
     * [Observable] that fetches chapter information
     *
     * @param source source of manga
     * @param manga manga that needs updating
     * @return [Observable] that contains manga
     */
    fun restoreChapterFetchObservable(source: Source, manga: Manga, chapters: List<Chapter>, throttleManager: EHentaiThrottleManager): Observable<Pair<List<Chapter>, List<Chapter>>> {
        // SY -->
        if (source is MergedSource) {
            val syncedChapters = runBlocking { source.fetchChaptersAndSync(manga, false) }
            return syncedChapters.onEach { pair ->
                if (pair.first.isNotEmpty()) {
                    chapters.forEach { it.manga_id = manga.id }
                    insertChapters(chapters)
                }
            }.asObservable()
        } else {
            return (
                if (source is EHentai) {
                    source.fetchChapterList(manga, throttleManager::throttle)
                } else {
                    source.fetchChapterList(manga)
                }
                ).map {
                if (it.last().chapter_number == -99F) {
                    chapters.forEach { chapter ->
                        chapter.name =
                            "Chapter ${chapter.chapter_number} restored by dummy source"
                    }
                    syncChaptersWithSource(databaseHelper, chapters, manga, source)
                } else {
                    syncChaptersWithSource(databaseHelper, it, manga, source)
                }
            }
                // SY <--
                .doOnNext { pair ->
                    if (pair.first.isNotEmpty()) {
                        chapters.forEach { it.manga_id = manga.id }
                        insertChapters(chapters)
                    }
                }
        }
    }

    /**
     * Restore the categories from Json
     *
     * @param jsonCategories array containing categories
     */
    internal fun restoreCategories(jsonCategories: JsonArray) {
        // Get categories from file and from db
        val dbCategories = databaseHelper.getCategories().executeAsBlocking()
        val backupCategories = parser.fromJson<List<CategoryImpl>>(jsonCategories)

        // Iterate over them
        backupCategories.forEach { category ->
            // Used to know if the category is already in the db
            var found = false
            for (dbCategory in dbCategories) {
                // If the category is already in the db, assign the id to the file's category
                // and do nothing
                if (category.name == dbCategory.name) {
                    category.id = dbCategory.id
                    found = true
                    break
                }
            }
            // If the category isn't in the db, remove the id and insert a new category
            // Store the inserted id in the category
            if (!found) {
                // Let the db assign the id
                category.id = null
                val result = databaseHelper.insertCategory(category).executeAsBlocking()
                category.id = result.insertedId()?.toInt()
            }
        }
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    internal fun restoreCategoriesForManga(manga: Manga, categories: List<String>) {
        val dbCategories = databaseHelper.getCategories().executeAsBlocking()
        val mangaCategoriesToUpdate = mutableListOf<MangaCategory>()
        for (backupCategoryStr in categories) {
            for (dbCategory in dbCategories) {
                if (backupCategoryStr == dbCategory.name) {
                    mangaCategoriesToUpdate.add(MangaCategory.create(manga, dbCategory))
                    break
                }
            }
        }

        // Update database
        if (mangaCategoriesToUpdate.isNotEmpty()) {
            databaseHelper.deleteOldMangasCategories(listOf(manga)).executeAsBlocking()
            databaseHelper.insertMangasCategories(mangaCategoriesToUpdate).executeAsBlocking()
        }
    }

    /**
     * Restore history from Json
     *
     * @param history list containing history to be restored
     */
    internal fun restoreHistoryForManga(history: List<DHistory>) {
        // List containing history to be updated
        val historyToBeUpdated = mutableListOf<History>()
        for ((url, lastRead) in history) {
            val dbHistory = databaseHelper.getHistoryByChapterUrl(url).executeAsBlocking()
            // Check if history already in database and update
            if (dbHistory != null) {
                dbHistory.apply {
                    last_read = max(lastRead, dbHistory.last_read)
                }
                historyToBeUpdated.add(dbHistory)
            } else {
                // If not in database create
                databaseHelper.getChapter(url).executeAsBlocking()?.let {
                    val historyToAdd = History.create(it).apply {
                        last_read = lastRead
                    }
                    historyToBeUpdated.add(historyToAdd)
                }
            }
        }
        databaseHelper.updateHistoryLastRead(historyToBeUpdated).executeAsBlocking()
    }

    /**
     * Restores the sync of a manga.
     *
     * @param manga the manga whose sync have to be restored.
     * @param tracks the track list to restore.
     */
    internal fun restoreTrackForManga(manga: Manga, tracks: List<Track>) {
        // Fix foreign keys with the current manga id
        tracks.map { it.manga_id = manga.id!! }

        // Get tracks from database
        val dbTracks = databaseHelper.getTracks(manga).executeAsBlocking()
        val trackToUpdate = mutableListOf<Track>()

        tracks.forEach { track ->
            val service = trackManager.getService(track.sync_id)
            if (service != null && service.isLogged) {
                var isInDatabase = false
                for (dbTrack in dbTracks) {
                    if (track.sync_id == dbTrack.sync_id) {
                        // The sync is already in the db, only update its fields
                        if (track.media_id != dbTrack.media_id) {
                            dbTrack.media_id = track.media_id
                        }
                        if (track.library_id != dbTrack.library_id) {
                            dbTrack.library_id = track.library_id
                        }
                        dbTrack.last_chapter_read = max(dbTrack.last_chapter_read, track.last_chapter_read)
                        isInDatabase = true
                        trackToUpdate.add(dbTrack)
                        break
                    }
                }
                if (!isInDatabase) {
                    // Insert new sync. Let the db assign the id
                    track.id = null
                    trackToUpdate.add(track)
                }
            }
        }
        // Update database
        if (trackToUpdate.isNotEmpty()) {
            databaseHelper.insertTracks(trackToUpdate).executeAsBlocking()
        }
    }

    /**
     * Restore the chapters for manga if chapters already in database
     *
     * @param manga manga of chapters
     * @param chapters list containing chapters that get restored
     * @return boolean answering if chapter fetch is not needed
     */
    internal fun restoreChaptersForManga(manga: Manga, chapters: List<Chapter>): Boolean {
        val dbChapters = databaseHelper.getChapters(manga).executeAsBlocking()

        // Return if fetch is needed
        if (dbChapters.isEmpty() || dbChapters.size < chapters.size) {
            return false
        }

        for (chapter in chapters) {
            val pos = dbChapters.indexOf(chapter)
            if (pos != -1) {
                val dbChapter = dbChapters[pos]
                chapter.id = dbChapter.id
                chapter.copyFrom(dbChapter)
                break
            }
        }
        // Filter the chapters that couldn't be found.
        chapters.filter { it.id != null }
        chapters.map { it.manga_id = manga.id }

        insertChapters(chapters)
        return true
    }

    // SY -->
    internal fun restoreSavedSearches(jsonSavedSearches: JsonElement) {
        val backupSavedSearches = jsonSavedSearches.asString.split("***").toSet()

        val newSavedSearches = backupSavedSearches.mapNotNull {
            try {
                val id = it.substringBefore(':').toLong()
                val content = Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
                id to content
            } catch (t: RuntimeException) {
                // Load failed
                Timber.e(t, "Failed to load saved search!")
                t.printStackTrace()
                null
            }
        }.toMutableList()

        val currentSources = newSavedSearches.map { it.first }.toSet()

        newSavedSearches += preferences.eh_savedSearches().get().mapNotNull {
            try {
                val id = it.substringBefore(':').toLong()
                val content = Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
                id to content
            } catch (t: RuntimeException) {
                // Load failed
                Timber.e(t, "Failed to load saved search!")
                t.printStackTrace()
                null
            }
        }.toMutableList()

        val otherSerialized = preferences.eh_savedSearches().get().mapNotNull {
            val sourceId = it.split(":")[0].toLongOrNull() ?: return@mapNotNull null
            if (sourceId in currentSources) return@mapNotNull null
            it
        }

        val newSerialized = newSavedSearches.map {
            "${it.first}:" + Json.encodeToString(it.second)
        }
        preferences.eh_savedSearches().set((otherSerialized + newSerialized).toSet())
    }

    /**
     * Restore the categories from Json
     *
     * @param jsonMergedMangaReferences array containing md manga references
     */
    internal fun restoreMergedMangaReferences(jsonMergedMangaReferences: JsonArray) {
        // Get merged manga references from file and from db
        val dbMergedMangaReferences = databaseHelper.getMergedMangaReferences().executeAsBlocking()
        val backupMergedMangaReferences = parser.fromJson<List<MergedMangaReference>>(jsonMergedMangaReferences)
        var lastMergeManga: Manga? = null

        // Iterate over them
        backupMergedMangaReferences.forEach { mergedMangaReference ->
            // Used to know if the merged manga reference is already in the db
            var found = false
            for (dbMergedMangaReference in dbMergedMangaReferences) {
                // If the mergedMangaReference is already in the db, assign the id to the file's mergedMangaReference
                // and do nothing
                if (mergedMangaReference.mergeUrl == dbMergedMangaReference.mergeUrl && mergedMangaReference.mangaUrl == dbMergedMangaReference.mangaUrl) {
                    mergedMangaReference.id = dbMergedMangaReference.id
                    mergedMangaReference.mergeId = dbMergedMangaReference.mergeId
                    mergedMangaReference.mangaId = dbMergedMangaReference.mangaId
                    found = true
                    break
                }
            }
            // If the mergedMangaReference isn't in the db, remove the id and insert a new mergedMangaReference
            // Store the inserted id in the mergedMangaReference
            if (!found) {
                // Let the db assign the id
                var mergedManga = if (mergedMangaReference.mergeUrl != lastMergeManga?.url) databaseHelper.getManga(mergedMangaReference.mergeUrl, MERGED_SOURCE_ID).executeAsBlocking() else lastMergeManga
                if (mergedManga == null) {
                    mergedManga = Manga.create(MERGED_SOURCE_ID).apply {
                        url = mergedMangaReference.mergeUrl
                        title = context.getString(R.string.refresh_merge)
                    }
                    mergedManga.id = databaseHelper.insertManga(mergedManga).executeAsBlocking().insertedId()
                }

                val manga = databaseHelper.getManga(mergedMangaReference.mangaUrl, mergedMangaReference.mangaSourceId).executeAsBlocking() ?: return@forEach
                lastMergeManga = mergedManga

                mergedMangaReference.mergeId = mergedManga.id
                mergedMangaReference.mangaId = manga.id
                mergedMangaReference.id = null
                val result = databaseHelper.insertMergedManga(mergedMangaReference).executeAsBlocking()
                mergedMangaReference.id = result.insertedId()
            }
        }
    }
    // SY <--

    /**
     * Returns manga
     *
     * @return [Manga], null if not found
     */
    internal fun getMangaFromDatabase(manga: Manga): Manga? =
        databaseHelper.getManga(manga.url, manga.source).executeAsBlocking()

    /**
     * Returns list containing manga from library
     *
     * @return [Manga] from library
     */
    internal fun getFavoriteManga(): List<Manga> =
        databaseHelper.getFavoriteMangas().executeAsBlocking()

    internal fun getMergedManga(): List<Manga> =
        databaseHelper.getMergedMangas().executeAsBlocking()

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    internal fun insertManga(manga: Manga): Long? =
        databaseHelper.insertManga(manga).executeAsBlocking().insertedId()

    /**
     * Inserts list of chapters
     */
    private fun insertChapters(chapters: List<Chapter>) {
        databaseHelper.updateChaptersBackup(chapters).executeAsBlocking()
    }

    /**
     * Return number of backups.
     *
     * @return number of backups selected by user
     */
    fun numberOfBackups(): Int = preferences.numberOfBackups().get()
}
