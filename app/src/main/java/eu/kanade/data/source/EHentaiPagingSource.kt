package eu.kanade.data.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.util.lang.awaitSingle

abstract class EHentaiPagingSource(source: CatalogueSource) : SourcePagingSource(source) {

    private var lastMangaLink: String? = null

    abstract suspend fun fetchNextPage(currentPage: Int): MangasPage

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val lastMangaLink = lastMangaLink

        val mangasPage = fetchNextPage(currentPage)

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

class EHentaiSearchPagingSource(source: CatalogueSource, val query: String, val filters: FilterList) : EHentaiPagingSource(source) {
    override suspend fun fetchNextPage(currentPage: Int): MangasPage {
        return source.fetchSearchManga(currentPage, query, filters).awaitSingle()
    }
}

class EHentaiPopularPagingSource(source: CatalogueSource) : EHentaiPagingSource(source) {
    override suspend fun fetchNextPage(currentPage: Int): MangasPage {
        return source.fetchPopularManga(currentPage).awaitSingle()
    }
}

class EHentaiLatestPagingSource(source: CatalogueSource) : EHentaiPagingSource(source) {
    override suspend fun fetchNextPage(currentPage: Int): MangasPage {
        return source.fetchLatestUpdates(currentPage).awaitSingle()
    }
}
