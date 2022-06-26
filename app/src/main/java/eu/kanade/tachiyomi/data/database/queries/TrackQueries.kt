package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.tables.TrackTable

interface TrackQueries : DbProvider {

    fun getTracks() = db.get()
        .listOfObjects(Track::class.java)
        .withQuery(
            Query.builder()
                .table(TrackTable.TABLE)
                .build(),
        )
        .prepare()

    fun getTracks(mangaId: Long?) = db.get()
        .listOfObjects(Track::class.java)
        .withQuery(
            Query.builder()
                .table(TrackTable.TABLE)
                .where("${TrackTable.COL_MANGA_ID} = ?")
                .whereArgs(mangaId)
                .build(),
        )
        .prepare()

    fun insertTrack(track: Track) = db.put().`object`(track).prepare()

    fun insertTracks(tracks: List<Track>) = db.put().objects(tracks).prepare()
}
