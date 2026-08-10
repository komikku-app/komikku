package tachiyomi.domain.chapterTag.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapterTag.model.ChapterTagFilter
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class GetChapterTagFilter(
    private val chapterTagRepository: ChapterTagRepository,
) {

    fun subscribe(mangaId: Long): Flow<ChapterTagFilter> = chapterTagRepository.getFilterAsFlow(mangaId)

    suspend fun await(mangaId: Long): ChapterTagFilter = chapterTagRepository.getFilter(mangaId)
}
