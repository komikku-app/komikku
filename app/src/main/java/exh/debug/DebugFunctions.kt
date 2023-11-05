package exh.debug

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.workManager
import exh.EXHMigrations
import exh.eh.EHentaiThrottleManager
import exh.eh.EHentaiUpdateWorker
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.nHentaiSourceIds
import exh.util.jobScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetSearchMetadata
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import java.util.UUID

@Suppress("unused")
object DebugFunctions {
    private val app: Application by injectLazy()
    private val handler: DatabaseHandler by injectLazy()
    private val prefsStore: PreferenceStore by injectLazy()
    private val basePrefs: BasePreferences by injectLazy()
    private val uiPrefs: UiPreferences by injectLazy()
    private val networkPrefs: NetworkPreferences by injectLazy()
    private val sourcePrefs: SourcePreferences by injectLazy()
    private val securityPrefs: SecurityPreferences by injectLazy()
    private val libraryPrefs: LibraryPreferences by injectLazy()
    private val readerPrefs: ReaderPreferences by injectLazy()
    private val backupPrefs: BackupPreferences by injectLazy()
    private val trackerManager: TrackerManager by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val getFavorites: GetFavorites by injectLazy()
    private val getFlatMetadataById: GetFlatMetadataById by injectLazy()
    private val insertFlatMetadata: InsertFlatMetadata by injectLazy()
    private val getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata by injectLazy()
    private val getSearchMetadata: GetSearchMetadata by injectLazy()
    private val getAllManga: GetAllManga by injectLazy()
    private val pagePreviewCache: PagePreviewCache by injectLazy()

    fun forceUpgradeMigration() {
        val lastVersionCode = prefsStore.getInt("eh_last_version_code", 0)
        lastVersionCode.set(1)
        EXHMigrations.upgrade(
            context = app,
            preferenceStore = prefsStore,
            basePreferences = basePrefs,
            uiPreferences = uiPrefs,
            networkPreferences = networkPrefs,
            sourcePreferences = sourcePrefs,
            securityPreferences = securityPrefs,
            libraryPreferences = libraryPrefs,
            readerPreferences = readerPrefs,
            backupPreferences = backupPrefs,
            trackerManager = trackerManager,
            pagePreviewCache = pagePreviewCache,
        )
    }

    fun forceSetupJobs() {
        val lastVersionCode = prefsStore.getInt("eh_last_version_code", 0)
        lastVersionCode.set(0)
        EXHMigrations.upgrade(
            context = app,
            preferenceStore = prefsStore,
            basePreferences = basePrefs,
            uiPreferences = uiPrefs,
            networkPreferences = networkPrefs,
            sourcePreferences = sourcePrefs,
            securityPreferences = securityPrefs,
            libraryPreferences = libraryPrefs,
            readerPreferences = readerPrefs,
            backupPreferences = backupPrefs,
            trackerManager = trackerManager,
            pagePreviewCache = pagePreviewCache,
        )
    }

    fun resetAgedFlagInEXHManga() {
        runBlocking {
            getExhFavoriteMangaWithMetadata.await().forEach { manga ->
                val meta = getFlatMetadataById.await(manga.id)?.raise<EHentaiSearchMetadata>() ?: return@forEach
                // remove age flag
                meta.aged = false
                insertFlatMetadata.await(meta)
            }
        }
    }
    private val throttleManager = EHentaiThrottleManager()

    fun getDelegatedSourceList(): String = AndroidSourceManager.currentDelegatedSources.map {
        it.value.sourceName + " : " + it.value.sourceId + " : " + it.value.factory
    }.joinToString(separator = "\n")

    fun resetEHGalleriesForUpdater() {
        throttleManager.resetThrottle()
        runBlocking {
            val allManga = getExhFavoriteMangaWithMetadata.await()

            val eh = sourceManager.get(EH_SOURCE_ID)
            val ex = sourceManager.get(EXH_SOURCE_ID)

            allManga.forEach { manga ->
                throttleManager.throttle()

                val networkManga = when (manga.source) {
                    EH_SOURCE_ID -> eh
                    EXH_SOURCE_ID -> ex
                    else -> return@forEach
                }?.getMangaDetails(manga.toSManga()) ?: return@forEach

                updateManga.awaitUpdateFromSource(manga, networkManga, true)
            }
        }
    }

