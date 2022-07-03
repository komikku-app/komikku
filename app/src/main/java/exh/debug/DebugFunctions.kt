package exh.debug

import android.app.Application
import androidx.work.WorkManager
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.manga.mangaMapper
import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toMangaInfo
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.NHentai
import exh.EXHMigrations
import exh.eh.EHentaiThrottleManager
import exh.eh.EHentaiUpdateWorker
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.awaitFlatMetadataForManga
import exh.metadata.metadata.base.awaitInsertFlatMetadata
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.isEhBasedManga
import exh.source.nHentaiSourceIds
import exh.util.cancellable
import exh.util.executeOnIO
import exh.util.jobScheduler
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy
import java.util.UUID

@Suppress("unused")
object DebugFunctions {
    val app: Application by injectLazy()
    val db: DatabaseHelper by injectLazy()
    val handler: DatabaseHandler by injectLazy()
    val prefs: PreferencesHelper by injectLazy()
    val sourceManager: SourceManager by injectLazy()
    val updateManga: UpdateManga by injectLazy()
    val getFavorites: GetFavorites by injectLazy()

    fun forceUpgradeMigration() {
        prefs.ehLastVersionCode().set(1)
        EXHMigrations.upgrade(prefs)
    }

    fun forceSetupJobs() {
        prefs.ehLastVersionCode().set(0)
        EXHMigrations.upgrade(prefs)
    }

    fun resetAgedFlagInEXHManga() {
        runBlocking {
            val metadataManga = db.getFavoriteMangaWithMetadata().executeOnIO()

            val allManga = metadataManga.asFlow().cancellable().mapNotNull { manga ->
                if (manga.isEhBasedManga()) manga
                else null
            }.toList()

            allManga.forEach { manga ->
                val meta = handler.awaitFlatMetadataForManga(manga.id!!)?.raise<EHentaiSearchMetadata>() ?: return@forEach
                // remove age flag
                meta.aged = false
                handler.awaitInsertFlatMetadata(meta.flatten())
            }
        }
    }
    private val throttleManager = EHentaiThrottleManager()

    fun getDelegatedSourceList(): String = SourceManager.currentDelegatedSources.map { it.value.sourceName + " : " + it.value.sourceId + " : " + it.value.factory }.joinToString(separator = "\n")

    fun resetEHGalleriesForUpdater() {
        throttleManager.resetThrottle()
        runBlocking {
            val allManga = handler
                .awaitList { mangasQueries.getEhMangaWithMetadata(EH_SOURCE_ID, EXH_SOURCE_ID, mangaMapper) }

            val eh = sourceManager.get(EH_SOURCE_ID)
            val ex = sourceManager.get(EXH_SOURCE_ID)

            allManga.forEach { manga ->
                throttleManager.throttle()

                val networkManga = when (manga.source) {
                    EH_SOURCE_ID -> eh
                    EXH_SOURCE_ID -> ex
                    else -> return@forEach
                }?.getMangaDetails(manga.toMangaInfo()) ?: return@forEach

                updateManga.awaitUpdateFromSource(manga, networkManga, true)
            }
        }
    }

    fun getEHMangaListWithAgedFlagInfo(): String {
        return runBlocking {
            val allManga = handler
                .awaitList { mangasQueries.getEhMangaWithMetadata(EH_SOURCE_ID, EXH_SOURCE_ID, mangaMapper) }

            allManga.map { manga ->
                val meta = handler.awaitFlatMetadataForManga(manga.id)?.raise<EHentaiSearchMetadata>() ?: return@map
                "Aged: ${meta.aged}\t Title: ${manga.title}"
            }
        }.joinToString(",\n")
    }

    fun countAgedFlagInEXHManga(): Int {
        return runBlocking {
            handler
                .awaitList { mangasQueries.getEhMangaWithMetadata(EH_SOURCE_ID, EXH_SOURCE_ID, mangaMapper) }
                .count { manga ->
                    val meta = handler.awaitFlatMetadataForManga(manga.id)
                        ?.raise<EHentaiSearchMetadata>()
                        ?: return@count false
                    meta.aged
                }
        }
    }

