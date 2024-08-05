package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupFeed(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val global: Boolean = true,
    @ProtoNumber(9) val savedSearch: BackupSavedSearch? = null,
)

val backupFeedMapper =
    { source: Long, global: Boolean, savedSearch: Long?, name: String?, query: String?, filtersJson: String? ->
        BackupFeed(
            source = source,
            global = global,
            savedSearch = if (savedSearch != null && name != null) {
                backupSavedSearchMapper(savedSearch, source, name, query, filtersJson)
            } else {
                null
            },
        )
    }
