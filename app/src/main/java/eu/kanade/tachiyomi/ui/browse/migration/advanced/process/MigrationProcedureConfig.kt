package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import java.io.Serializable

data class MigrationProcedureConfig(
    var mangaIds: List<Long>,
    val extraSearchParams: String?,
) : Serializable
