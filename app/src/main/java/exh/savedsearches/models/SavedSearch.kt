package exh.savedsearches.models

data class SavedSearch(
    // Tag identifier, unique
    var id: Long?,

    // The source the saved search is for
    var source: Long,

    // If false the manga will not grab chapter updates
    var name: String,

    // The query if there is any
    var query: String?,

    // The filter list
    var filtersJson: String?,
)
