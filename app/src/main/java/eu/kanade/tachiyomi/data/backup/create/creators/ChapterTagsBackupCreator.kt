package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupChapterTag
import eu.kanade.tachiyomi.data.backup.models.backupChapterTagMapper
import tachiyomi.domain.chapterTag.interactor.GetChapterTags
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Backs up the chapter tag definitions. Tags with no assignments are included too, so an empty
 * tag someone created ahead of time survives a restore.
 */
class ChapterTagsBackupCreator(
    private val getChapterTags: GetChapterTags = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupChapterTag> {
        return getChapterTags.await().map(backupChapterTagMapper)
    }
}
// KMK <--
