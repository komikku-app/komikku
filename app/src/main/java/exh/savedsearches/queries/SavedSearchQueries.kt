package exh.savedsearches.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import exh.savedsearches.models.SavedSearch
import exh.savedsearches.tables.SavedSearchTable

interface SavedSearchQueries : DbProvider {
    fun getSavedSearches(source: Long) = db.get()
        .listOfObjects(SavedSearch::class.java)
        .withQuery(
            Query.builder()
                .table(SavedSearchTable.TABLE)
                .where("${SavedSearchTable.COL_SOURCE} = ?")
                .whereArgs(source)
                .build(),
        )
        .prepare()

    fun deleteSavedSearches(source: Long) = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(SavedSearchTable.TABLE)
                .where("${SavedSearchTable.COL_SOURCE} = ?")
                .whereArgs(source)
                .build(),
        )
        .prepare()

    fun getSavedSearches() = db.get()
        .listOfObjects(SavedSearch::class.java)
        .withQuery(
            Query.builder()
                .table(SavedSearchTable.TABLE)
                .orderBy(SavedSearchTable.COL_ID)
                .build(),
        )
        .prepare()

    fun getSavedSearch(id: Long) = db.get()
        .`object`(SavedSearch::class.java)
        .withQuery(
            Query.builder()
                .table(SavedSearchTable.TABLE)
                .where("${SavedSearchTable.COL_ID} = ?")
                .whereArgs(id)
                .build(),
        )
        .prepare()

    fun getSavedSearches(ids: List<Long>) = db.get()
        .listOfObjects(SavedSearch::class.java)
        .withQuery(
            Query.builder()
                .table(SavedSearchTable.TABLE)
                .where("${SavedSearchTable.COL_ID} IN (?)")
                .whereArgs(ids.joinToString())
                .build(),
        )
        .prepare()

    fun insertSavedSearch(savedSearch: SavedSearch) = db.put().`object`(savedSearch).prepare()

    fun insertSavedSearches(savedSearches: List<SavedSearch>) = db.put().objects(savedSearches).prepare()

    fun deleteSavedSearch(savedSearch: SavedSearch) = db.delete().`object`(savedSearch).prepare()

    fun deleteSavedSearch(id: Long) = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(SavedSearchTable.TABLE)
                .where("${SavedSearchTable.COL_ID} = ?")
                .whereArgs(id)
                .build(),
        ).prepare()

    fun deleteAllSavedSearches() = db.delete().byQuery(
        DeleteQuery.builder()
            .table(SavedSearchTable.TABLE)
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
