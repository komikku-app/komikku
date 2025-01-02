package tachiyomi.domain.source.model

data class FeedSavedSearchUpdate(
    // Tag identifier, unique
    val id: Long,

    // Source for the saved search
    val source: Long? = null,

    // If null then get latest/popular, if set get the saved search
    val savedSearch: Long? = null,

    // If the feed is a global (FeedScreen) or source specific feed (SourceFeedScreen)
    val global: Boolean? = null,

    // Local index in global feed if it's a global one or in source feed if it's a source one
    val feedOrder: Long? = null,
)
