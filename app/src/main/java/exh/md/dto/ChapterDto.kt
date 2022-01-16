package exh.md.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChapterListDto(
    override val limit: Int,
    override val offset: Int,
    override val total: Int,
    override val data: List<ChapterDataDto>,
) : ListCallDto<ChapterDataDto>

@Serializable
data class ChapterDto(
    val result: String,
    val data: ChapterDataDto,
)

@Serializable
data class ChapterDataDto(
    val id: String,
    val type: String,
    val attributes: ChapterAttributesDto,
    val relationships: List<RelationshipDto>,
)

@Serializable
data class ChapterAttributesDto(
    val title: String?,
    val volume: String?,
    val chapter: String?,
    val translatedLanguage: String,
    val externalUrl: String?,
    val pages: Int,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val publishAt: String,
)

@Serializable
data class GroupListDto(
    override val limit: Int,
    override val offset: Int,
    override val total: Int,
    override val data: List<GroupDataDto>,
) : ListCallDto<GroupDataDto>

@Serializable
data class GroupDto(
    val result: String,
    val data: GroupDataDto,
)

@Serializable
data class GroupDataDto(
    val id: String,
    val attributes: GroupAttributesDto,
)

@Serializable
data class GroupAttributesDto(
    val name: String,
)
