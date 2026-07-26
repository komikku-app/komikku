package tachiyomi.data

// KMK -->
/** Drops the cached stats rows, runs [block], then rebuilds them once. Bulk paths only; needs a transaction. */
internal fun Database.rebuildingStats(resolveMangaIds: () -> List<Long>, block: Database.() -> Unit) {
    val mangaIds = resolveMangaIds()
    if (mangaIds.isEmpty()) {
        block()
        return
    }
    manga_chapter_statsQueries.deleteForMangaIds(mangaIds)
    block()
    manga_chapter_statsQueries.rebuildForMangaIds(mangaIds)
}
// KMK <--
