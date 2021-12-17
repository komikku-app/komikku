package exh.merged.sql.mappers

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
import exh.merged.sql.models.MergedMangaReference
import exh.merged.sql.tables.MergedTable.COL_CHAPTER_PRIORITY
import exh.merged.sql.tables.MergedTable.COL_CHAPTER_SORT_MODE
import exh.merged.sql.tables.MergedTable.COL_DOWNLOAD_CHAPTERS
import exh.merged.sql.tables.MergedTable.COL_GET_CHAPTER_UPDATES
import exh.merged.sql.tables.MergedTable.COL_ID
import exh.merged.sql.tables.MergedTable.COL_IS_INFO_MANGA
import exh.merged.sql.tables.MergedTable.COL_MANGA_ID
import exh.merged.sql.tables.MergedTable.COL_MANGA_SOURCE
import exh.merged.sql.tables.MergedTable.COL_MANGA_URL
import exh.merged.sql.tables.MergedTable.COL_MERGE_ID
import exh.merged.sql.tables.MergedTable.COL_MERGE_URL
import exh.merged.sql.tables.MergedTable.TABLE

class MergedMangaTypeMapping : SQLiteTypeMapping<MergedMangaReference>(
    MergedMangaPutResolver(),
    MergedMangaGetResolver(),
    MergedMangaDeleteResolver()
)

class MergedMangaPutResolver : DefaultPutResolver<MergedMangaReference>() {

    override fun mapToInsertQuery(obj: MergedMangaReference) = InsertQuery.builder()
        .table(TABLE)
        .build()

    override fun mapToUpdateQuery(obj: MergedMangaReference) = UpdateQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()

    override fun mapToContentValues(obj: MergedMangaReference) = contentValuesOf(
        COL_ID to obj.id,
        COL_IS_INFO_MANGA to obj.isInfoManga,
        COL_GET_CHAPTER_UPDATES to obj.getChapterUpdates,
        COL_CHAPTER_SORT_MODE to obj.chapterSortMode,
        COL_CHAPTER_PRIORITY to obj.chapterPriority,
        COL_DOWNLOAD_CHAPTERS to obj.downloadChapters,
        COL_MERGE_ID to obj.mergeId,
        COL_MERGE_URL to obj.mergeUrl,
        COL_MANGA_ID to obj.mangaId,
        COL_MANGA_URL to obj.mangaUrl,
        COL_MANGA_SOURCE to obj.mangaSourceId
    )
}

class MergedMangaGetResolver : DefaultGetResolver<MergedMangaReference>() {

    override fun mapFromCursor(cursor: Cursor): MergedMangaReference = MergedMangaReference(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        isInfoManga = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_INFO_MANGA)) == 1,
        getChapterUpdates = cursor.getInt(cursor.getColumnIndexOrThrow(COL_GET_CHAPTER_UPDATES)) == 1,
        chapterSortMode = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAPTER_SORT_MODE)),
        chapterPriority = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAPTER_PRIORITY)),
        downloadChapters = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DOWNLOAD_CHAPTERS)) == 1,
        mergeId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MERGE_ID)),
        mergeUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_MERGE_URL)),
        mangaId = cursor.getLongOrNull(cursor.getColumnIndexOrThrow(COL_MANGA_ID)),
        mangaUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_MANGA_URL)),
        mangaSourceId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MANGA_SOURCE))
    )
}

class MergedMangaDeleteResolver : DefaultDeleteResolver<MergedMangaReference>() {

    override fun mapToDeleteQuery(obj: MergedMangaReference) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