    fun getEHMangaListWithAgedFlagInfo(): String {
        return runBlocking {
            getExhFavoriteMangaWithMetadata.await().map { manga ->
                val meta = getFlatMetadataById.await(manga.id)?.raise<EHentaiSearchMetadata>() ?: return@map
                "Aged: ${meta.aged}\t Title: ${manga.title}"
            }
        }.joinToString(",\n")
    }

    fun countAgedFlagInEXHManga(): Int {
        return runBlocking {
            getExhFavoriteMangaWithMetadata.await()
                .count { manga ->
                    val meta = getFlatMetadataById.await(manga.id)
                        ?.raise<EHentaiSearchMetadata>()
                        ?: return@count false
                    meta.aged
                }
        }
    }

    fun addAllMangaInDatabaseToLibrary() {
        runBlocking { handler.await { ehQueries.addAllMangaInDatabaseToLibrary() } }
    }

    fun countMangaInDatabaseInLibrary() = runBlocking { getFavorites.await().size }

    fun countMangaInDatabaseNotInLibrary() = runBlocking { getAllManga.await() }.count { !it.favorite }

    fun countMangaInDatabase() = runBlocking { getAllManga.await() }.size

    fun countMetadataInDatabase() = runBlocking { getSearchMetadata.await().size }

    fun countMangaInLibraryWithMissingMetadata() = runBlocking {
        runBlocking { getAllManga.await() }.count {
            it.favorite && getSearchMetadata.await(it.id) == null
        }
    }

    fun clearSavedSearches() = runBlocking { handler.await { saved_searchQueries.deleteAll() } }

    fun listAllSources() = sourceManager.getCatalogueSources().joinToString("\n") {
        "${it.id}: ${it.name} (${it.lang.uppercase()})"
    }

    fun listAllSourcesClassName() = sourceManager.getCatalogueSources().joinToString("\n") {
        "${it::class.qualifiedName}: ${it.name} (${it.lang.uppercase()})"
    }

    fun listVisibleSources() = sourceManager.getVisibleCatalogueSources().joinToString("\n") {
        "${it.id}: ${it.name} (${it.lang.uppercase()})"
    }

    fun listAllHttpSources() = sourceManager.getOnlineSources().joinToString("\n") {
        "${it.id}: ${it.name} (${it.lang.uppercase()})"
    }
    fun listVisibleHttpSources() = sourceManager.getVisibleOnlineSources().joinToString("\n") {
        "${it.id}: ${it.name} (${it.lang.uppercase()})"
    }

    fun convertAllEhentaiGalleriesToExhentai() = convertSources(EH_SOURCE_ID, EXH_SOURCE_ID)

    fun convertAllExhentaiGalleriesToEhentai() = convertSources(EXH_SOURCE_ID, EH_SOURCE_ID)

    fun testLaunchEhentaiBackgroundUpdater() {
        EHentaiUpdateWorker.launchBackgroundTest(app)
    }

    fun rescheduleEhentaiBackgroundUpdater() {
        EHentaiUpdateWorker.scheduleBackground(app)
    }

    fun listScheduledJobs() = app.jobScheduler.allPendingJobs.joinToString(",\n") { j ->
        val info = j.extras.getString("EXTRA_WORK_SPEC_ID")?.let {
            app.workManager.getWorkInfoById(UUID.fromString(it)).get()
        }

        if (info != null) {
            """
            {
                id: ${info.id},
                isPeriodic: ${j.extras.getBoolean("EXTRA_IS_PERIODIC")},
                state: ${info.state.name},
                tags: [
                    ${info.tags.joinToString(separator = ",\n                    ")}
                ],
            }
            """.trimIndent()
        } else {
            """
            {
                info: ${j.id},
                isPeriodic: ${j.isPeriodic},
                isPersisted: ${j.isPersisted},
                intervalMillis: ${j.intervalMillis},
            }
            """.trimIndent()
        }
    }

