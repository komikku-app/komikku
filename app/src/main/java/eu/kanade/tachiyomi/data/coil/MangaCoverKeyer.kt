package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.Manga as DomainManga

class MangaKeyer(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        val dataSaverKey = getCoverDataSaverKey(sourcePreferences, data.source)
        return if (data.hasCustomCover()) {
            "${data.id};${data.coverLastModified}"
        } else {
            "${data.thumbnailUrl};${data.coverLastModified};$dataSaverKey"
        }
    }
}

class MangaCoverKeyer(
    private val coverCache: CoverCache = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        val dataSaverKey = getCoverDataSaverKey(sourcePreferences, data.sourceId)
        return if (coverCache.getCustomCoverFile(data.mangaId).exists()) {
            "${data.mangaId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified};$dataSaverKey"
        }
    }
}

private fun getCoverDataSaverKey(sourcePreferences: SourcePreferences, sourceId: Long): String {
    val dataSaver = sourcePreferences.dataSaver().get()
    val dataSaverCovers = sourcePreferences.dataSaverCovers().get()
    val excludedSources = sourcePreferences.dataSaverExcludedSources().get()

    val useDataSaver = dataSaverCovers &&
        dataSaver != SourcePreferences.DataSaver.NONE &&
        sourceId.toString() !in excludedSources

    return if (useDataSaver) {
        val server = sourcePreferences.dataSaverServer().get()
        val quality = sourcePreferences.dataSaverImageQuality().get()
        val format = sourcePreferences.dataSaverImageFormatJpeg().get()
        val colorBW = sourcePreferences.dataSaverColorBW().get()
        val ignoreJpeg = sourcePreferences.dataSaverIgnoreJpeg().get()
        val ignoreGif = sourcePreferences.dataSaverIgnoreGif().get()

        "$dataSaver;$server;$quality;$format;$colorBW;$ignoreJpeg;$ignoreGif"
    } else {
        "no-data-saver"
    }
}
