package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.metadata.metadata.RankedSearchMetadata
import exh.recs.batch.RankedSearchResults
import tachiyomi.domain.manga.model.Manga

class StaticResultPagingSource(
    val data: RankedSearchResults,
) : RecommendationPagingSource(Manga.create()) {

    override val name: String get() = data.recSourceName
    override val category: StringResource get() = StringResource(data.recSourceCategoryResId)
    override val associatedSourceId: Long? get() = data.recAssociatedSourceId

    override suspend fun requestNextPage(currentPage: Int): MangasPage =
        // Use virtual paging to improve performance for large lists
        data.results
            .entries
            .chunked(PAGE_SIZE)
            .getOrElse(currentPage - 1) { emptyList() }
            .let { chunk ->
                MetadataMangasPage(
                    mangas = chunk.map { it.key },
                    hasNextPage = data.results.size > currentPage * PAGE_SIZE,
                    mangasMetadata = chunk
                        .map { it.value }
                        .map { count ->
                            RankedSearchMetadata().also { it.rank = count }
                        },
                )
            }

    companion object {
        const val PAGE_SIZE = 25
    }
}
