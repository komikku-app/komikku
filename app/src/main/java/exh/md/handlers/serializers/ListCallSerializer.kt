package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class ListCallResponse<T>(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val results: List<T>
)
