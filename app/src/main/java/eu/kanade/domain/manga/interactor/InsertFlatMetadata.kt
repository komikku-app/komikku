package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.MangaMetadataRepository
import eu.kanade.tachiyomi.util.system.logcat
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import logcat.LogPriority

class InsertFlatMetadata(
    private val mangaMetadataRepository: MangaMetadataRepository,
) {

    suspend fun await(flatMetadata: FlatMetadata) {
        try {
            mangaMetadataRepository.insertFlatMetadata(flatMetadata)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun await(metadata: RaisedSearchMetadata) {
        try {
            mangaMetadataRepository.insertMetadata(metadata)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
