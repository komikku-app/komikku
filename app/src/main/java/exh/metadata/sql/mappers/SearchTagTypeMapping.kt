package exh.metadata.sql.mappers

import android.database.Cursor
import androidx.core.content.contentValuesOf
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.tables.SearchTagTable.COL_ID
import exh.metadata.sql.tables.SearchTagTable.COL_MANGA_ID
import exh.metadata.sql.tables.SearchTagTable.COL_NAME
import exh.metadata.sql.tables.SearchTagTable.COL_NAMESPACE
import exh.metadata.sql.tables.SearchTagTable.COL_TYPE
import exh.metadata.sql.tables.SearchTagTable.TABLE

class SearchTagTypeMapping : SQLiteTypeMapping<SearchTag>(
    SearchTagPutResolver(),
    SearchTagGetResolver(),
    SearchTagDeleteResolver()
)

class SearchTagPutResolver : DefaultPutResolver<SearchTag>() {

    override fun mapToInsertQuery(obj: SearchTag) = InsertQuery.builder()
        .table(TABLE)
        .build()

    override fun mapToUpdateQuery(obj: SearchTag) = UpdateQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()

    override fun mapToContentValues(obj: SearchTag) = contentValuesOf(
        COL_ID to obj.id,
        COL_MANGA_ID to obj.mangaId,
        COL_NAMESPACE to obj.namespace,
        COL_NAME to obj.name,
        COL_TYPE to obj.type,
    )
}

class SearchTagGetResolver : DefaultGetResolver<SearchTag>() {

    override fun mapFromCursor(cursor: Cursor): SearchTag = SearchTag(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        mangaId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MANGA_ID)),
        namespace = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAMESPACE)),
        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
        type = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TYPE))
    )
}

class SearchTagDeleteResolver : DefaultDeleteResolver<SearchTag>() {

    override fun mapToDeleteQuery(obj: SearchTag) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
