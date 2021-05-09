package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.utils.FollowStatus
import exh.metadata.metadata.base.RaisedSearchMetadata

interface FollowsSource : CatalogueSource {
    suspend fun fetchFollows(page: Int): MangasPage

    /**
     * Returns a list of all Follows retrieved by Coroutines
     *
     * @param SManga all smanga found for user
     */
    suspend fun fetchAllFollows(): List<Pair<SManga, RaisedSearchMetadata>>

    /**
     * updates the follow status for a manga
     */
    suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean

    /**
     * Get a MdList Track of the manga
     */
    suspend fun fetchTrackingInfo(url: String): Track
}
