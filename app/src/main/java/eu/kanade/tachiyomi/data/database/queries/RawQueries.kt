package eu.kanade.tachiyomi.data.database.queries

import exh.source.MERGED_SOURCE_ID
import eu.kanade.tachiyomi.data.database.tables.CategoryTable as Category
import eu.kanade.tachiyomi.data.database.tables.ChapterTable as Chapter
import eu.kanade.tachiyomi.data.database.tables.HistoryTable as History
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable as MangaCategory
import eu.kanade.tachiyomi.data.database.tables.MangaTable as Manga
import exh.merged.sql.tables.MergedTable as Merged

// SY -->
/**
 * Query to get the manga merged into a merged manga
 */
fun getMergedMangaQuery() =
    """
    SELECT ${Manga.TABLE}.*
    FROM (
        SELECT ${Merged.COL_MANGA_ID} FROM ${Merged.TABLE} WHERE ${Merged.COL_MERGE_ID} = ?
    ) AS M
    JOIN ${Manga.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = M.${Merged.COL_MANGA_ID}
"""

/**
 * Query to get all the manga that are merged into other manga
 */
fun getAllMergedMangaQuery() =
    """
    SELECT ${Manga.TABLE}.*
    FROM (
        SELECT ${Merged.COL_MANGA_ID} FROM ${Merged.TABLE}
    ) AS M
    JOIN ${Manga.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = M.${Merged.COL_MANGA_ID}
"""

/**
 * Query to get the manga merged into a merged manga using the Url
 */
fun getMergedMangaFromUrlQuery() =
    """
    SELECT ${Manga.TABLE}.*
    FROM (
        SELECT ${Merged.COL_MANGA_ID} FROM ${Merged.TABLE} WHERE ${Merged.COL_MERGE_URL} = ?
    ) AS M
    JOIN ${Manga.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = M.${Merged.COL_MANGA_ID}
"""

/**
 * Query to get the chapters of all manga in a merged manga
 */
fun getMergedChaptersQuery() =
    """
    SELECT ${Chapter.TABLE}.*
    FROM (
        SELECT ${Merged.COL_MANGA_ID} FROM ${Merged.TABLE} WHERE ${Merged.COL_MERGE_ID} = ?
    ) AS M
    JOIN ${Chapter.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = M.${Merged.COL_MANGA_ID}
"""

/**
 * Query to get manga that are not in library, but have read chapters
 */
fun getReadMangaNotInLibraryQuery() =
    """
    SELECT ${Manga.TABLE}.* 
    FROM ${Manga.TABLE} 
    WHERE ${Manga.COL_FAVORITE} = 0 AND ${Manga.COL_ID} IN(
        SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} FROM ${Chapter.TABLE} WHERE ${Chapter.COL_READ} = 1 OR ${Chapter.COL_LAST_PAGE_READ} != 0
    )
"""

/**
 * Query to get the manga from the library, with their categories and unread count.
 */
val libraryQuery =
    """
    SELECT M.*, COALESCE(MC.${MangaCategory.COL_CATEGORY_ID}, 0) AS ${Manga.COL_CATEGORY}
    FROM (
        SELECT ${Manga.TABLE}.*, COALESCE(C.unread, 0) AS ${Manga.COL_UNREAD}, COALESCE(R.read, 0) AS ${Manga.COL_READ}
            FROM ${Manga.TABLE}
            LEFT JOIN (
                SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}, COUNT(*) AS unread
                FROM ${Chapter.TABLE}
                WHERE ${Chapter.COL_READ} = 0
                GROUP BY ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
            ) AS C
            ON ${Manga.TABLE}.${Manga.COL_ID} = C.${Chapter.COL_MANGA_ID}
            LEFT JOIN (
                SELECT ${Chapter.COL_MANGA_ID}, COUNT(*) AS read
                FROM ${Chapter.TABLE}
                WHERE ${Chapter.COL_READ} = 1
                GROUP BY ${Chapter.COL_MANGA_ID}
            ) AS R
            ON ${Manga.TABLE}.${Manga.COL_ID} = R.${Chapter.COL_MANGA_ID}
            WHERE ${Manga.COL_FAVORITE} = 1 AND ${Manga.COL_SOURCE} <> $MERGED_SOURCE_ID
            GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
        UNION
        SELECT ${Manga.TABLE}.*, COALESCE(C.unread, 0) AS ${Manga.COL_UNREAD}, COALESCE(R.read, 0) AS ${Manga.COL_READ}
            FROM ${Manga.TABLE}
            LEFT JOIN (
                SELECT ${Merged.TABLE}.${Merged.COL_MERGE_ID}, COUNT(*) as unread
                FROM ${Merged.TABLE}
                JOIN ${Chapter.TABLE}
                ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = ${Merged.TABLE}.${Merged.COL_MANGA_ID}
                WHERE ${Chapter.TABLE}.${Chapter.COL_READ} = 0
                GROUP BY ${Merged.TABLE}.${Merged.COL_MERGE_ID}
            ) AS C
            ON ${Manga.TABLE}.${Manga.COL_ID} = C.${Merged.COL_MERGE_ID}
            LEFT JOIN (
                SELECT ${Merged.TABLE}.${Merged.COL_MERGE_ID}, COUNT(*) as read
                FROM ${Merged.TABLE}
                JOIN ${Chapter.TABLE}
                ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = ${Merged.TABLE}.${Merged.COL_MANGA_ID}
                WHERE ${Chapter.TABLE}.${Chapter.COL_READ} = 1
                GROUP BY ${Merged.TABLE}.${Merged.COL_MERGE_ID}
            ) AS R
            ON ${Manga.TABLE}.${Manga.COL_ID} = R.${Merged.COL_MERGE_ID}
            WHERE ${Manga.COL_FAVORITE} = 1 AND ${Manga.COL_SOURCE} = $MERGED_SOURCE_ID
            GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
        ORDER BY ${Manga.COL_TITLE}
    ) AS M
    LEFT JOIN (
            SELECT * FROM ${MangaCategory.TABLE}
        ) AS MC
        ON MC.${MangaCategory.COL_MANGA_ID} = M.${Manga.COL_ID};
"""
// SY <--

