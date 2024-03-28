package exh.debug

import android.app.Application
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.source.AndroidSourceManager
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.util.system.workManager
import exh.eh.EHentaiThrottleManager
import exh.eh.EHentaiUpdateWorker
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.nHentaiSourceIds
import exh.util.jobScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator
import mihon.core.migration.MigrationContext
import mihon.core.migration.MigrationJobFactory
import mihon.core.migration.MigrationStrategyFactory
import mihon.core.migration.Migrator
import mihon.core.migration.migrations.migrations
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetSearchMetadata
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.UUID

@Suppress("unused")
object DebugFunctions {
    private val app: Application by injectLazy()
    private val handler: DatabaseHandler by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val getFavorites: GetFavorites by injectLazy()
    private val getFlatMetadataById: GetFlatMetadataById by injectLazy()
    private val insertFlatMetadata: InsertFlatMetadata by injectLazy()
    private val getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata by injectLazy()
    private val getSearchMetadata: GetSearchMetadata by injectLazy()
    private val getAllManga: GetAllManga by injectLazy()

    fun forceUpgradeMigration(): Boolean {
        val migrationContext = MigrationContext(dryrun = false)
        val migrationJobFactory = MigrationJobFactory(migrationContext, Migrator.scope)
        val migrationStrategyFactory = MigrationStrategyFactory(migrationJobFactory, {})
        val strategy = migrationStrategyFactory.create(1, BuildConfig.VERSION_CODE)
        return runBlocking { strategy(migrations).await() }
    }

    fun forceSetupJobs(): Boolean {
        val migrationContext = MigrationContext(dryrun = false)
        val migrationJobFactory = MigrationJobFactory(migrationContext, Migrator.scope)
        val migrationStrategyFactory = MigrationStrategyFactory(migrationJobFactory, {})
        val strategy = migrationStrategyFactory.create(0, BuildConfig.VERSION_CODE)
        return runBlocking { strategy(migrations).await() }
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

    fun killSyncJobs() {
        val context = Injekt.get<Application>()
        SyncDataJob.stop(context)
    }

    fun killLibraryJobs() {
        val context = Injekt.get<Application>()
        LibraryUpdateJob.stop(context)
    }
}
