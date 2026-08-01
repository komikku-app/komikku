package tachiyomi.domain.chapterTag.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class GetChapterTagFilter(
    private val chapterTagRepository: ChapterTagRepository,
) {

    fun subscribe(mangaId: Long): Flow<Set<Long>> = chapterTagRepository.getFilterTagIdsAsFlow(mangaId)
}
