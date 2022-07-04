package exh.savedsearches.models

data class FeedSavedSearch(
    // Tag identifier, unique
    val id: Long,

    // Source for the saved search
    val source: Long,

    // If -1 then get latest, if set get the saved search
    val savedSearch: Long?,

    // If the feed is a global or source specific feed
    val global: Boolean,
)
