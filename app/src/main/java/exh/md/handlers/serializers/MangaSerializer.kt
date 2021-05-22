package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class MangaListResponse(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val results: List<MangaResponse>
)

@Serializable
data class MangaResponse(
    val result: String,
    val data: NetworkManga,
    val relationships: List<Relationships>
)

@Serializable
data class NetworkManga(val id: String, val type: String, val attributes: NetworkMangaAttributes)

@Serializable
data class NetworkMangaAttributes(
    val title: Map<String, String>,
    val altTitles: List<Map<String, String>>,
    val description: Map<String, String>,
    val links: Map<String, String>?,
    val originalLanguage: String,
    val lastVolume: String?,
    val lastChapter: String,
    val contentRating: String?,
    val publicationDemographic: String?,
    val status: String?,
    val year: Int?,
    val tags: List<TagsSerializer>
)

@Serializable
data class TagsSerializer(
    val id: String,
    val attributes: TagAttributes
)

@Serializable
data class TagAttributes(
    val name: Map<String, String>
)

@Serializable
data class Relationships(
    val id: String,
    val type: String,
)

@Serializable
data class AuthorResponseList(
    val results: List<AuthorResponse>,
)

@Serializable
data class AuthorResponse(
    val result: String,
    val data: NetworkAuthor,
)

@Serializable
data class NetworkAuthor(
    val id: String,
    val attributes: AuthorAttributes,
)

@Serializable
data class AuthorAttributes(
    val name: String,
)

@Serializable
data class UpdateReadingStatus(
    val status: String?
)

@Serializable
data class MangaStatusResponse(
    val status: String?
)

@Serializable
data class MangaStatusListResponse(
    val statuses: Map<String, String?>
)

@Serializable
data class CoverListResponse(
    val results: List<CoverResponse>,
)

@Serializable
data class CoverResponse(
    val data: Cover,
)

@Serializable
data class Cover(
    val attributes: CoverAttributes,
)

@Serializable
data class CoverAttributes(
    val fileName: String,
)
