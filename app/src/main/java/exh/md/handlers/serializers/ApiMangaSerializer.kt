package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class ApiMangaSerializer(
    val chapter: Map<String, ChapterSerializer>? = null,
    val manga: MangaSerializer,
    val status: String
)

@Serializable
data class MangaSerializer(
    val artist: String,
    val author: String,
    val cover_url: String,
    val description: String,
    val genres: List<Int>,
    val hentai: Int,
    val lang_flag: String,
    val lang_name: String,
    val last_chapter: String? = null,
    val links: LinksSerializer? = null,
    val rating: RatingSerializer? = null,
    val status: Int,
    val title: String
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
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val lang_code: String,
    val group_id: Int? = null,
    val group_name: String? = null,
    val group_id_2: Int? = null,
    val group_name_2: String? = null,
    val group_id_3: Int? = null,
    val group_name_3: String? = null,
    val timestamp: Long
)

@Serializable
data class CoversResult(
    val covers: List<String> = emptyList(),
    val status: String
)

@Serializable
data class ImageReportResult(
    val url: String,
    val success: Boolean,
    val bytes: Int?
)
