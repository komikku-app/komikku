package eu.kanade.tachiyomi.ui.browse.source

import android.os.Bundle
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

/**
 * Presenter of [SourceController]
 * Function calls should be done from here. UI calls should be done from the controller.
 */
class SourcePresenter(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    // SY -->
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    private val getShowLatest: GetShowLatest = Injekt.get(),
    private val toggleExcludeFromDataSaver: ToggleExcludeFromDataSaver = Injekt.get(),
    private val setSourceCategories: SetSourceCategories = Injekt.get(),
    private val controllerMode: SourceController.Mode,
    // SY <--
) : BasePresenter<SourceController>() {

    private val _state: MutableStateFlow<SourceState> = MutableStateFlow(SourceState.EMPTY)
    val state: StateFlow<SourceState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getEnabledSources.subscribe()
                .catch { exception ->
                    _state.update { state ->
                        state.copy(sources = listOf(), error = exception)
                    }
                }
                .collectLatest(::collectLatestSources)
        }
        // SY -->
        presenterScope.launchIO {
            getSourceCategories.subscribe()
                .catch { exception ->
                    _state.update { state ->
                        state.copy(sources = listOf(), error = exception)
                    }
                }
                .collectLatest(::updateCategories)
        }
        presenterScope.launchIO {
            _state.update { state ->
                state.copy(
                    showPin = controllerMode == SourceController.Mode.CATALOGUE,
                )
            }
        }
        presenterScope.launchIO {
            getShowLatest.subscribe(mode = controllerMode)
                .catch { exception ->
                    _state.update { state ->
                        state.copy(sources = listOf(), error = exception)
                    }
                }
                .collectLatest(::updateShowLatest)
        }
        // SY <--
    }

    private fun collectLatestSources(sources: List<Source>) {
        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            // Catalogues without a lang defined will be placed at the end
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = sources.groupByTo(map) {
            when {
                // SY -->
                it.category != null -> it.category
                // SY <--
                it.isUsedLast -> LAST_USED_KEY
                Pin.Actual in it.pin -> PINNED_KEY
                else -> it.lang
            }
        }
        _state.update { state ->
            state.copy(
                sources = byLang.flatMap {
                    listOf(
                        UiModel.Header(it.key, it.value.firstOrNull()?.category != null),
                        *it.value.map { source ->
                            UiModel.Item(source)
                        }.toTypedArray()
                    )
                },
                error = null
            )
        }
    }

    // SY -->
    private fun updateCategories(categories: Set<String>) {
        _state.update { state ->
            state.copy(
                sourceCategories = categories.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it })
            )
        }
    }
    private fun updateShowLatest(showLatest: Boolean) {
        _state.update { state ->
            state.copy(
                showLatest = showLatest
            )
        }
    }
    // SY <--

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    fun toggleExcludeFromDataSaver(source: Source) {
        toggleExcludeFromDataSaver.await(source)
    }

    fun setSourceCategories(source: Source, categories: List<String>) {
        setSourceCategories.await(source, categories)
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}

sealed class UiModel {
    data class Item(val source: Source) : UiModel()
    data class Header(val language: String, val isCategory: Boolean) : UiModel()
}

data class SourceState(
    val sources: List<UiModel>,
    val error: Throwable?,
    val sourceCategories: List<String>,
    val showLatest: Boolean,
    val showPin: Boolean
) {

    val isLoading: Boolean
        get() = sources.isEmpty() && error == null

    val hasError: Boolean
        get() = error != null

    val isEmpty: Boolean
        get() = sources.isEmpty()

    companion object {
        val EMPTY = SourceState(listOf(), null, emptyList(), true, true)
    }
}
