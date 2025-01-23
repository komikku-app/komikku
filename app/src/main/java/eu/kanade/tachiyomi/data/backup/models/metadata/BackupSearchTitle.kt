package eu.kanade.tachiyomi.data.backup.models.metadata

import exh.metadata.sql.models.SearchTitle
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupSearchTitle(
    @ProtoNumber(1) var title: String,
    @ProtoNumber(2) var type: Int,
) {
    fun getSearchTitle(mangaId: Long): SearchTitle {
        return SearchTitle(
            id = null,
            animeId = mangaId,
            title = title,
            type = type,
        )
    }

    companion object {
        fun copyFrom(searchTitle: SearchTitle): BackupSearchTitle {
            return BackupSearchTitle(
                title = searchTitle.title,
                type = searchTitle.type,
            )
        }
    }
}
