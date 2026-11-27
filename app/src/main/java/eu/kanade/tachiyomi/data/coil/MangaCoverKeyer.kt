package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.vault.VaultSession
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.Manga as DomainManga

class MangaKeyer : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        // KMK -->
        // Manga ids are per database, so the cache key must not be shared between profiles.
        val prefix = if (VaultSession.isActive) "vault:" else ""
        return prefix + if (data.hasCustomCover()) {
            // KMK <--
            "${data.id};${data.coverLastModified}"
        } else {
            "${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class MangaCoverKeyer(
    private val coverCache: CoverCache = Injekt.get(),
) : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        // KMK -->
        val prefix = if (VaultSession.isActive) "vault:" else ""
        // KMK <--
        return if (coverCache.getCustomCoverFile(data.mangaId).exists()) {
            // KMK -->
            "$prefix${data.mangaId};${data.lastModified}"
            // KMK <--
        } else {
            "${data.url};${data.lastModified}"
        }
    }
}
