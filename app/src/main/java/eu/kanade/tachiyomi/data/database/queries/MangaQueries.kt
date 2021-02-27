package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.resolvers.LibraryMangaGetResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaCoverLastModifiedPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaFavoritePutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaFlagsPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaInfoPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaLastUpdatedPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaMigrationPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaThumbnailPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaTitlePutResolver
import eu.kanade.tachiyomi.data.database.resolvers.MangaViewerPutResolver
import eu.kanade.tachiyomi.data.database.tables.CategoryTable
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import exh.merged.sql.tables.MergedTable
import exh.metadata.sql.tables.SearchMetadataTable

interface MangaQueries : DbProvider {

    fun getMangas() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            Query.builder()
                .table(MangaTable.TABLE)
                .build()
        )
        .prepare()

    fun getLibraryMangas() = db.get()
        .listOfObjects(LibraryManga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(libraryQuery)
                .observesTables(MangaTable.TABLE, ChapterTable.TABLE, MangaCategoryTable.TABLE, CategoryTable.TABLE)
                .build()
        )
        .withGetResolver(LibraryMangaGetResolver.INSTANCE)
        .prepare()

    fun getFavoriteMangas() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            Query.builder()
                .table(MangaTable.TABLE)
                .where("${MangaTable.COL_FAVORITE} = ?")
                .whereArgs(1)
                .orderBy(MangaTable.COL_TITLE)
                .build()
        )
        .prepare()

    fun getManga(url: String, sourceId: Long) = db.get()
        .`object`(Manga::class.java)
        .withQuery(
            Query.builder()
                .table(MangaTable.TABLE)
                .where("${MangaTable.COL_URL} = ? AND ${MangaTable.COL_SOURCE} = ?")
                .whereArgs(url, sourceId)
                .build()
        )
        .prepare()

    fun getManga(id: Long) = db.get()
        .`object`(Manga::class.java)
        .withQuery(
            Query.builder()
                .table(MangaTable.TABLE)
                .where("${MangaTable.COL_ID} = ?")
                .whereArgs(id)
                .build()
        )
        .prepare()

    // SY -->
    fun getReadNotInLibraryMangas() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getReadMangaNotInLibraryQuery())
                .build()
        )
        .prepare()

    fun updateMangaInfo(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaInfoPutResolver())
        .prepare()

    fun resetMangaInfo(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaInfoPutResolver(true))
        .prepare()

    fun updateMangaMigrate(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaMigrationPutResolver())
        .prepare()

    fun updateMangaThumbnail(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaThumbnailPutResolver())
        .prepare()
    // SY <--

    fun insertManga(manga: Manga) = db.put().`object`(manga).prepare()

    fun insertMangas(mangas: List<Manga>) = db.put().objects(mangas).prepare()

    fun updateFlags(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaFlagsPutResolver())
        .prepare()

    fun updateFlags(mangas: List<Manga>) = db.put()
        .objects(mangas)
        .withPutResolver(MangaFlagsPutResolver(true))
        .prepare()

    fun updateLastUpdated(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaLastUpdatedPutResolver())
        .prepare()

    fun updateMangaFavorite(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaFavoritePutResolver())
        .prepare()

    fun updateMangaViewer(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaViewerPutResolver())
        .prepare()

    fun updateMangaTitle(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaTitlePutResolver())
        .prepare()

    fun updateMangaCoverLastModified(manga: Manga) = db.put()
        .`object`(manga)
        .withPutResolver(MangaCoverLastModifiedPutResolver())
        .prepare()

    fun deleteManga(manga: Manga) = db.delete().`object`(manga).prepare()

    fun deleteMangas(mangas: List<Manga>) = db.delete().objects(mangas).prepare()

    fun deleteMangasNotInLibrary() = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(MangaTable.TABLE)
                .where(
                    """
                    ${MangaTable.COL_FAVORITE} = ? AND ${MangaTable.COL_ID} NOT IN (
                        SELECT ${MergedTable.COL_MANGA_ID} FROM ${MergedTable.TABLE} WHERE ${MergedTable.COL_MANGA_ID} != ${MergedTable.COL_MERGE_ID}
                    )
                    """.trimIndent()
                )
                .whereArgs(0)
                .build()
        )
        .prepare()

    // SY -->
    fun deleteMangasNotInLibraryAndNotRead() = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(MangaTable.TABLE)
                .where(
                    """
                    ${MangaTable.COL_FAVORITE} = ? AND ${MangaTable.COL_ID} NOT IN (
                        SELECT ${MergedTable.COL_MANGA_ID} FROM ${MergedTable.TABLE} WHERE ${MergedTable.COL_MANGA_ID} != ${MergedTable.COL_MERGE_ID}
                    ) AND ${MangaTable.COL_ID} NOT IN (
                        SELECT ${ChapterTable.COL_MANGA_ID} FROM ${ChapterTable.TABLE} WHERE ${ChapterTable.COL_READ} = 1 OR ${ChapterTable.COL_LAST_PAGE_READ} != 0
                    )
                    """.trimIndent()
                )
                .whereArgs(0)
                .build()
        )
        .prepare()
    // SY <--

    fun deleteMangas() = db.delete()
        .byQuery(
            DeleteQuery.builder()
                .table(MangaTable.TABLE)
                .build()
        )
        .prepare()

    fun getLastReadManga() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getLastReadMangaQuery())
                .observesTables(MangaTable.TABLE)
                .build()
        )
        .prepare()

    fun getTotalChapterManga() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getTotalChapterMangaQuery())
                .observesTables(MangaTable.TABLE)
                .build()
        )
        .prepare()

    fun getLatestChapterManga() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(getLatestChapterMangaQuery())
                .observesTables(MangaTable.TABLE)
                .build()
        )
        .prepare()

    // SY -->
    fun getMangaWithMetadata() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(
                    """
                    SELECT ${MangaTable.TABLE}.* FROM ${MangaTable.TABLE}
                    INNER JOIN ${SearchMetadataTable.TABLE}
                        ON ${MangaTable.TABLE}.${MangaTable.COL_ID} = ${SearchMetadataTable.TABLE}.${SearchMetadataTable.COL_MANGA_ID}
                    ORDER BY ${MangaTable.TABLE}.${MangaTable.COL_ID}
                    """.trimIndent()
                )
                .build()
        )
        .prepare()

    fun getFavoriteMangaWithMetadata() = db.get()
        .listOfObjects(Manga::class.java)
        .withQuery(
            RawQuery.builder()
                .query(
                    """
                    SELECT ${MangaTable.TABLE}.* FROM ${MangaTable.TABLE}
                    INNER JOIN ${SearchMetadataTable.TABLE}
                        ON ${MangaTable.TABLE}.${MangaTable.COL_ID} = ${SearchMetadataTable.TABLE}.${SearchMetadataTable.COL_MANGA_ID}
                    WHERE ${MangaTable.TABLE}.${MangaTable.COL_FAVORITE} = 1
                    ORDER BY ${MangaTable.TABLE}.${MangaTable.COL_ID}
                    """.trimIndent()
                )
                .build()
        )
        .prepare()

    fun getIdsOfFavoriteMangaWithMetadata() = db.get()
        .cursor()
        .withQuery(
            RawQuery.builder()
                .query(
                    """
                    SELECT ${MangaTable.TABLE}.${MangaTable.COL_ID} FROM ${MangaTable.TABLE}
                    INNER JOIN ${SearchMetadataTable.TABLE}
                        ON ${MangaTable.TABLE}.${MangaTable.COL_ID} = ${SearchMetadataTable.TABLE}.${SearchMetadataTable.COL_MANGA_ID}
                    WHERE ${MangaTable.TABLE}.${MangaTable.COL_FAVORITE} = 1
                    ORDER BY ${MangaTable.TABLE}.${MangaTable.COL_ID}
                    """.trimIndent()
                )
                .build()
        )
        .prepare()
    // SY <--
}
