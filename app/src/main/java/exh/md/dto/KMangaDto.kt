package exh.md.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ViewerApiResponse(
    @SerialName("page_list") val pageList: List<String>,
    @SerialName("scramble_seed") val scrambleSeed: String,
    @SerialName("title_id") val titleId: Int,
    @SerialName("episode_id") val episodeId: Int,
)

@Serializable
data class BirthdayCookie(
    val value: String,
    val expires: Long,
)

@Serializable
data class LocalStorageAccount(
    val isLoggedIn: Boolean?,
    val checkedTicketExpiredList: List<CheckedTicketExpired>?,
)

@Serializable
data class CheckedTicketExpired(
    val userId: Int,
)
