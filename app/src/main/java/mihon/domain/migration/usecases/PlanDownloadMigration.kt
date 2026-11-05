package mihon.domain.migration.usecases

import tachiyomi.domain.chapter.model.Chapter

/**
 * Decides which downloaded chapters can move onto a target catalog during migration.
 *
 * Recognized numbers that exist on the target become [Move] pairs. Everything else that is
 * downloaded becomes an [orphanOldChapters] candidate so the mover can create `orphaned://`
 * rows on the destination instead of leaving files on the old entry.
 */
object PlanDownloadMigration {

    data class Move(
        val oldChapter: Chapter,
        val targetChapter: Chapter,
    )

    data class Plan(
        val moves: List<Move>,
        val orphanOldChapters: List<Chapter>,
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
            chapter.id in downloadedChapterIds
        }
        val moves = candidates.mapNotNull { oldChapter ->
            if (!oldChapter.isRecognizedNumber) return@mapNotNull null
            val target = chooseTarget(oldChapter, catalogTargets) ?: return@mapNotNull null
            Move(oldChapter, target)
        }
        val movedIds = moves.map { it.oldChapter.id }.toSet()
        val orphanOldChapters = candidates.filter { it.id !in movedIds }
        return Plan(moves, orphanOldChapters)
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
