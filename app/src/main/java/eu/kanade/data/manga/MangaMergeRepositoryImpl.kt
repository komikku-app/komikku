package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.mergedMangaReferenceMapper
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaMergeRepository
import exh.merged.sql.models.MergedMangaReference
import kotlinx.coroutines.flow.Flow

class MangaMergeRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaMergeRepository {

    override suspend fun getMergedMangaById(id: Long): List<Manga> {
        return handler.awaitList { mergedQueries.selectMergedMangasById(id, mangaMapper) }
    }

    override suspend fun subscribeMergedMangaById(id: Long): Flow<List<Manga>> {
        return handler.subscribeToList { mergedQueries.selectMergedMangasById(id, mangaMapper) }
    }

    override suspend fun getReferencesById(id: Long): List<MergedMangaReference> {
        return handler.awaitList { mergedQueries.selectByMergeId(id, mergedMangaReferenceMapper) }
    }

    override suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>> {
        return handler.subscribeToList { mergedQueries.selectByMergeId(id, mergedMangaReferenceMapper) }
    }
}
