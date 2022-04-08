package exh.metadata.metadata.base

import kotlinx.serialization.Serializable

@Serializable
data class RaisedTag(
    val namespace: String?,
    val name: String,
    val type: Int,
)
