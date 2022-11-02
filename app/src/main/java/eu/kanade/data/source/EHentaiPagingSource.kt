package eu.kanade.data.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.lang.awaitSingle
import exh.metadata.metadata.EHentaiSearchMetadata

abstract class EHentaiPagingSource(override val source: EHentai) : SourcePagingSource(source) {

    private var lastMangaLink: String? = null

    abstract suspend fun fetchNextPage(currentPage: Int): MangasPage

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val lastMangaLink = lastMangaLink

        val gid = if (lastMangaLink != null && source.exh) {
            EHentaiSearchMetadata.galleryId(lastMangaLink).toInt()
        } else {
            null
        }

        val mangasPage = fetchNextPage(gid ?: currentPage)

        mangasPage.mangas.lastOrNull()?.let {
            this.lastMangaLink = it.url
        }
        return if (lastMangaLink != null) {
            val index = mangasPage.mangas.indexOfFirst { it.url == lastMangaLink }
            if (index != -1) {
                val lastIndex = mangasPage.mangas.size
                val startIndex = (index + 1).coerceAtMost(mangasPage.mangas.lastIndex)
                if (mangasPage is MetadataMangasPage) {
                    mangasPage.copy(
                        mangas = mangasPage.mangas.subList(startIndex, lastIndex),
                        mangasMetadata = mangasPage.mangasMetadata.subList(startIndex, lastIndex),
                    )
                } else {
                    mangasPage.copy(
                        mangas = mangasPage.mangas.subList(startIndex, lastIndex),
                    )
                }
            } else {
                mangasPage
            }
        } else {
            mangasPage
        }
    }
}

class EHentaiSearchPagingSource(source: EHentai, val query: String, val filters: FilterList) : EHentaiPagingSource(source) {
    override suspend fun fetchNextPage(currentPage: Int): MangasPage {
        return source.fetchSearchManga(currentPage, query, filters).awaitSingle()
    }
}

class EHentaiPopularPagingSource(source: EHentai) : EHentaiPagingSource(source) {
    override suspend fun fetchNextPage(currentPage: Int): MangasPage {
        return source.fetchPopularManga(currentPage).awaitSingle()
    }
}

class EHentaiLatestPagingSource(source: EHentai) : EHentaiPagingSource(source) {
    override suspend fun fetchNextPage(currentPage: Int): MangasPage {
        return source.fetchLatestUpdates(currentPage).awaitSingle()
    }
}
