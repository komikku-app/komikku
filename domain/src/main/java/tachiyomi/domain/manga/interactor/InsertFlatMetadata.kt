package tachiyomi.domain.manga.interactor

import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.FlatMetadata
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class InsertFlatMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) : MetadataSource.InsertFlatMetadata {

    suspend fun await(flatMetadata: FlatMetadata) {
        try {
            mangaMetadataRepository.insertFlatMetadata(flatMetadata)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun await(flatMetadatas: List<FlatMetadata>) {
        try {
            mangaMetadataRepository.insertFlatMetadatas(flatMetadatas)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun await(metadata: RaisedSearchMetadata) {
        try {
            mangaMetadataRepository.insertMetadata(metadata)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
