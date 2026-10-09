package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupChapterTag
import tachiyomi.domain.chapterTag.interactor.CreateChapterTagWithName
import tachiyomi.domain.chapterTag.interactor.GetChapterTags
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Recreates the chapter tag definitions from a backup. Tags are matched against existing ones by
 * exact name, the same uniqueness rule the tag management screen enforces, so restoring onto a
 * device that already has a "Filler" tag reuses it instead of creating a duplicate.
 */
class ChapterTagsRestorer(
    private val getChapterTags: GetChapterTags = Injekt.get(),
    private val createChapterTagWithName: CreateChapterTagWithName = Injekt.get(),
) {

    suspend operator fun invoke(backupChapterTags: List<BackupChapterTag>) {
        if (backupChapterTags.isEmpty()) return

        val existingNames = getChapterTags.await().mapTo(mutableSetOf()) { it.name }

        backupChapterTags
            .distinctBy { it.name }
            .filterNot { it.name in existingNames }
            .forEach { createChapterTagWithName.await(it.name) }
    }
}
// KMK <--