    fun addAllMangaInDatabaseToLibrary() {
        db.inTransaction {
            db.lowLevel().executeSQL(
                RawQuery.builder()
                    .query(
                        """
                        UPDATE ${MangaTable.TABLE}
                            SET ${MangaTable.COL_FAVORITE} = 1
                        """.trimIndent(),
                    )
                    .affectsTables(MangaTable.TABLE)
                    .build(),
            )
        }
    }

    fun countMangaInDatabaseInLibrary() = runBlocking { getFavorites.await().size }

    fun countMangaInDatabaseNotInLibrary() = db.getMangas().executeAsBlocking().count { !it.favorite }

    fun countMangaInDatabase() = db.getMangas().executeAsBlocking().size

    fun countMetadataInDatabase() = db.getSearchMetadata().executeAsBlocking().size

    fun countMangaInLibraryWithMissingMetadata() = db.getMangas().executeAsBlocking().count {
        it.favorite && db.getSearchMetadataForManga(it.id!!).executeAsBlocking() == null
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
            WorkManager.getInstance(app).getWorkInfoById(UUID.fromString(it)).get()
        }

        if (info != null) {
            """
            {
                id: ${info.id},
                isPeriodic: ${j.extras["EXTRA_IS_PERIODIC"]},
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
        db.lowLevel().executeSQL(
            RawQuery.builder()
                .query(
                    """
                    UPDATE ${MangaTable.TABLE}
                        SET ${MangaTable.COL_SOURCE} = $to
                        WHERE ${MangaTable.COL_SOURCE} = $from
                    """.trimIndent(),
                )
                .affectsTables(MangaTable.TABLE)
                .build(),
        )
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
        db.inTransaction {
            db.lowLevel().executeSQL(
                RawQuery.builder()
                    .query(
                        """
                        UPDATE ${MangaTable.TABLE}
                            SET ${MangaTable.COL_VIEWER} = 0
                            WHERE ${MangaTable.COL_VIEWER} = -1
                        """.trimIndent(),
                    )
                    .affectsTables(MangaTable.TABLE)
                    .build(),
            )
        }
    }

    fun resetReaderViewerForAllManga() {
        db.inTransaction {
            db.lowLevel().executeSQL(
                RawQuery.builder()
                    .query(
                        """
                        UPDATE ${MangaTable.TABLE}
                            SET ${MangaTable.COL_VIEWER} = 0
                        """.trimIndent(),
                    )
                    .affectsTables(MangaTable.TABLE)
                    .build(),
            )
        }
    }

    fun migrateAllNhentaiToOtherLang() {
        val sources = nHentaiSourceIds.toMutableList()
            .also { it.remove(NHentai.otherId) }
            .joinToString(separator = ",")

        db.inTransaction {
            db.lowLevel().executeSQL(
                RawQuery.builder()
                    .query(
                        """
                        UPDATE ${MangaTable.TABLE}
                            SET ${MangaTable.COL_SOURCE} = ${NHentai.otherId}
                            WHERE ${MangaTable.COL_FAVORITE} = 1 AND ${MangaTable.COL_SOURCE} in ($sources)
                        """.trimIndent(),
                    )
                    .affectsTables(MangaTable.TABLE)
                    .build(),
            )
        }
    }

    fun resetFilteredScanlatorsForAllManga() {
        db.inTransaction {
            db.lowLevel().executeSQL(
                RawQuery.builder()
                    .query(
                        """
                        UPDATE ${MangaTable.TABLE}
                            SET ${MangaTable.COL_FILTERED_SCANLATORS} = NULL
                        """.trimIndent(),
                    )
                    .affectsTables(MangaTable.TABLE)
                    .build(),
            )
        }
    }
}
