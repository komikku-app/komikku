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
        val dataSaverKey = sourcePreferences.getCoverDataSaverKey(data.source)
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
        val dataSaverKey = sourcePreferences.getCoverDataSaverKey(data.sourceId)
        return if (coverCache.getCustomCoverFile(data.mangaId).exists()) {
            "${data.mangaId};${data.lastModified}"
        } else {
            "${data.url};${data.lastModified};$dataSaverKey"
        }
    }
}

private fun SourcePreferences.getCoverDataSaverKey(sourceId: Long): String {
    val useDataSaver = dataSaverCovers().get() &&
        dataSaver().get() != SourcePreferences.DataSaver.NONE &&
        sourceId.toString() !in dataSaverExcludedSources().get()
    return if (useDataSaver) {
        "${dataSaver().get()};${dataSaverServer().get()};${dataSaverImageQuality().get()};${dataSaverImageFormatJpeg().get()};${dataSaverColorBW().get()};${dataSaverIgnoreJpeg().get()};${dataSaverIgnoreGif().get()}"
    } else {
        "no-data-saver"
    }
}
