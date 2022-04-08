package exh.eh

import kotlinx.serialization.Serializable

@Serializable
data class EHentaiUpdaterStats(
    val startTime: Long,
    val possibleUpdates: Int,
    val updateCount: Int,
)
