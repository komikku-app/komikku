package exh.merged.sql.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.inTransaction
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.queries.getAllMergedMangaQuery
import eu.kanade.tachiyomi.data.database.queries.getMergedChaptersQuery
import eu.kanade.tachiyomi.data.database.queries.getMergedMangaFromUrlQuery
import eu.kanade.tachiyomi.data.database.queries.getMergedMangaQuery
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import exh.merged.sql.models.MergedMangaReference
import exh.merged.sql.resolvers.MergeMangaSettingsPutResolver
import exh.merged.sql.resolvers.MergedMangaIdPutResolver
import exh.merged.sql.resolvers.MergedMangaSettingsPutResolver
import exh.merged.sql.tables.MergedTable

interface MergedQueries : DbProvider {
    fun getMergedMangaReferences(mergedMangaId: Long) = db.get()
        .listOfObjects(MergedMangaReference::class.java)
        .withQuery(
            Query.builder()
                .table(MergedTable.TABLE)
                .where("${MergedTable.COL_MERGE_ID} = ?")
                .whereArgs(mergedMangaId)
                .build()
        )
        .prepare()

    fun getMergedMangaReferences(mergedMangaUrl: String) = db.get()
        .listOfObjects(MergedMangaReference::class.java)
        .withQuery(
            Query.builder()
                .table(MergedTable.TABLE)
                .where("${MergedTable.COL_MERGE_URL} = ?")
                .whereArgs(mergedMangaUrl)
                .build()
        )
        .prepare()

    fun deleteMangaForMergedManga(mergedMangaId: Long) = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(MergedTable.TABLE)
                .where("${MergedTable.COL_MERGE_ID} = ?")
                .whereArgs(mergedMangaId)
                .build()
        )
        .prepare()

    fun getMergedMangas(mergedMangaId: Long) = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getMergedMangaQuery())
                .args(mergedMangaId)
                .build()
        )
        .prepare()

    fun getMergedMangas(mergedMangaUrl: String) = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getMergedMangaFromUrlQuery())
                .args(mergedMangaUrl)
                .build()
        )
        .prepare()

    fun getMergedMangas() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getAllMergedMangaQuery())
                .build()
        )
        .prepare()

    fun deleteMangaForMergedManga(mergedMangaUrl: String) = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(MergedTable.TABLE)
                .where("${MergedTable.COL_MERGE_URL} = ?")
                .whereArgs(mergedMangaUrl)
                .build()
        )
        .prepare()

    fun getMergedMangaReferences() = db.get()
        .listOfObjects(MergedMangaReference::class.java)
        .withQuery(
            Query.builder()
                .table(MergedTable.TABLE)
                .orderBy(MergedTable.COL_ID)
                .build()
        )
        .prepare()

    fun getChaptersByMergedMangaId(mergedMangaId: Long) = db.get()
        .listOfObjects(Chapter::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getMergedChaptersQuery())
                .args(mergedMangaId)
                .observesTables(ChapterTable.TABLE, MergedTable.TABLE)
                .build()
        )
        .prepare()

    fun insertMergedManga(mergedManga: MergedMangaReference) = db.put().`object`(mergedManga).prepare()

    fun insertNewMergedMangaId(mergedManga: MergedMangaReference) = db.put().`object`(mergedManga).withPutResolver(MergedMangaIdPutResolver()).prepare()

    fun insertMergedMangas(mergedManga: List<MergedMangaReference>) = db.put().objects(mergedManga).prepare()

    fun updateMergedMangaSettings(mergedManga: List<MergedMangaReference>) = db.put().objects(mergedManga).withPutResolver(MergedMangaSettingsPutResolver()).prepare()

    fun updateMergeMangaSettings(mergeManga: MergedMangaReference) = db.put().`object`(mergeManga).withPutResolver(MergeMangaSettingsPutResolver()).prepare()

    fun deleteMergedManga(mergedManga: MergedMangaReference) = db.delete().`object`(mergedManga).prepare()

    fun deleteAllMergedManga() = db.delete().byQuery(
        DeleteQuery.builder()
            .table(MergedTable.TABLE)
            .build()
    )
        .prepare()

    fun setMangasForMergedManga(mergedMangaId: Long, mergedMangas: List<MergedMangaReference>) {
        db.inTransaction {
            deleteMangaForMergedManga(mergedMangaId).executeAsBlocking()
            mergedMangas.chunked(100) { chunk ->
                insertMergedMangas(chunk).executeAsBlocking()
            }
        }
    }
}
