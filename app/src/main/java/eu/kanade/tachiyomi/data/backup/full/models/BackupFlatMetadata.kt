package eu.kanade.tachiyomi.data.backup.full.models

import eu.kanade.tachiyomi.data.backup.full.models.metadata.BackupSearchMetadata
import eu.kanade.tachiyomi.data.backup.full.models.metadata.BackupSearchTag
import eu.kanade.tachiyomi.data.backup.full.models.metadata.BackupSearchTitle
import exh.metadata.metadata.base.FlatMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupFlatMetadata(
    @ProtoNumber(1) var searchMetadata: BackupSearchMetadata,
    @ProtoNumber(2) var searchTags: List<BackupSearchTag> = emptyList(),
    @ProtoNumber(3) var searchTitles: List<BackupSearchTitle> = emptyList(),
) {
    fun getFlatMetadata(mangaId: Long): FlatMetadata {
        return FlatMetadata(
            metadata = searchMetadata.getSearchMetadata(mangaId),
            tags = searchTags.map { it.getSearchTag(mangaId) },
            titles = searchTitles.map { it.getSearchTitle(mangaId) },
        )
    }

    companion object {
        fun copyFrom(flatMetadata: FlatMetadata): BackupFlatMetadata {
            return BackupFlatMetadata(
                searchMetadata = BackupSearchMetadata.copyFrom(flatMetadata.metadata),
                searchTags = flatMetadata.tags.map { BackupSearchTag.copyFrom(it) },
                searchTitles = flatMetadata.titles.map { BackupSearchTitle.copyFrom(it) },
            )
        }
    }
}
