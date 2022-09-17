package eu.kanade.tachiyomi.ui.category.biometric

import android.app.Application
import android.os.Bundle
import eu.kanade.presentation.category.BiometricTimesState
import eu.kanade.presentation.category.BiometricTimesStateImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.preference.plusAssign
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [BiometricTimesController]. Used to manage the categories of the library.
 */
class BiometricTimesPresenter(
    private val state: BiometricTimesStateImpl = BiometricTimesState() as BiometricTimesStateImpl,
) : BasePresenter<BiometricTimesController>(), BiometricTimesState by state {

    val preferences: PreferencesHelper = Injekt.get()

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events = _events.consumeAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            // todo usecase
            preferences.authenticatorTimeRanges().changes()
                .collectLatest {
                    val context = view?.activity ?: Injekt.get<Application>()
                    state.isLoading = false
                    state.timeRanges = it.toList()
                        .mapNotNull(TimeRange::fromPreferenceString)
                        .map { TimeRangeItem(it, it.getFormattedString(context)) }
                }
        }
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createTimeRange(timeRange: TimeRange) {
        // todo usecase
        presenterScope.launchIO {
            // Do not allow duplicate categories.
            if (timeRangeConflicts(timeRange)) {
                _events.send(Event.TimeConflicts)
                return@launchIO
            }

            preferences.authenticatorTimeRanges() += timeRange.toPreferenceString()
        }
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param timeRanges The list of categories to delete.
     */
    fun deleteTimeRanges(timeRange: TimeRangeItem) {
        // todo usecase
        presenterScope.launchIO {
            preferences.authenticatorTimeRanges().set(
                state.timeRanges.filterNot { it == timeRange }.map { it.timeRange.toPreferenceString() }.toSet(),
            )
        }
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun timeRangeConflicts(timeRange: TimeRange): Boolean {
        return timeRanges.any { timeRange.conflictsWith(it.timeRange) }
    }

    sealed class Event {
        object TimeConflicts : Event()
        object InternalError : Event()
    }

    sealed class Dialog {
        object Create : Dialog()
        data class Delete(val timeRange: TimeRangeItem) : Dialog()
    }
}
