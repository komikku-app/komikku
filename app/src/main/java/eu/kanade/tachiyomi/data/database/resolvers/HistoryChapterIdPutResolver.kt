package eu.kanade.tachiyomi.data.database.resolvers

import androidx.core.content.contentValuesOf
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import com.pushtorefresh.storio.sqlite.operations.put.PutResolver
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.inTransactionReturn
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.tables.HistoryTable

class HistoryChapterIdPutResolver : PutResolver<History>() {

    override fun performPut(db: StorIOSQLite, history: History) = db.inTransactionReturn {
        val updateQuery = mapToUpdateQuery(history)
        val contentValues = mapToContentValues(history)

        val numberOfRowsUpdated = db.lowLevel().update(updateQuery, contentValues)
        PutResult.newUpdateResult(numberOfRowsUpdated, updateQuery.table())
    }

    fun mapToUpdateQuery(history: History) = UpdateQuery.builder()
        .table(HistoryTable.TABLE)
        .where("${HistoryTable.COL_ID} = ?")
        .whereArgs(history.id)
        .build()

    fun mapToContentValues(history: History) =
        contentValuesOf(
            HistoryTable.COL_CHAPTER_ID to history.chapter_id,
        )
}
