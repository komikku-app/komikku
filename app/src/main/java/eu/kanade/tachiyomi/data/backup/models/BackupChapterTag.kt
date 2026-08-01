package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.chapterTag.model.ChapterTag

// KMK -->
/**
 * A chapter tag definition. Only the name is carried: assignments reference tags by name too,
 * so ids never have to survive a round trip between devices.
 */
@Serializable
class BackupChapterTag(
    @ProtoNumber(1) var name: String,
)

val backupChapterTagMapper = { tag: ChapterTag ->
    BackupChapterTag(name = tag.name)
}
// KMK <--
