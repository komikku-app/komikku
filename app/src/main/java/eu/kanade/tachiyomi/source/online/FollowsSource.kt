package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.utils.FollowStatus
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.coroutines.flow.Flow
import rx.Observable

interface FollowsSource {
    fun fetchFollows(): Observable<MangasPage>

    /**
     * Returns a list of all Follows retrieved by Coroutines
     *
     * @param SManga all smanga found for user
     */
    fun fetchAllFollows(forceHd: Boolean = false): Flow<List<Pair<SManga, RaisedSearchMetadata>>>

    /**
     * updates the follow status for a manga
     */
    fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Flow<Boolean>

    /**
     * Get a MdList Track of the manga
     */
    fun fetchTrackingInfo(url: String): Flow<Track>
}
