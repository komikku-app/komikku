package tachiyomi.data.updates

import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.view.UpdatesView

val updateWithRelationMapper: (Long, String, Long, String, String?, Boolean, Boolean, Long, Long, Boolean, String?, Long, Long, Long) -> UpdatesWithRelations = {
        mangaId, mangaTitle, chapterId, chapterName, scanlator, read, bookmark, lastPageRead, sourceId, favorite, thumbnailUrl, coverLastModified, _, dateFetch ->
    UpdatesWithRelations(
        mangaId = mangaId,
        // SY -->
        ogMangaTitle = mangaTitle,
        // SY <--
        chapterId = chapterId,
        chapterName = chapterName,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastPageRead = lastPageRead,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}

val updatesViewMapper: (UpdatesView) -> UpdatesWithRelations = {
    UpdatesWithRelations(
        mangaId = it.mangaId,
        ogMangaTitle = it.mangaTitle,
        chapterId = it.chapterId,
        chapterName = it.chapterName,
        scanlator = it.scanlator,
        read = it.read,
        bookmark = it.bookmark,
        lastPageRead = it.last_page_read,
        sourceId = it.source,
        dateFetch = it.datefetch,
        coverData = MangaCover(
            mangaId = it.mangaId,
            sourceId = it.source,
            isMangaFavorite = it.favorite,
            url = it.thumbnailUrl,
            lastModified = it.coverLastModified,
        ),
    )
}
