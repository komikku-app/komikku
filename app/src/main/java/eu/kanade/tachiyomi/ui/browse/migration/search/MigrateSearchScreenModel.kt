package eu.kanade.tachiyomi.ui.browse.migration.search

import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MigrateSearchScreenModel(
    val mangaId: Long,
    // SY -->
    val validSources: List<Long>,
    // SY <--
) : SearchScreenModel() {

    init {
        coroutineScope.launch {
            val manga = getManga.await(mangaId)!!

            mutableState.update {
                it.copy(fromSourceId = manga.source, searchQuery = manga.title)
            }

            search(manga.title)
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        val pinnedSources = sourcePreferences.pinnedSources().get()
        // SY -->
        return validSources.mapNotNull { sourceManager.get(it) }
            .filterIsInstance<CatalogueSource>()
            .filter { mutableState.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
        // SY <--
    }
}
