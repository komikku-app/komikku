package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class ApiMangaSerializer(
    val data: DataSerializer,
    val status: String
)

@Serializable
data class DataSerializer(
    val manga: MangaSerializer,
    val chapters: List<ChapterSerializer>,
    val groups: List<GroupSerializer>,

)

@Serializable
data class MangaSerializer(
    val artist: List<String>,
    val author: List<String>,
    val mainCover: String,
    val description: String,
    val tags: List<Int>,
    val isHentai: Boolean,
    val lastChapter: String? = null,
    val publication: PublicationSerializer? = null,
    val links: LinksSerializer? = null,
    val rating: RatingSerializer? = null,
    val title: String
)

@Serializable
data class PublicationSerializer(
    val language: String? = null,
    val status: Int,
    val demographic: Int?

)

@Serializable
data class LinksSerializer(
    val al: String? = null,
    val amz: String? = null,
    val ap: String? = null,
    val engtl: String? = null,
    val kt: String? = null,
    val mal: String? = null,
    val mu: String? = null,
    val raw: String? = null
)

@Serializable
data class RatingSerializer(
    val bayesian: String? = null,
    val mean: String? = null,
    val users: String? = null
)

@Serializable
data class ChapterSerializer(
    val id: Long,
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val language: String,
    val groups: List<Long>,
    val timestamp: Long
)

@Serializable
data class GroupSerializer(
    val id: Long,
    val name: String? = null
)