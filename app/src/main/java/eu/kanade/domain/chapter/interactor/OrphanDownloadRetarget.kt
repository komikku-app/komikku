package eu.kanade.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter

/**
 * Chooses the catalog chapter an existing `orphaned://` download should be linked to, based on
 * the on-disk folder name. No file IO.
 *
 * Other `orphaned://` rows are never targets. A digit-bearing prefix such as `Fallguy69420` is
 * not a scanlator, so a duplicated chapter number uses the lowest [Chapter.sourceOrder].
 */
object OrphanDownloadRetarget {

    sealed interface Decision {
        data class Retarget(val chapter: Chapter) : Decision

        data object Leave : Decision
    }

    fun choose(
        folderName: String,
        mangaTitle: String,
        chapters: List<Chapter>,
    ): Decision {
        val chapterNumber = DownloadFolderMatcher.parseChapterNumberFromFolder(mangaTitle, folderName)
        if (chapterNumber < 0.0) return Decision.Leave

        val catalog = chapters.filter { chapter ->
            !chapter.url.startsWith("orphaned://", ignoreCase = true) &&
                chapter.isRecognizedNumber &&
                chapter.chapterNumber == chapterNumber
        }
        val chosen = chooseAmong(folderName, catalog) ?: return Decision.Leave
        return Decision.Retarget(chosen)
    }

    private fun chooseAmong(folderName: String, catalog: List<Chapter>): Chapter? {
        if (catalog.isEmpty()) return null
        if (catalog.size == 1) return catalog.first()

        val scanlator = DownloadFolderMatcher.parseScanlatorPrefix(folderName.removeSuffix(".cbz"))
        if (scanlator != null) {
            val matched = catalog.filter { chapter ->
                chapter.scanlator?.equals(scanlator, ignoreCase = true) == true
            }
            if (matched.size == 1) return matched.first()
        }
        return catalog.minBy { it.sourceOrder }
    }
}
