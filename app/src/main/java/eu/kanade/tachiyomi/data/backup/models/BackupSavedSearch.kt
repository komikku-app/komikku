package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/*
* SY saved searches class
 */
@Serializable
data class BackupSavedSearch(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val query: String = "",
    @ProtoNumber(3) val filterList: String = "",
    @ProtoNumber(4) val source: Long = 0,
    // KMK -->
    @ProtoNumber(9) var backupFeeds: List<BackupFeed> = emptyList(),
    // KMK <--
)

val backupSavedSearchMapper =
    { _: Long, source: Long, name: String, query: String?, filtersJson: String?, backupFeeds: List<BackupFeed> ->
        BackupSavedSearch(
            source = source,
            name = name,
            query = query.orEmpty(),
            filterList = filtersJson ?: "[]",
            // KMK -->
            backupFeeds = backupFeeds,
            // KMK <--
        )
    }
