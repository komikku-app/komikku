package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.util.system.logcat
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import logcat.LogPriority
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

    override suspend fun await(metadata: RaisedSearchMetadata) {
        try {
            mangaMetadataRepository.insertMetadata(metadata)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
