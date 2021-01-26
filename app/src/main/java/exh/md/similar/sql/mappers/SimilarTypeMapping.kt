package exh.md.similar.sql.mappers

import android.database.Cursor
import androidx.core.content.contentValuesOf
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import exh.md.similar.sql.models.MangaSimilar
import exh.md.similar.sql.models.MangaSimilarImpl
import exh.md.similar.sql.tables.SimilarTable.COL_ID
import exh.md.similar.sql.tables.SimilarTable.COL_MANGA_ID
import exh.md.similar.sql.tables.SimilarTable.COL_MANGA_SIMILAR_MATCHED_IDS
import exh.md.similar.sql.tables.SimilarTable.COL_MANGA_SIMILAR_MATCHED_TITLES
import exh.md.similar.sql.tables.SimilarTable.TABLE

class SimilarTypeMapping : SQLiteTypeMapping<MangaSimilar>(
    SimilarPutResolver(),
    SimilarGetResolver(),
    SimilarDeleteResolver()
)

class SimilarPutResolver : DefaultPutResolver<MangaSimilar>() {

    override fun mapToInsertQuery(obj: MangaSimilar) = InsertQuery.builder()
        .table(TABLE)
        .build()

    override fun mapToUpdateQuery(obj: MangaSimilar) = UpdateQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()

    override fun mapToContentValues(obj: MangaSimilar) = contentValuesOf(
        COL_ID to obj.id,
        COL_MANGA_ID to obj.manga_id,
        COL_MANGA_SIMILAR_MATCHED_IDS to obj.matched_ids,
        COL_MANGA_SIMILAR_MATCHED_TITLES to obj.matched_titles
    )
}

class SimilarGetResolver : DefaultGetResolver<MangaSimilar>() {

    override fun mapFromCursor(cursor: Cursor): MangaSimilar = MangaSimilarImpl().apply {
        id = cursor.getLong(cursor.getColumnIndex(COL_ID))
        manga_id = cursor.getLong(cursor.getColumnIndex(COL_MANGA_ID))
        matched_ids = cursor.getString(cursor.getColumnIndex(COL_MANGA_SIMILAR_MATCHED_IDS))
        matched_titles = cursor.getString(cursor.getColumnIndex(COL_MANGA_SIMILAR_MATCHED_TITLES))
    }
}

class SimilarDeleteResolver : DefaultDeleteResolver<MangaSimilar>() {

    override fun mapToDeleteQuery(obj: MangaSimilar) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
