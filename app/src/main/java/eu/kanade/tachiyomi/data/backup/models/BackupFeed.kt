package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.data.SelectAllFeedHasSavedSearch

@Serializable
data class BackupFeed(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val global: Boolean = true,
)

val backupFeedMapper =
    { _: Long, source: Long, _: Long?, global: Boolean ->
        BackupFeed(
            source = source,
            global = global,
        )
    }

fun SelectAllFeedHasSavedSearch.backupFeedMapper() = backupFeedMapper(_id, source, saved_search, global)
