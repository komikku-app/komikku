package exh.md.similar

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.getMainSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourcePagingSourceType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexSimilarScreenModel(
    val mangaId: Long,
    sourceId: Long,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourceScreenModel(sourceId, null) {

    val manga: Manga = runBlocking { getManga.await(mangaId) }!!

    override fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSourceType {
        return MangaDexSimilarPagingSource(manga, source.getMainSource() as MangaDex)
    }

    override fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        return map { it to metadata }
    }

    init {
        mutableState.update { it.copy(filterable = false) }
    }
}
