package exh.metadata.metadata.base

import kotlinx.serialization.Serializable

@Serializable
data class RaisedTitle(
    val title: String,
    val type: Int = 0,
)
