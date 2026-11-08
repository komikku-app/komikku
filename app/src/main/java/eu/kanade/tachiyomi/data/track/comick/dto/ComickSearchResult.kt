package eu.kanade.tachiyomi.data.track.comick.dto

import eu.kanade.tachiyomi.data.track.comick.asHidToLong
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// KMK -->
@Serializable
data class ComickSearchResult(val data: List<ComickSearchItem>)

@Serializable
data class ComickTitleLookupResult(val data: ComickSearchItem)

@Serializable
data class ComickSearchItem(
    val hid: String,
    val title: String,
    val year: Int? = null,
    val description: String? = null,
    val status: Int? = null,
    @SerialName("bayesian_rating") val bayesianRating: Double? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val country: String? = null,
    val url: String,
) {
    fun toTrackSearch(trackerId: Long): TrackSearch = TrackSearch.create(trackerId).apply {
        remote_id = hid.asHidToLong()
        title = this@ComickSearchItem.title
        tracking_url = url
        summary = description.orEmpty()
        start_date = year?.toString().orEmpty()
        score = bayesianRating ?: -1.0
        cover_url = coverUrl.orEmpty()
        publishing_status = when (this@ComickSearchItem.status) {
            null -> ""
            1 -> "Ongoing"
            2 -> "Completed"
            3 -> "Cancelled"
            4 -> "Hiatus"
            else -> ""
        }
        publishing_type = when (country) {
            "kr" -> "Manhwa"
            "cn" -> "Manhua"
            "fr" -> "Manfra"
            else -> "Manga"
        }
    }
}
// KMK <--
