package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.domain.manga.model.toMangaInfo
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.Source
import exh.source.getMainSource

class GetPagePreviews(
    private val pagePreviewCache: PagePreviewCache,
) {

    suspend fun await(manga: Manga, source: Source, page: Int): Result {
        @Suppress("NAME_SHADOWING")
        val source = source.getMainSource<PagePreviewSource>() ?: return Result.Unused
        return try {
            val pagePreviews = try {
                pagePreviewCache.getPageListFromCache(manga, page)
            } catch (e: Exception) {
                source.getPagePreviewList(manga.toMangaInfo(), page).also {
                    pagePreviewCache.putPageListToCache(manga, it)
                }
            }
            Result.Success(
                pagePreviews.pagePreviews.map {
                    PagePreview(it.index, it.imageUrl, source.id)
                },
                pagePreviews.hasNextPage,
                pagePreviews.pagePreviewPages,
            )
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed class Result {
        object Unused : Result()
        data class Success(
            val pagePreviews: List<PagePreview>,
            val hasNextPage: Boolean,
            val pageCount: Int?,
        ) : Result()
        data class Error(val error: Throwable) : Result()
    }
}
