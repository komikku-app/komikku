package eu.kanade.tachiyomi.data.database.resolvers

import androidx.core.content.contentValuesOf
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import com.pushtorefresh.storio.sqlite.operations.put.PutResolver
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.inTransactionReturn
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import exh.util.nullIfZero

class MangaInfoPutResolver(val reset: Boolean = false) : PutResolver<Manga>() {

    override fun performPut(db: StorIOSQLite, manga: Manga) = db.inTransactionReturn {
        val updateQuery = mapToUpdateQuery(manga)
        val contentValues = if (reset) resetToContentValues(manga) else mapToContentValues(manga)

        val numberOfRowsUpdated = db.lowLevel().update(updateQuery, contentValues)
        PutResult.newUpdateResult(numberOfRowsUpdated, updateQuery.table())
    }

    fun mapToUpdateQuery(manga: Manga) = UpdateQuery.builder()
        .table(MangaTable.TABLE)
        .where("${MangaTable.COL_ID} = ?")
        .whereArgs(manga.id)
        .build()

    fun mapToContentValues(manga: Manga) = contentValuesOf(
        MangaTable.COL_TITLE to manga.originalTitle,
        MangaTable.COL_GENRE to manga.originalGenre,
        MangaTable.COL_AUTHOR to manga.originalAuthor,
        MangaTable.COL_ARTIST to manga.originalArtist,
        MangaTable.COL_DESCRIPTION to manga.originalDescription,
        MangaTable.COL_STATUS to manga.originalStatus
    )

    private fun resetToContentValues(manga: Manga) = contentValuesOf(
        MangaTable.COL_TITLE to manga.title.split(splitter).last(),
        MangaTable.COL_GENRE to manga.genre?.split(splitter)?.lastOrNull(),
        MangaTable.COL_AUTHOR to manga.author?.split(splitter)?.lastOrNull(),
        MangaTable.COL_ARTIST to manga.artist?.split(splitter)?.lastOrNull(),
        MangaTable.COL_DESCRIPTION to manga.description?.split(splitter)?.lastOrNull(),
        MangaTable.COL_STATUS to manga.status.nullIfZero()?.toString()?.split(splitter)?.lastOrNull()
    )

    companion object {
        const val splitter = "▒ ▒∩▒"
    }
}
