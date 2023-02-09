package tachiyomi.data.source

import tachiyomi.domain.source.model.FeedSavedSearch

val feedSavedSearchMapper: (Long, Long, Long?, Boolean) -> FeedSavedSearch =
    { id, source, savedSearch, global ->
        FeedSavedSearch(
            id = id,
            source = source,
            savedSearch = savedSearch,
            global = global,
        )
    }
