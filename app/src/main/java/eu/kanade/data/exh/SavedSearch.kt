package eu.kanade.data.exh

import exh.savedsearches.models.SavedSearch

val savedSearchMapper: (Long, Long, String, String?, String?) -> SavedSearch =
    { id, source, name, query, filtersJson ->
        SavedSearch(
            id = id,
            source = source,
            name = name,
            query = query,
            filtersJson = filtersJson,
        )
    }
