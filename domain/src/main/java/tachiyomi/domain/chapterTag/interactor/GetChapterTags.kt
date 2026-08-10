package tachiyomi.domain.chapterTag.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class GetChapterTags(
    private val chapterTagRepository: ChapterTagRepository,
) {

    fun subscribe(): Flow<List<ChapterTag>> = chapterTagRepository.getAllAsFlow()

    suspend fun await(): List<ChapterTag> = chapterTagRepository.getAll()

    fun subscribeByMangaId(mangaId: Long): Flow<Map<Long, List<ChapterTag>>> =
        chapterTagRepository.getTagsByMangaIdAsFlow(mangaId)

    suspend fun awaitByMangaId(mangaId: Long): Map<Long, List<ChapterTag>> =
        chapterTagRepository.getTagsByMangaId(mangaId)

    suspend fun awaitTagIdsByChapterIds(chapterIds: List<Long>): Map<Long, List<Long>> =
        chapterTagRepository.getTagIdsByChapterIds(chapterIds)
}
