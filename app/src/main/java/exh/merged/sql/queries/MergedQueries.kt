package exh.merged.sql.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.queries.getMergedMangaForDownloadingQuery
import eu.kanade.tachiyomi.data.database.queries.getMergedMangaQuery
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
                .build(),
        )
        .prepare()

    fun deleteMangaForMergedManga(mergedMangaId: Long) = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(MergedTable.TABLE)
                .where("${MergedTable.COL_MERGE_ID} = ?")
                .whereArgs(mergedMangaId)
                .build(),
        )
        .prepare()

    fun getMergedMangas(mergedMangaId: Long) = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getMergedMangaQuery())
                .args(mergedMangaId)
                .build(),
        )
        .prepare()

    fun getMergedMangasForDownloading(mergedMangaId: Long) = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getMergedMangaForDownloadingQuery())
                .args(mergedMangaId)
                .build(),
        )
        .prepare()

    fun insertMergedManga(mergedManga: MergedMangaReference) = db.put().`object`(mergedManga).prepare()

    fun insertNewMergedMangaId(mergedManga: MergedMangaReference) = db.put().`object`(mergedManga).withPutResolver(MergedMangaIdPutResolver()).prepare()

    fun insertMergedMangas(mergedManga: List<MergedMangaReference>) = db.put().objects(mergedManga).prepare()

    fun updateMergedMangaSettings(mergedManga: List<MergedMangaReference>) = db.put().objects(mergedManga).withPutResolver(MergedMangaSettingsPutResolver()).prepare()

    fun updateMergeMangaSettings(mergeManga: MergedMangaReference) = db.put().`object`(mergeManga).withPutResolver(MergeMangaSettingsPutResolver()).prepare()

    fun deleteMergedManga(mergedManga: MergedMangaReference) = db.delete().`object`(mergedManga).prepare()
}
