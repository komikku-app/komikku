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
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class SourcesPresenter(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    // SY -->
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    private val getShowLatest: GetShowLatest = Injekt.get(),
    private val toggleExcludeFromDataSaver: ToggleExcludeFromDataSaver = Injekt.get(),
    private val setSourceCategories: SetSourceCategories = Injekt.get(),
    private val controllerMode: SourcesController.Mode,
    // SY <--
) : BasePresenter<SourcesController>() {

    private val _state: MutableStateFlow<SourceState> = MutableStateFlow(SourceState.Loading)
    val state: StateFlow<SourceState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        // SY -->
        combine(
            getEnabledSources.subscribe(),
            getSourceCategories.subscribe(),
            getShowLatest.subscribe(controllerMode),
            flowOf(controllerMode == SourcesController.Mode.CATALOGUE),
            ::collectLatestSources,
        )
            .catch { exception ->
                _state.value = SourceState.Error(exception)
            }
            .flowOn(Dispatchers.IO)
            .launchIn(presenterScope)
        // SY <--
    }

    private fun collectLatestSources(sources: List<Source>, categories: List<String>, showLatest: Boolean, showPin: Boolean) {
        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            // Sources without a lang defined will be placed at the end
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

        val uiModels = byLang.flatMap {
            listOf(
                SourceUiModel.Header(it.key, it.value.firstOrNull()?.category != null),
                *it.value.map { source ->
                    SourceUiModel.Item(source)
                }.toTypedArray(),
            )
        }
        _state.value = SourceState.Success(
            uiModels,
            categories.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it }),
            showLatest,
            showPin,
        )
    }

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

sealed class SourceState {
    object Loading : SourceState()
    data class Error(val error: Throwable) : SourceState()
    data class Success(
        val uiModels: List<SourceUiModel>,
        val sourceCategories: List<String>,
        val showLatest: Boolean,
        val showPin: Boolean,
    ) : SourceState()
}
