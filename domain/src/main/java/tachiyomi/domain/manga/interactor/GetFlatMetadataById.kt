package tachiyomi.domain.manga.interactor

import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.metadata.metadata.base.FlatMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class GetFlatMetadataById(
    private val mangaMetadataRepository: MangaMetadataRepository,
) : MetadataSource.GetFlatMetadataById {

    override suspend fun await(id: Long): FlatMetadata? {
        return try {
            val meta = mangaMetadataRepository.getMetadataById(id)
            return if (meta != null) {
                val tags = mangaMetadataRepository.getTagsById(id)
                val titles = mangaMetadataRepository.getTitlesById(id)

                FlatMetadata(meta, tags, titles)
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    fun subscribe(id: Long): Flow<FlatMetadata?> {
        return combine(
            mangaMetadataRepository.subscribeMetadataById(id),
            mangaMetadataRepository.subscribeTagsById(id),
            mangaMetadataRepository.subscribeTitlesById(id),
        ) { meta, tags, titles ->
            if (meta != null) {
                FlatMetadata(meta, tags, titles)
            } else {
                null
            }
        }
    }
}
