package exh.merged.sql.mappers

import android.content.ContentValues
import android.database.Cursor
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

    override fun mapToContentValues(obj: MergedMangaReference) = ContentValues(5).apply {
        put(COL_ID, obj.id)
        put(COL_IS_INFO_MANGA, obj.isInfoManga)
        put(COL_GET_CHAPTER_UPDATES, obj.getChapterUpdates)
        put(COL_CHAPTER_SORT_MODE, obj.chapterSortMode)
        put(COL_CHAPTER_PRIORITY, obj.chapterPriority)
        put(COL_DOWNLOAD_CHAPTERS, obj.downloadChapters)
        put(COL_MERGE_ID, obj.mergeId)
        put(COL_MERGE_URL, obj.mergeUrl)
        put(COL_MANGA_ID, obj.mangaId)
        put(COL_MANGA_URL, obj.mangaUrl)
        put(COL_MANGA_SOURCE, obj.mangaSourceId)
    }
}

class MergedMangaGetResolver : DefaultGetResolver<MergedMangaReference>() {

    override fun mapFromCursor(cursor: Cursor): MergedMangaReference = MergedMangaReference(
        id = cursor.getLong(cursor.getColumnIndex(COL_ID)),
        isInfoManga = cursor.getInt(cursor.getColumnIndex(COL_IS_INFO_MANGA)) == 1,
        getChapterUpdates = cursor.getInt(cursor.getColumnIndex(COL_GET_CHAPTER_UPDATES)) == 1,
        chapterSortMode = cursor.getInt(cursor.getColumnIndex(COL_CHAPTER_SORT_MODE)),
        chapterPriority = cursor.getInt(cursor.getColumnIndex(COL_CHAPTER_PRIORITY)),
        downloadChapters = cursor.getInt(cursor.getColumnIndex(COL_DOWNLOAD_CHAPTERS)) == 1,
        mergeId = cursor.getLong(cursor.getColumnIndex(COL_MERGE_ID)),
        mergeUrl = cursor.getString(cursor.getColumnIndex(COL_MERGE_URL)),
        mangaId = cursor.getLongOrNull(cursor.getColumnIndex(COL_MANGA_ID)),
        mangaUrl = cursor.getString(cursor.getColumnIndex(COL_MANGA_URL)),
        mangaSourceId = cursor.getLong(cursor.getColumnIndex(COL_MANGA_SOURCE))
    )
}

class MergedMangaDeleteResolver : DefaultDeleteResolver<MergedMangaReference>() {

    override fun mapToDeleteQuery(obj: MergedMangaReference) = DeleteQuery.builder()
        .table(TABLE)
        .where("$COL_ID = ?")
        .whereArgs(obj.id)
        .build()
}
