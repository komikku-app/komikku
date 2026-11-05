package mihon.domain.migration.usecases

import tachiyomi.domain.chapter.model.Chapter

/**
 * Decides which downloaded chapters can move onto a target catalog during migration.
 * Chapter numbers must match exactly. No file IO and no `orphaned://` rows are created.
 */
object PlanDownloadMigration {

    data class Move(
        val oldChapter: Chapter,
        val targetChapter: Chapter,
    )

    data class Plan(
        val moves: List<Move>,
        val skippedChapterIds: List<Long>,
    )

    fun plan(
        oldChapters: List<Chapter>,
        targetChapters: List<Chapter>,
        downloadedChapterIds: Set<Long>,
    ): Plan {
        val catalogTargets = targetChapters.filter { chapter ->
            !chapter.url.startsWith("orphaned://", ignoreCase = true)
        }
        val candidates = oldChapters.filter { chapter ->
            chapter.id in downloadedChapterIds && chapter.isRecognizedNumber
        }
        val moves = candidates.mapNotNull { oldChapter ->
            val target = chooseTarget(oldChapter, catalogTargets) ?: return@mapNotNull null
            Move(oldChapter, target)
        }
        val movedIds = moves.map { it.oldChapter.id }.toSet()
        val skippedChapterIds = candidates.map { it.id }.filter { it !in movedIds }
        return Plan(moves, skippedChapterIds)
    }

    private fun chooseTarget(oldChapter: Chapter, targetChapters: List<Chapter>): Chapter? {
        val matches = targetChapters.filter { target ->
            target.isRecognizedNumber && target.chapterNumber == oldChapter.chapterNumber
        }
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches.first()

        val scanlator = oldChapter.scanlator?.takeIf { it.isNotBlank() }
        if (scanlator != null) {
            val byScanlator = matches.filter { target ->
                target.scanlator?.equals(scanlator, ignoreCase = true) == true
            }
            if (byScanlator.size == 1) return byScanlator.first()
        }
        return matches.minBy { it.sourceOrder }
    }
}
