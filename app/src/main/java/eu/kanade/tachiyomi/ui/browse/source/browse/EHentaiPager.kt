package eu.kanade.tachiyomi.ui.browse.source.browse

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.util.lang.awaitSingle

class EHentaiPager(val source: CatalogueSource, val query: String, val filters: FilterList) : Pager() {

    private var lastMangaLink: String? = null

    override suspend fun requestNextPage() {
        val page = currentPage
        val lastMangaLink = lastMangaLink

        val observable = if (query.isBlank() && filters.isEmpty()) {
            source.fetchPopularManga(page)
        } else {
            source.fetchSearchManga(page, query, filters)
        }

        val mangasPage = observable.awaitSingle()

        mangasPage.mangas.lastOrNull()?.let {
            this.lastMangaLink = it.url
        }
        if (lastMangaLink != null) {
            val index = mangasPage.mangas.indexOfFirst { it.url == lastMangaLink }
            if (index != -1) {
                val lastIndex = mangasPage.mangas.size
                val startIndex = (index + 1).coerceAtMost(mangasPage.mangas.lastIndex)
                onPageReceived(
                    if (mangasPage is MetadataMangasPage) {
                        mangasPage.copy(
                            mangas = mangasPage.mangas.subList(startIndex, lastIndex),
                            mangasMetadata = mangasPage.mangasMetadata.subList(startIndex, lastIndex),
                        )
                    } else {
                        mangasPage.copy(
                            mangas = mangasPage.mangas.subList(startIndex, lastIndex),
                        )
                    },
                )
            } else {
                onPageReceived(mangasPage)
            }
        } else {
            onPageReceived(mangasPage)
        }
    }
}
