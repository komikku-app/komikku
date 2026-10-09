package tachiyomi.domain.chapterTag.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository

class GetChapterTagsPerManga(
    private val chapterTagRepository: ChapterTagRepository,
) {

    fun subscribe(): Flow<Map<Long, Set<Long>>> = chapterTagRepository.getTagIdsPerMangaAsFlow()
}
