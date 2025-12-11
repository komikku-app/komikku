package mihon.feature.migration.list

import java.io.Serializable

sealed class MigrationType : Serializable {
    data class MangaList(val mangaIds: List<Long>) : MigrationType()
    data class MangaSingle(val fromMangaId: Long, val toManga: Long?) : MigrationType()
}

data class MigrationProcedureConfig(
    var migration: MigrationType,
    val extraSearchParams: String?,
) : Serializable
