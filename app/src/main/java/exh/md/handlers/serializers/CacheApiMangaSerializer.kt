package exh.md.handlers.serializers

import kotlinx.serialization.Serializable

@Serializable
data class CacheApiMangaSerializer(
    val id: Long,
    val title: String,
    val url: String,
    val description: String,
    val is_r18: Boolean,
    val rating: Float,
    val demographic: List<String>,
    val content: List<String>,
    val format: List<String>,
    val genre: List<String>,
    val theme: List<String>,
    val languages: List<String>,
    val related: List<CacheRelatedSerializer>,
    val external: MutableMap<String, String>,
    val last_updated: String,
    val matches: List<CacheSimilarMatchesSerializer>,
)

@Serializable
data class CacheRelatedSerializer(
    val id: Long,
    val title: String,
    val type: String,
    val r18: Boolean,
)

@Serializable
data class CacheSimilarMatchesSerializer(
    val id: Long,
    val title: String,
    val score: Float,
    val r18: Boolean,
    val languages: List<String>,
)
