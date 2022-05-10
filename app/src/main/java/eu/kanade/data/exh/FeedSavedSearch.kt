package eu.kanade.data.exh

import exh.savedsearches.models.FeedSavedSearch

val feedSavedSearchMapper: (Long, Long, Long?, Boolean) -> FeedSavedSearch =
    { id, source, savedSearch, global ->
        FeedSavedSearch(
            id = id,
            source = source,
            savedSearch = savedSearch,
            global = global,
        )
    }
