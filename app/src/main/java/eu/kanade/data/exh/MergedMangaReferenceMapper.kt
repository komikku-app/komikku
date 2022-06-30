package eu.kanade.data.exh

import exh.merged.sql.models.MergedMangaReference

val mergedMangaReferenceMapper = {
        id: Long,
        isInfoManga: Boolean,
        getChapterUpdates: Boolean,
        chapterSortMode: Long,
        chapterPriority: Long,
        downloadChapters: Boolean,
        mergeId: Long,
        mergeUrl: String,
        mangaId: Long?,
        mangaUrl: String,
        mangaSourceId: Long,  ->
    MergedMangaReference(
        id = id,
        isInfoManga = isInfoManga,
        getChapterUpdates = getChapterUpdates,
        chapterSortMode = chapterSortMode.toInt(),
        chapterPriority = chapterPriority.toInt(),
        downloadChapters = downloadChapters,
        mergeId = mergeId,
        mergeUrl = mergeUrl,
        mangaId = mangaId,
        mangaUrl = mangaUrl,
        mangaSourceId = mangaSourceId,
    )
}
