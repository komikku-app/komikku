package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.repository.ChapterRepository

class DeleteChapters(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapters: List<Long>) {
        chapterRepository.removeChaptersWithIds(chapters)
    }
}
