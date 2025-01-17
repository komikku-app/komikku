package eu.kanade.tachiyomi.ui.browse.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateSearchScreenModel(
    val mangaId: Long,
    // SY -->
    val validSources: List<Long>,
    // SY <--
    getAnime: GetAnime = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    // SY <--
) : SearchScreenModel() {

    init {
        screenModelScope.launch {
            val manga = getAnime.await(mangaId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = manga.source,
                    searchQuery = manga.title,
                )
            }
            search()
        }

        // KMK -->
        shouldPinnedSourcesHidden()
        // KMK <--
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        // SY -->
        return validSources.mapNotNull { sourceManager.get(it) }
            .filterIsInstance<AnimeCatalogueSource>()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
        // SY <--
    }
}
