package tachiyomi.domain.library.model

import tachiyomi.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val category: Long,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    // KMK -->
    val bookmarkReadCount: Long,
    val chapterFlags: Long,
    // KMK <--
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
) {
    val id: Long = manga.id

    val unreadCount
        get() = when {
            // KMK -->
            chapterFlags and Manga.CHAPTER_SHOW_NOT_BOOKMARKED != 0L -> (totalChapters - bookmarkCount) - (readCount - bookmarkReadCount)
            chapterFlags and Manga.CHAPTER_SHOW_BOOKMARKED != 0L -> bookmarkCount - bookmarkReadCount
            // KMK <--
            else -> totalChapters - readCount
        }

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasStarted = readCount > 0
}
