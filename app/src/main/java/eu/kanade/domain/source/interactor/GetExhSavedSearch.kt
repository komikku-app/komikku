package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.log.xLogE
import exh.savedsearches.EXHSavedSearch
import exh.savedsearches.models.SavedSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

class GetExhSavedSearch(
    private val getSavedSearchById: GetSavedSearchById,
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId,
    private val filterSerializer: FilterSerializer,
) {

    suspend fun awaitOne(savedSearchId: Long, getFilterList: () -> FilterList): EXHSavedSearch? {
        val search = getSavedSearchById.awaitOrNull(savedSearchId) ?: return null
        return withIOContext { loadSearch(search, getFilterList) }
    }

    suspend fun await(sourceId: Long, getFilterList: () -> FilterList): List<EXHSavedSearch> {
        return withIOContext { loadSearches(getSavedSearchBySourceId.await(sourceId), getFilterList) }
    }

    fun subscribe(sourceId: Long, getFilterList: () -> FilterList): Flow<List<EXHSavedSearch>> {
        return getSavedSearchBySourceId.subscribe(sourceId)
            .map { loadSearches(it, getFilterList) }
            .flowOn(Dispatchers.IO)
    }

    private fun loadSearches(searches: List<SavedSearch>, getFilterList: () -> FilterList): List<EXHSavedSearch> {
        return searches.map { loadSearch(it, getFilterList) }
    }

    private fun loadSearch(search: SavedSearch, getFilterList: () -> FilterList): EXHSavedSearch {
        val filters = getFilters(search.filtersJson)

        return EXHSavedSearch(
            id = search.id,
            name = search.name,
            query = search.query.orEmpty(),
            filterList = filters?.let { deserializeFilters(it, getFilterList) },
        )
    }

    private fun getFilters(filtersJson: String?): JsonArray? {
        return runCatching {
            filtersJson?.let { Json.decodeFromString<JsonArray>(it) }
        }.onFailure {
            xLogE("Failed to load saved search!", it)
        }.getOrNull()
    }

    private fun deserializeFilters(filters: JsonArray, getFilterList: () -> FilterList): FilterList? {
        return runCatching {
            val originalFilters = getFilterList()
            filterSerializer.deserialize(originalFilters, filters)
            originalFilters
        }.onFailure {
            xLogE("Failed to load saved search!", it)
        }.getOrNull()
    }
}
