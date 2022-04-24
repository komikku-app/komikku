package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Pins
import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.repository.SourceRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetEnabledSources(
    private val repository: SourceRepository,
    private val preferences: PreferencesHelper
) {

    fun subscribe(): Flow<List<Source>> {
        return preferences.pinnedSources().asFlow()
            .combine(preferences.enabledLanguages().asFlow()) { pinList, enabledLanguages ->
                Config(pinSet = pinList, enabledSources = enabledLanguages)
            }
            .combine(preferences.disabledSources().asFlow()) { config, disabledSources ->
                config.copy(disabledSources = disabledSources)
            }
            .combine(preferences.lastUsedSource().asFlow()) { config, lastUsedSource ->
                config.copy(lastUsedSource = lastUsedSource)
            }
            // SY -->
            .combine(preferences.dataSaverExcludedSources().asFlow()) { config, excludedFromDataSaver ->
                config.copy(excludedFromDataSaver = excludedFromDataSaver)
            }
            .combine(preferences.sourcesTabSourcesInCategories().asFlow()) { config, sourcesInCategories ->
                config.copy(sourcesInCategories = sourcesInCategories)
            }
            .combine(preferences.sourcesTabCategoriesFilter().asFlow()) { config, sourceCategoriesFilter ->
                config.copy(sourceCategoriesFilter = sourceCategoriesFilter)
            }
            // SY <--
            .combine(repository.getSources()) { (pinList, enabledLanguages, disabledSources, lastUsedSource, excludedFromDataSaver, sourcesInCategories, sourceCategoriesFilter), sources ->
                val pinsOnTop = preferences.pinsOnTop().get()
                val sourcesAndCategories = sourcesInCategories.map {
                    it.split('|').let { (source, test) -> source.toLong() to test }
                }
                val sourcesInSourceCategories = sourcesAndCategories.map { it.first }
                sources
                    .filter { it.lang in enabledLanguages || it.id == LocalSource.ID }
                    .filterNot { it.id.toString() in disabledSources }
                    .flatMap {
                        val flag = if ("${it.id}" in pinList) Pins.pinned else Pins.unpinned
                        // SY -->
                        val categories = sourcesAndCategories.filter { (id) -> id == it.id }
                            .map(Pair<*, String>::second)
                            .toSet()
                        // SY <--
                        val source = it.copy(
                            pin = flag,
                            isExcludedFromDataSaver = it.id.toString() in excludedFromDataSaver,
                            categories = categories
                        )
                        val toFlatten = mutableListOf(source)
                        if (source.id == lastUsedSource) {
                            toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                        }
                        if (pinsOnTop.not() && Pin.Pinned in source.pin) {
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

private data class Config(
    val pinSet: Set<String> = setOf(),
    val enabledSources: Set<String> = setOf(),
    val disabledSources: Set<String> = setOf(),
    val lastUsedSource: Long? = null,
    // SY -->
    val excludedFromDataSaver: Set<String> = setOf(),
    val sourcesInCategories: Set<String> = setOf(),
    val sourceCategoriesFilter: Boolean = false,
    // SY <--
)
