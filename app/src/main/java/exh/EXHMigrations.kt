package exh

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.source.EH_OLD_ID
import exh.source.EH_SOURCE_ID
import exh.source.EXH_OLD_ID
import exh.source.EXH_SOURCE_ID
import exh.source.HBROWSE_OLD_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.NHENTAI_OLD_ID
import exh.source.NHENTAI_SOURCE_ID
import exh.source.TSUMINO_OLD_ID
import exh.source.TSUMINO_SOURCE_ID
import tachiyomi.domain.manga.model.Manga
import java.net.URI
import java.net.URISyntaxException

object EXHMigrations {

    /**
     * Migrate old source ID of delegated sources in old backup
     */
    fun migrateBackupEntry(manga: Manga): Manga {
        var newManga = manga
        if (newManga.source == NHENTAI_OLD_ID) {
            newManga = newManga.copy(
                // Migrate the old source to the delegated one
                source = NHENTAI_SOURCE_ID,
                // Migrate nhentai URLs
                url = getUrlWithoutDomain(newManga.url),
            )
        }

        // Migrate Tsumino source IDs
        if (newManga.source == TSUMINO_OLD_ID) {
            newManga = newManga.copy(
                source = TSUMINO_SOURCE_ID,
            )
        }

        if (newManga.source == HBROWSE_OLD_ID) {
            newManga = newManga.copy(
                source = HBROWSE_SOURCE_ID,
                url = newManga.url + "/c00001/",
            )
        }

        // KMK -->
        // Allow importing of EHentai extension backups
        if (newManga.source == EH_OLD_ID) {
            newManga = newManga.copy(
                source = EH_SOURCE_ID,
            )
        }
        if (newManga.source == EXH_OLD_ID) {
            newManga = newManga.copy(
                source = EXH_SOURCE_ID,
            )
        }
        // KMK <--

        return newManga
    }

    // KMK -->
    /**
     * Migrate old source ID of delegated sources in old backup
     */
    fun migrateBackupMergedMangaReference(mergeReference: BackupMergedMangaReference): BackupMergedMangaReference {
        return when (mergeReference.mangaSourceId) {
            NHENTAI_OLD_ID -> mergeReference.copy(
                mangaSourceId = NHENTAI_SOURCE_ID,
            )
            TSUMINO_OLD_ID -> mergeReference.copy(
                mangaSourceId = TSUMINO_SOURCE_ID,
            )
            HBROWSE_OLD_ID -> mergeReference.copy(
                mangaSourceId = HBROWSE_SOURCE_ID,
            )
            EH_OLD_ID -> mergeReference.copy(
                mangaSourceId = EH_SOURCE_ID,
            )
            EXH_OLD_ID -> mergeReference.copy(
                mangaSourceId = EXH_SOURCE_ID,
            )
            else -> mergeReference
        }
    }

    /**
     * Migrate old source ID of delegated sources in old backup
     */
    fun migrateBackupSavedSearch(savedSearch: BackupSavedSearch): BackupSavedSearch {
        return when (savedSearch.source) {
            NHENTAI_OLD_ID -> savedSearch.copy(
                source = NHENTAI_SOURCE_ID,
            )
            TSUMINO_OLD_ID -> savedSearch.copy(
                source = TSUMINO_SOURCE_ID,
            )
            HBROWSE_OLD_ID -> savedSearch.copy(
                source = HBROWSE_SOURCE_ID,
            )
            EH_OLD_ID -> savedSearch.copy(
                source = EH_SOURCE_ID,
            )
            EXH_OLD_ID -> savedSearch.copy(
                source = EXH_SOURCE_ID,
            )
            else -> savedSearch
        }
    }

    /**
     * Migrate old source ID of delegated sources in old backup
     */
    fun migrateBackupFeed(feed: BackupFeed): BackupFeed {
        return when (feed.source) {
            NHENTAI_OLD_ID -> feed.copy(
                source = NHENTAI_SOURCE_ID,
            )
            TSUMINO_OLD_ID -> feed.copy(
                source = TSUMINO_SOURCE_ID,
            )
            HBROWSE_OLD_ID -> feed.copy(
                source = HBROWSE_SOURCE_ID,
            )
            EH_OLD_ID -> feed.copy(
                source = EH_SOURCE_ID,
            )
            EXH_OLD_ID -> feed.copy(
                source = EXH_SOURCE_ID,
            )
            else -> feed
        }
    }

    /**
     * Migrate old source ID of delegated sources in old backup
     */
    fun migrateSourceIds(oldSourceIds: Set<String>): Set<String> {
        var newSourceIds = oldSourceIds
        if (NHENTAI_OLD_ID.toString() in newSourceIds) {
            newSourceIds = newSourceIds.minus(NHENTAI_OLD_ID.toString())
                .plus(NHENTAI_SOURCE_ID.toString())
        }
        if (TSUMINO_OLD_ID.toString() in newSourceIds) {
            newSourceIds = newSourceIds.minus(TSUMINO_OLD_ID.toString())
                .plus(TSUMINO_SOURCE_ID.toString())
        }
        if (HBROWSE_OLD_ID.toString() in newSourceIds) {
            newSourceIds = newSourceIds.minus(HBROWSE_OLD_ID.toString())
                .plus(HBROWSE_SOURCE_ID.toString())
        }
        if (EH_OLD_ID.toString() in newSourceIds) {
            newSourceIds = newSourceIds.minus(EH_OLD_ID.toString())
                .plus(EH_SOURCE_ID.toString())
        }
        if (EXH_OLD_ID.toString() in newSourceIds) {
            newSourceIds = newSourceIds.minus(EXH_OLD_ID.toString())
                .plus(EXH_SOURCE_ID.toString())
        }
        return newSourceIds
    }
    // KMK -->

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
}
