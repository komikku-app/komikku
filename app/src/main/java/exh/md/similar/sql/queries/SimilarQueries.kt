package exh.md.similar.sql.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import exh.md.similar.sql.models.MangaSimilar
import exh.md.similar.sql.tables.SimilarTable

interface SimilarQueries : DbProvider {

    fun getAllSimilar() = db.get()
        .listOfObjects(MangaSimilar::class.java)
        .withQuery(
            Query.builder()
                .table(SimilarTable.TABLE)
                .build()
        )
        .prepare()

    fun getSimilar(manga_id: Long) = db.get()
        .`object`(MangaSimilar::class.java)
        .withQuery(
            Query.builder()
                .table(SimilarTable.TABLE)
                .where("${SimilarTable.COL_MANGA_ID} = ?")
                .whereArgs(manga_id)
                .build()
        )
        .prepare()

    fun insertSimilar(similar: MangaSimilar) = db.put().`object`(similar).prepare()

    fun insertSimilar(similarList: List<MangaSimilar>) = db.put().objects(similarList).prepare()

    fun deleteAllSimilar() = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(SimilarTable.TABLE)
                .build()
        )
        .prepare()
}
