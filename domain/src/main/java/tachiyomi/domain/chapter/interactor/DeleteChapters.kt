package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.repository.ChapterRepository

class DeleteChapters(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapters: List<Long>) {
        chapterRepository.removeChaptersWithIds(chapters)
    }
}
