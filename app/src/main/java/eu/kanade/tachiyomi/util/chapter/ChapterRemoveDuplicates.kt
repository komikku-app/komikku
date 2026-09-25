package eu.kanade.tachiyomi.util.chapter

import tachiyomi.domain.chapter.model.Chapter

/**
 * Returns a copy of the list with duplicate chapters removed
 */
fun List<Chapter>.removeDuplicates(currentChapter: Chapter): List<Chapter> {
    // KMK -->
    // Chapters without a recognized number (e.g. merged one-shots all numbered -1) are
    // distinct entries, so each one gets its own group instead of collapsing into one.
    return groupBy<Chapter, Any> { if (it.isRecognizedNumber) it.chapterNumber else it.id }
        // KMK <--
        .map { (_, chapters) ->
            chapters.find { it.id == currentChapter.id }
                ?: chapters.find { it.scanlator == currentChapter.scanlator }
                ?: chapters.first()
        }
}
