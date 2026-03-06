package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class MALUserListResult(
    val data: List<MALUserListNode>,
    val paging: MALSearchPaging,
)

@Serializable
data class MALUserListNode(
    val node: MALManga,
    @JsonNames("list_status", "my_list_status")
    @SerialName("list_status")
    val listStatus: MALListItemStatus? = null,
)
