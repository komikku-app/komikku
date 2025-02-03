package tachiyomi.domain.manga.interactor

import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
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

    override suspend fun awaitSearchMetadata(ids: List<Long>): Map<Long, SearchMetadata> {
        return try {
            mangaMetadataRepository.getMetadataByIds(ids)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
        }
    }

    override suspend fun await(ids: List<Long>): Map<Long, FlatMetadata> {
        return try {
            val metas = mangaMetadataRepository.getMetadataByIds(ids)
            val tags = mangaMetadataRepository.getTagsByIds(ids)
            val titles = mangaMetadataRepository.getTitlesByIds(ids)
            ids.mapNotNull { id ->
                val meta = metas[id] ?: return@mapNotNull null
                val tag = tags[id] ?: emptyList()
                val title = titles[id] ?: emptyList()

                FlatMetadata(meta, tag, title)
            }
                .associateBy { it.metadata.mangaId }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyMap()
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