/**
 * Query to get the recent chapters of manga from the library up to a date.
 */
fun getRecentsQuery() =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, * FROM ${Manga.TABLE} JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    WHERE ${Manga.COL_FAVORITE} = 1 
    AND ${Chapter.COL_DATE_UPLOAD} > ?
    AND ${Chapter.COL_DATE_FETCH} > ${Manga.COL_DATE_ADDED}
    ORDER BY ${Chapter.COL_DATE_UPLOAD} DESC
"""

/**
 * Query to get the recently read chapters of manga from the library up to a date.
 * The max_last_read table contains the most recent chapters grouped by manga
 * The select statement returns all information of chapters that have the same id as the chapter in max_last_read
 * and are read after the given time period
 */
fun getRecentMangasQuery(search: String = "") =
    """
    SELECT ${Manga.TABLE}.${Manga.COL_URL} as mangaUrl, ${Manga.TABLE}.*, ${Chapter.TABLE}.*, ${History.TABLE}.*
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    JOIN (
    SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID},${Chapter.TABLE}.${Chapter.COL_ID} as ${History.COL_CHAPTER_ID}, MAX(${History.TABLE}.${History.COL_LAST_READ}) as ${History.COL_LAST_READ}
    FROM ${Chapter.TABLE} JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    GROUP BY ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}) AS max_last_read
    ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = max_last_read.${Chapter.COL_MANGA_ID}
    WHERE ${History.TABLE}.${History.COL_LAST_READ} > ?
    AND max_last_read.${History.COL_CHAPTER_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    AND lower(${Manga.TABLE}.${Manga.COL_TITLE}) LIKE '%$search%'
    ORDER BY max_last_read.${History.COL_LAST_READ} DESC
    LIMIT ? OFFSET ?
"""

fun getHistoryByMangaId() =
    """
    SELECT ${History.TABLE}.*
    FROM ${History.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
    WHERE ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = ? AND ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
"""

fun getHistoryByChapterUrl() =
    """
    SELECT ${History.TABLE}.*
    FROM ${History.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
    WHERE ${Chapter.TABLE}.${Chapter.COL_URL} = ? AND ${History.TABLE}.${History.COL_CHAPTER_ID} = ${Chapter.TABLE}.${Chapter.COL_ID}
"""

fun getLastReadMangaQuery() =
    """
    SELECT ${Manga.TABLE}.*, MAX(${History.TABLE}.${History.COL_LAST_READ}) AS max
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    JOIN ${History.TABLE}
    ON ${Chapter.TABLE}.${Chapter.COL_ID} = ${History.TABLE}.${History.COL_CHAPTER_ID}
    WHERE ${Manga.TABLE}.${Manga.COL_FAVORITE} = 1
    GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
    ORDER BY max DESC
"""

fun getTotalChapterMangaQuery() =
    """
    SELECT ${Manga.TABLE}.*
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
    ORDER by COUNT(*)
"""

fun getLatestChapterMangaQuery() =
    """
    SELECT ${Manga.TABLE}.*, MAX(${Chapter.TABLE}.${Chapter.COL_DATE_UPLOAD}) AS max
    FROM ${Manga.TABLE}
    JOIN ${Chapter.TABLE}
    ON ${Manga.TABLE}.${Manga.COL_ID} = ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
    GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
    ORDER by max DESC
"""

/**
 * Query to get the categories for a manga.
 */
fun getCategoriesForMangaQuery() =
    """
    SELECT ${Category.TABLE}.* FROM ${Category.TABLE}
    JOIN ${MangaCategory.TABLE} ON ${Category.TABLE}.${Category.COL_ID} =
    ${MangaCategory.TABLE}.${MangaCategory.COL_CATEGORY_ID}
    WHERE ${MangaCategory.COL_MANGA_ID} = ?
"""
