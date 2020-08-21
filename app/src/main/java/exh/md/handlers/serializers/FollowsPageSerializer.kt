package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class FollowsPageResult(
    val result: List<Result> = emptyList()
)

@Serializable
data class Result(
    val title: String,
    val chapter: String,
    val follow_type: Int,
    val manga_id: Int,
    val volume: String
)
