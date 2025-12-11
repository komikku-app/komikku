package exh.smartsearch

import mihon.feature.migration.list.search.BaseSmartSearchEngine
import tachiyomi.domain.library.model.LibraryManga

class SmartLibrarySearchEngine(
    extraSearchParams: String? = null,
) : BaseSmartSearchEngine<LibraryManga>(extraSearchParams, 0.7) {

    override fun getTitle(result: LibraryManga) = result.manga.ogTitle

    suspend fun smartSearch(library: List<LibraryManga>, title: String): LibraryManga? =
        smartSearch(
            { query ->
                library.filter { it.manga.ogTitle.contains(query, true) }
            },
            title,
        )
}
