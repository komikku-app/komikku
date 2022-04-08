package exh.savedsearches.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.queries.getGlobalFeedSavedSearchQuery
import eu.kanade.tachiyomi.data.database.queries.getSourceFeedSavedSearchQuery
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import exh.savedsearches.tables.FeedSavedSearchTable

interface FeedSavedSearchQueries : DbProvider {
    fun getGlobalFeedSavedSearches() = db.get()
        .listOfObjects(FeedSavedSearch::class.java)
        .withQuery(
            Query.builder()
                .table(FeedSavedSearchTable.TABLE)
                .where("${FeedSavedSearchTable.COL_GLOBAL} = 1")
                .orderBy(FeedSavedSearchTable.COL_ID)
                .build(),
        )
        .prepare()

    fun getSourceFeedSavedSearches(sourceId: Long) = db.get()
        .listOfObjects(FeedSavedSearch::class.java)
        .withQuery(
            Query.builder()
                .table(FeedSavedSearchTable.TABLE)
                .where("${FeedSavedSearchTable.COL_SOURCE} = ? AND ${FeedSavedSearchTable.COL_GLOBAL} = 0")
                .whereArgs(sourceId)
                .orderBy(FeedSavedSearchTable.COL_ID)
                .build(),
        )
        .prepare()

    fun insertFeedSavedSearch(savedSearch: FeedSavedSearch) = db.put().`object`(savedSearch).prepare()

    fun insertFeedSavedSearches(savedSearches: List<FeedSavedSearch>) = db.put().objects(savedSearches).prepare()

    fun deleteFeedSavedSearch(savedSearch: FeedSavedSearch) = db.delete().`object`(savedSearch).prepare()

    fun deleteFeedSavedSearch(id: Long) = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(FeedSavedSearchTable.TABLE)
                .where("${FeedSavedSearchTable.COL_ID} = ?")
                .whereArgs(id)
                .build(),
        ).prepare()

    fun deleteAllFeedSavedSearches() = db.delete().byQuery(
        DeleteQuery.builder()
            .table(FeedSavedSearchTable.TABLE)
            .build(),
    )
        .prepare()

    fun getGlobalSavedSearchesFeed() = db.get()
        .listOfObjects(SavedSearch::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getGlobalFeedSavedSearchQuery())
                .build(),
        )
        .prepare()

    fun getSourceSavedSearchesFeed(sourceId: Long) = db.get()
        .listOfObjects(SavedSearch::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getSourceFeedSavedSearchQuery())
                .args(sourceId)
                .build(),
        )
        .prepare()

    /*fun setMangasForMergedManga(mergedMangaId: Long, mergedMangases: List<SavedSearch>) {
        db.inTransaction {
            deleteSavedSearches(mergedMangaId).executeAsBlocking()
            mergedMangases.chunked(100) { chunk ->
                insertSavedSearches(chunk).executeAsBlocking()
            }
        }
    }*/
}
