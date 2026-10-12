package exh.md.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KMangaViewerResponse(
    @SerialName("page_list") val pageList: List<String>,
    @SerialName("scramble_seed") val scrambleSeed: String,
    @SerialName("title_id") val titleId: Int,
    @SerialName("episode_id") val episodeId: Int,
)

@Serializable
data class KMangaBirthdayCookie(
    val value: String,
    val expires: Long,
)

@Serializable
data class KMangaLocalStorageAccount(
    val isLoggedIn: Boolean?,
    val checkedTicketExpiredList: List<KMangaCheckedTicketExpired>?,
)

@Serializable
data class KMangaCheckedTicketExpired(
    val userId: Int,
)
