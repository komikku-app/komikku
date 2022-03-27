package exh.savedsearches.mappers

import android.database.Cursor
import androidx.core.content.contentValuesOf
import androidx.core.database.getLongOrNull
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.tables.FeedSavedSearchTable.COL_ID
import exh.savedsearches.tables.FeedSavedSearchTable.COL_SAVED_SEARCH_ID
import exh.savedsearches.tables.FeedSavedSearchTable.COL_SOURCE
import exh.savedsearches.tables.FeedSavedSearchTable.TABLE

class FeedSavedSearchTypeMapping : SQLiteTypeMapping<FeedSavedSearch>(
    FeedSavedSearchPutResolver(),
    FeedSavedSearchGetResolver(),
    FeedSavedSearchDeleteResolver()
)

class FeedSavedSearchPutResolver : DefaultPutResolver<FeedSavedSearch>() {

    override fun mapToInsertQuery(obj: FeedSavedSearch) = InsertQuery.builder()
        .table(TABLE)
        .build()

    override fun mapToUpdateQuery(obj: FeedSavedSearch) = UpdateQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()

    override fun mapToContentValues(obj: FeedSavedSearch) = contentValuesOf(
        COL_ID to obj.id,
        COL_SOURCE to obj.source,
        COL_SAVED_SEARCH_ID to obj.savedSearch
    )
}

class FeedSavedSearchGetResolver : DefaultGetResolver<FeedSavedSearch>() {

    override fun mapFromCursor(cursor: Cursor): FeedSavedSearch = FeedSavedSearch(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        source = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SOURCE)),
        savedSearch = cursor.getLongOrNull(cursor.getColumnIndexOrThrow(COL_SAVED_SEARCH_ID))
    )
}

class FeedSavedSearchDeleteResolver : DefaultDeleteResolver<FeedSavedSearch>() {

    override fun mapToDeleteQuery(obj: FeedSavedSearch) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
