package exh.savedsearches.models

data class FeedSavedSearch(
    // Tag identifier, unique
    var id: Long?,

    // Source for the saved search
    var source: Long,

    // If -1 then get latest, if set get the saved search
    var savedSearch: Long?,

    // If the feed is a global or source specific feed
    var global: Boolean
)
