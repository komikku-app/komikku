package exh.favorites.sql.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import exh.favorites.sql.models.FavoriteEntry
import exh.favorites.sql.tables.FavoriteEntryTable

interface FavoriteEntryQueries : DbProvider {
    fun getFavoriteEntries() = db.get()
        .listOfObjects(FavoriteEntry::class.java)
        .withQuery(
            Query.builder()
                .table(FavoriteEntryTable.TABLE)
                .build()
        )
        .prepare()

    fun insertFavoriteEntries(favoriteEntries: List<FavoriteEntry>) = db.put()
        .objects(favoriteEntries)
        .prepare()

    fun deleteAllFavoriteEntries() = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(FavoriteEntryTable.TABLE)
                .build()
        )
        .prepare()
}
