package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// KMK -->
private val TITLE_PRIORITIES = listOf("en", "ja-Latn", "ja", "ko-Latn", "ko", "zh-Latn", "zh")

@Serializable data class MangaBakaSearchResult(val data: List<MangaBakaItem>)

@Serializable data class MangaBakaItemResult(val data: MangaBakaItem)

@Serializable data class MangaBakaItem(
    val id: Long,
    val cover: MangaBakaCover,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val description: String? = null,
    val published: MangaBakaPublished,
    val status: String,
    val type: String,
    val rating: Double? = null,
    val titles: List<MangaBakaTitle>? = null,
) {
    fun bestTitle(): String = titles.orEmpty().let { all ->
        TITLE_PRIORITIES.firstNotNullOfOrNull { language ->
            all.filter { it.language == language }.minByOrNull {
                when {
                    it.isPrimary -> 0
                    "official" in it.traits -> 1
                    "native" in it.traits -> 2
                    else -> 3
                }
            }?.title
        } ?: all.firstOrNull()?.title ?: "ID: $id - Could not find name!"
    }
}

@Serializable data class MangaBakaCover(val x250: MangaBakaScaledCover)

@Serializable data class MangaBakaScaledCover(val x1: String? = null)

@Serializable data class MangaBakaPublished(@SerialName("start_date") val startDate: String? = null)

@Serializable data class MangaBakaTitle(val language: String, val traits: List<String> = emptyList(), val title: String, @SerialName("is_primary") val isPrimary: Boolean = false)

@Serializable data class MangaBakaLibraryEntry(
    val state: String,
    @SerialName("progress_chapter") val progressChapter: Double? = null,
    val rating: Int? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("finish_date") val finishDate: String? = null,
)

@Serializable data class MangaBakaLibraryResult(val data: MangaBakaLibraryEntry)

@Serializable data class MangaBakaUserProfileResponse(val data: MangaBakaUserProfile)

@Serializable data class MangaBakaUserProfile(
    val id: String,
    val nickname: String? = null,
    @SerialName("preferred_username") val preferredUsername: String? = null,
    @SerialName("rating_steps") val ratingSteps: Int = 1,
)

@Serializable data class MangaBakaOAuth(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
) {
    fun isExpired() = System.currentTimeMillis() / 1000 >= expiresAt - 60
}
// KMK <--
