package exh.savedsearches.mappers

import android.database.Cursor
import androidx.core.content.contentValuesOf
import androidx.core.database.getStringOrNull
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import exh.savedsearches.models.SavedSearch
import exh.savedsearches.tables.SavedSearchTable.COL_FILTERS_JSON
import exh.savedsearches.tables.SavedSearchTable.COL_ID
import exh.savedsearches.tables.SavedSearchTable.COL_NAME
import exh.savedsearches.tables.SavedSearchTable.COL_QUERY
import exh.savedsearches.tables.SavedSearchTable.COL_SOURCE
import exh.savedsearches.tables.SavedSearchTable.TABLE

class SavedSearchTypeMapping : SQLiteTypeMapping<SavedSearch>(
    SavedSearchPutResolver(),
    SavedSearchGetResolver(),
    SavedSearchDeleteResolver()
)

class SavedSearchPutResolver : DefaultPutResolver<SavedSearch>() {

    override fun mapToInsertQuery(obj: SavedSearch) = InsertQuery.builder()
        .table(TABLE)
        .build()

    override fun mapToUpdateQuery(obj: SavedSearch) = UpdateQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()

    override fun mapToContentValues(obj: SavedSearch) = contentValuesOf(
        COL_ID to obj.id,
        COL_SOURCE to obj.source,
        COL_NAME to obj.name,
        COL_QUERY to obj.query,
        COL_FILTERS_JSON to obj.filtersJson
    )
}

class SavedSearchGetResolver : DefaultGetResolver<SavedSearch>() {

    override fun mapFromCursor(cursor: Cursor): SavedSearch = SavedSearch(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        source = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SOURCE)),
        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
        query = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COL_QUERY)),
        filtersJson = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COL_FILTERS_JSON))
    )
}

class SavedSearchDeleteResolver : DefaultDeleteResolver<SavedSearch>() {

    override fun mapToDeleteQuery(obj: SavedSearch) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
