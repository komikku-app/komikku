package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMetadataRepository
import eu.kanade.tachiyomi.util.system.logcat
import exh.metadata.metadata.base.FlatMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import logcat.LogPriority

class GetFlatMetadataById(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(id: Long): FlatMetadata? {
        return try {
            val meta = mangaMetadataRepository.getMetadataById(id)
            return if (meta != null) {
                val tags = mangaMetadataRepository.getTagsById(id)
                val titles = mangaMetadataRepository.getTitlesById(id)

                FlatMetadata(meta, tags, titles)
            } else null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun subscribe(id: Long): Flow<FlatMetadata?> {
        return combine(
            mangaMetadataRepository.subscribeMetadataById(id),
            mangaMetadataRepository.subscribeTagsById(id),
            mangaMetadataRepository.subscribeTitlesById(id),
        ) { meta, tags, titles ->
            if (meta != null) {
                FlatMetadata(meta, tags, titles)
            } else null
        }
    }
}
