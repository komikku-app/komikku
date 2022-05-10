package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Pins
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import exh.source.BlacklistedSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetEnabledSources(
    private val repository: SourceRepository,
    private val preferences: PreferencesHelper,
) {

    fun subscribe(): Flow<List<Source>> {
        return combine(
            preferences.pinnedSources().asFlow(),
            combine(
                preferences.enabledLanguages().asFlow(),
                preferences.disabledSources().asFlow(),
                preferences.lastUsedSource().asFlow(),
            ) { a, b, c -> Triple(a, b, c) },
            // SY -->
            combine(
                preferences.dataSaverExcludedSources().asFlow(),
                preferences.sourcesTabSourcesInCategories().asFlow(),
                preferences.sourcesTabCategoriesFilter().asFlow(),
            ) { a, b, c -> Triple(a, b, c) },
            // SY <--
            repository.getSources(),
        ) { pinnedSourceIds, (enabledLanguages, disabledSources, lastUsedSource), (excludedFromDataSaver, sourcesInCategories, sourceCategoriesFilter), sources ->
            val duplicatePins = preferences.duplicatePinnedSources().get()
            val sourcesAndCategories = sourcesInCategories.map {
                it.split('|').let { (source, test) -> source.toLong() to test }
            }
            val sourcesInSourceCategories = sourcesAndCategories.map { it.first }
            sources
                .filter { it.lang in enabledLanguages || it.id == LocalSource.ID }
                .filterNot { it.id.toString() in disabledSources || it.id in BlacklistedSources.HIDDEN_SOURCES }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .flatMap {
                    val flag = if ("${it.id}" in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    // SY -->
                    val categories = sourcesAndCategories.filter { (id) -> id == it.id }
                        .map(Pair<*, String>::second)
                        .toSet()
                    // SY <--
                    val source = it.copy(
                        pin = flag,
                        isExcludedFromDataSaver = it.id.toString() in excludedFromDataSaver,
                        categories = categories,
                    )
                    val toFlatten = mutableListOf(source)
                    if (source.id == lastUsedSource) {
                        toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                    }
                    if (duplicatePins && Pin.Pinned in source.pin) {
                        toFlatten[0] = toFlatten[0].copy(pin = source.pin + Pin.Forced)
                        toFlatten.add(source.copy(pin = source.pin - Pin.Actual))
                    }
                    // SY -->
                    categories.forEach { category ->
                        toFlatten.add(source.copy(category = category, pin = source.pin - Pin.Actual))
                    }
                    if (sourceCategoriesFilter && Pin.Actual !in toFlatten[0].pin && source.id in sourcesInSourceCategories) {
                        toFlatten.removeAt(0)
                    }
                    // SY <--
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
