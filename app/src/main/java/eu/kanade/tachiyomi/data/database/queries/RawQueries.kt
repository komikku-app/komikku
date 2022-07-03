package eu.kanade.tachiyomi.data.database.queries

import exh.source.MERGED_SOURCE_ID
import eu.kanade.tachiyomi.data.database.tables.ChapterTable as Chapter
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
 * Query to get the manga merged into a merged manga
 */
fun getMergedMangaForDownloadingQuery() =
    """
    SELECT ${Manga.TABLE}.*
    FROM (
        SELECT ${Merged.COL_MANGA_ID} FROM ${Merged.TABLE} WHERE ${Merged.COL_MERGE_ID} = ? AND ${Merged.COL_DOWNLOAD_CHAPTERS} = 1
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
 * Query to get the manga from the library, with their categories, read and unread count.
 */
val libraryQuery =
    """
    SELECT M.*, COALESCE(MC.${MangaCategory.COL_CATEGORY_ID}, 0) AS ${Manga.COL_CATEGORY}
    FROM (
        SELECT ${Manga.TABLE}.*, COALESCE(C.unreadCount, 0) AS ${Manga.COMPUTED_COL_UNREAD_COUNT}, COALESCE(R.readCount, 0) AS ${Manga.COMPUTED_COL_READ_COUNT}
            FROM ${Manga.TABLE}
            LEFT JOIN (
                SELECT ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}, COUNT(*) AS unreadCount
                FROM ${Chapter.TABLE}
                WHERE ${Chapter.COL_READ} = 0
                GROUP BY ${Chapter.TABLE}.${Chapter.COL_MANGA_ID}
            ) AS C
            ON ${Manga.TABLE}.${Manga.COL_ID} = C.${Chapter.COL_MANGA_ID}
            LEFT JOIN (
                SELECT ${Chapter.COL_MANGA_ID}, COUNT(*) AS readCount
                FROM ${Chapter.TABLE}
                WHERE ${Chapter.COL_READ} = 1
                GROUP BY ${Chapter.COL_MANGA_ID}
            ) AS R
            ON ${Manga.TABLE}.${Manga.COL_ID} = R.${Chapter.COL_MANGA_ID}
            WHERE ${Manga.COL_FAVORITE} = 1 AND ${Manga.COL_SOURCE} <> $MERGED_SOURCE_ID
            GROUP BY ${Manga.TABLE}.${Manga.COL_ID}
        UNION
        SELECT ${Manga.TABLE}.*, COALESCE(C.unreadCount, 0) AS ${Manga.COMPUTED_COL_UNREAD_COUNT}, COALESCE(R.readCount, 0) AS ${Manga.COMPUTED_COL_READ_COUNT}
            FROM ${Manga.TABLE}
            LEFT JOIN (
                SELECT ${Merged.TABLE}.${Merged.COL_MERGE_ID}, COUNT(*) as unreadCount
                FROM ${Merged.TABLE}
                JOIN ${Chapter.TABLE}
                ON ${Chapter.TABLE}.${Chapter.COL_MANGA_ID} = ${Merged.TABLE}.${Merged.COL_MANGA_ID}
                WHERE ${Chapter.TABLE}.${Chapter.COL_READ} = 0
                GROUP BY ${Merged.TABLE}.${Merged.COL_MERGE_ID}
            ) AS C
            ON ${Manga.TABLE}.${Manga.COL_ID} = C.${Merged.COL_MERGE_ID}
            LEFT JOIN (
                SELECT ${Merged.TABLE}.${Merged.COL_MERGE_ID}, COUNT(*) as readCount
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
