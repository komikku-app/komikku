package tachiyomi.data.manga

import tachiyomi.domain.manga.model.MergedMangaReference

object MergedMangaMapper {
    fun map(
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
        mangaSourceId: Long,
    ): MergedMangaReference {
        return MergedMangaReference(
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
}
