package tachiyomi.data.source

import tachiyomi.domain.source.model.SavedSearch

object SavedSearchMapper {
    fun map(
        id: Long,
        source: Long,
        name: String,
        query: String?,
        filtersJson: String?,
    ): SavedSearch {
        return SavedSearch(
            id = id,
            source = source,
            name = name,
            query = query,
            filtersJson = filtersJson,
        )
    }
}
