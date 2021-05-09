package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class ChapterListResponse(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val results: List<ChapterResponse>
)

@Serializable
data class ChapterResponse(
    val result: String,
    val data: NetworkChapter,
    val relationships: List<Relationships>
)

@Serializable
data class NetworkChapter(
    val id: String,
    val type: String,
    val attributes: ChapterAttributes,
)

@Serializable
data class ChapterAttributes(
    val title: String?,
    val volume: String?,
    val chapter: String?,
    val translatedLanguage: String,
    val publishAt: String,
    val data: List<String>,
    val dataSaver: List<String>,
    val hash: String,
)

@Serializable
data class AtHomeResponse(
    val baseUrl: String
)

@Serializable
data class GroupListResponse(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val results: List<GroupResponse>
)

@Serializable
data class GroupResponse(
    val result: String,
    val data: GroupData,
)

@Serializable
data class GroupData(
    val id: String,
    val attributes: GroupAttributes,
)

@Serializable
data class GroupAttributes(
    val name: String,
)
