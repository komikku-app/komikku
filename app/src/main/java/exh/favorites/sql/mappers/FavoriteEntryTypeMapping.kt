package exh.favorites.sql.mappers

import android.database.Cursor
import androidx.core.content.contentValuesOf
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import exh.favorites.sql.models.FavoriteEntry
import exh.favorites.sql.tables.FavoriteEntryTable.COL_CATEGORY
import exh.favorites.sql.tables.FavoriteEntryTable.COL_GID
import exh.favorites.sql.tables.FavoriteEntryTable.COL_ID
import exh.favorites.sql.tables.FavoriteEntryTable.COL_TITLE
import exh.favorites.sql.tables.FavoriteEntryTable.COL_TOKEN
import exh.favorites.sql.tables.FavoriteEntryTable.TABLE

class FavoriteEntryTypeMapping : SQLiteTypeMapping<FavoriteEntry>(
    FavoriteEntryPutResolver(),
    FavoriteEntryGetResolver(),
    FavoriteEntryDeleteResolver()
)

class FavoriteEntryPutResolver : DefaultPutResolver<FavoriteEntry>() {

    override fun mapToInsertQuery(obj: FavoriteEntry) = InsertQuery.builder()
        .table(TABLE)
        .build()

    override fun mapToUpdateQuery(obj: FavoriteEntry) = UpdateQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()

    override fun mapToContentValues(obj: FavoriteEntry) = contentValuesOf(
        COL_ID to obj.id,
        COL_TITLE to obj.title,
        COL_GID to obj.gid,
        COL_TOKEN to obj.token,
        COL_CATEGORY to obj.category
    )
}

class FavoriteEntryGetResolver : DefaultGetResolver<FavoriteEntry>() {

    override fun mapFromCursor(cursor: Cursor): FavoriteEntry = FavoriteEntry(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
        gid = cursor.getString(cursor.getColumnIndexOrThrow(COL_GID)),
        token = cursor.getString(cursor.getColumnIndexOrThrow(COL_TOKEN)),
        category = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CATEGORY))
    )
}

class FavoriteEntryDeleteResolver : DefaultDeleteResolver<FavoriteEntry>() {

    override fun mapToDeleteQuery(obj: FavoriteEntry) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
