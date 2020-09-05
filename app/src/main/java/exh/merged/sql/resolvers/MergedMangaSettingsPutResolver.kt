package exh.merged.sql.resolvers

import android.content.ContentValues
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import com.pushtorefresh.storio.sqlite.operations.put.PutResolver
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.inTransactionReturn
import exh.merged.sql.models.MergedMangaReference
import exh.merged.sql.tables.MergedTable

class MergedMangaSettingsPutResolver(val reset: Boolean = false) : PutResolver<MergedMangaReference>() {

    override fun performPut(db: StorIOSQLite, mergedMangaReference: MergedMangaReference) = db.inTransactionReturn {
        val updateQuery = mapToUpdateQuery(mergedMangaReference)
        val contentValues = mapToContentValues(mergedMangaReference)

        val numberOfRowsUpdated = db.lowLevel().update(updateQuery, contentValues)
        PutResult.newUpdateResult(numberOfRowsUpdated, updateQuery.table())
    }

    fun mapToUpdateQuery(mergedMangaReference: MergedMangaReference) = UpdateQuery.builder()
        .table(MergedTable.TABLE)
        .where("${MergedTable.COL_ID} = ?")
        .whereArgs(mergedMangaReference.id)
        .build()

    fun mapToContentValues(mergedMangaReference: MergedMangaReference) = ContentValues(4).apply {
        put(MergedTable.COL_GET_CHAPTER_UPDATES, mergedMangaReference.getChapterUpdates)
        put(MergedTable.COL_DOWNLOAD_CHAPTERS, mergedMangaReference.downloadChapters)
        put(MergedTable.COL_IS_INFO_MANGA, mergedMangaReference.isInfoManga)
        put(MergedTable.COL_CHAPTER_PRIORITY, mergedMangaReference.chapterPriority)
    }
}
