package eu.kanade.tachiyomi.data.track.comick.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.comick.asHidToLong
import eu.kanade.tachiyomi.data.track.comick.fromApiListStatus
import kotlinx.serialization.Serializable

// KMK -->
@Serializable
data class ComickLibraryEntry(val data: ComickLibraryEntryItem) {
    fun toTrack(trackerId: Long): Track = Track.create(trackerId).apply {
        remote_id = data.hid.asHidToLong()
        title = data.title
        status = data.status.toLong().fromApiListStatus()
        last_chapter_read = data.progress?.number?.toDoubleOrNull() ?: 0.0
        score = data.rating?.toDouble() ?: 0.0
        // KMK --> Avoid treating a current release number as a final total.
        total_chapters = if (data.publicationStatus == 2) data.totalUnits?.toLong() ?: 0L else 0L
        // KMK <--
    }
}

@Serializable
data class ComickLibraryEntryItem(
    val hid: String,
    val title: String,
    val status: Int,
    val progress: ComickLibraryEntryProgress? = null,
    val rating: Int? = null,
    @kotlinx.serialization.SerialName("publication_status") val publicationStatus: Int? = null,
    @kotlinx.serialization.SerialName("total_units") val totalUnits: Int? = null,
)

@Serializable
data class ComickLibraryEntryProgress(val number: String? = null)
// KMK <--
