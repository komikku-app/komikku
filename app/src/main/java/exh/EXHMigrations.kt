package exh

import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
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
}