    fun cancelAllScheduledJobs() = app.jobScheduler.cancelAll()

    private fun convertSources(from: Long, to: Long) {
        runBlocking {
            handler.await { ehQueries.migrateSource(to, from) }
        }
    }

    /*fun copyEHentaiSavedSearchesToExhentai() {
        runBlocking {
            val source = sourceManager.get(EH_SOURCE_ID) as? CatalogueSource ?: return@runBlocking
            val newSource = sourceManager.get(EXH_SOURCE_ID) as? CatalogueSource ?: return@runBlocking
            val savedSearches = prefs.savedSearches().get().mapNotNull {
                try {
                    val id = it.substringBefore(':').toLong()
                    if (id != source.id) return@mapNotNull null
                    Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
                } catch (t: RuntimeException) {
                    // Load failed
                    xLogE("Failed to load saved search!", t)
                    t.printStackTrace()
                    null
                }
            }.toMutableList()
            savedSearches += prefs.savedSearches().get().mapNotNull {
                try {
                    val id = it.substringBefore(':').toLong()
                    if (id != newSource.id) return@mapNotNull null
                    Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
                } catch (t: RuntimeException) {
                    // Load failed
                    xLogE("Failed to load saved search!", t)
                    t.printStackTrace()
                    null
                }
            }.filterNot { newSavedSearch -> savedSearches.any { it.name == newSavedSearch.name } }

            val otherSerialized = prefs.savedSearches().get().filter {
                !it.startsWith("${newSource.id}:")
            }
            val newSerialized = savedSearches.map {
                "${newSource.id}:" + Json.encodeToString(it)
            }
            prefs.savedSearches().set((otherSerialized + newSerialized).toSet())
        }
    }

    fun copyExhentaiSavedSearchesToEHentai() {
        runBlocking {
            val source = sourceManager.get(EXH_SOURCE_ID) as? CatalogueSource ?: return@runBlocking
            val newSource = sourceManager.get(EH_SOURCE_ID) as? CatalogueSource ?: return@runBlocking
            val savedSearches = prefs.savedSearches().get().mapNotNull {
                try {
                    val id = it.substringBefore(':').toLong()
                    if (id != source.id) return@mapNotNull null
                    Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
                } catch (t: RuntimeException) {
                    // Load failed
                    xLogE("Failed to load saved search!", t)
                    t.printStackTrace()
                    null
                }
            }.toMutableList()
            savedSearches += prefs.savedSearches().get().mapNotNull {
                try {
                    val id = it.substringBefore(':').toLong()
                    if (id != newSource.id) return@mapNotNull null
                    Json.decodeFromString<JsonSavedSearch>(it.substringAfter(':'))
                } catch (t: RuntimeException) {
                    // Load failed
                    xLogE("Failed to load saved search!", t)
                    t.printStackTrace()
                    null
                }
            }.filterNot { newSavedSearch -> savedSearches.any { it.name == newSavedSearch.name } }

            val otherSerialized = prefs.savedSearches().get().filter {
                !it.startsWith("${newSource.id}:")
            }
            val newSerialized = savedSearches.map {
                "${newSource.id}:" + Json.encodeToString(it)
            }
            prefs.savedSearches().set((otherSerialized + newSerialized).toSet())
        }
    }*/

    fun fixReaderViewerBackupBug() {
        runBlocking { handler.await { ehQueries.fixReaderViewerBackupBug() } }
    }

    fun resetReaderViewerForAllManga() {
        runBlocking { handler.await { ehQueries.resetReaderViewerForAllManga() } }
    }

    fun migrateLangNhentaiToMultiLangSource() {
        val sources = nHentaiSourceIds - NHentai.otherId

        runBlocking { handler.await { ehQueries.migrateAllNhentaiToOtherLang(NHentai.otherId, sources) } }
    }

    fun exportProtobufScheme() = ProtoBufSchemaGenerator.generateSchemaText(Backup.serializer().descriptor)
}
