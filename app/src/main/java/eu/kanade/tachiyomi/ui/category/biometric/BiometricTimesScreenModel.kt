package eu.kanade.tachiyomi.ui.category.biometric

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.preference.plusAssign
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BiometricTimesScreenModel(
    private val preferences: SecurityPreferences = Injekt.get(),
) : StateScreenModel<BiometricTimesScreenState>(BiometricTimesScreenState.Loading) {

    private val _events: Channel<BiometricTimesEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        coroutineScope.launchIO {
            // todo usecase
            preferences.authenticatorTimeRanges().changes()
                .collectLatest { times ->
                    val context = Injekt.get<Application>()
                    mutableState.update {
                        BiometricTimesScreenState.Success(
                            timeRanges = times.toList()
                                .mapNotNull(TimeRange::fromPreferenceString)
                                .map { TimeRangeItem(it, it.getFormattedString(context)) },
                        )
                    }
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
        coroutineScope.launchIO {
            // Do not allow duplicate categories.
            if (timeRangeConflicts(timeRange)) {
                _events.send(BiometricTimesEvent.TimeConflicts)
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
        coroutineScope.launchIO {
            val state = state.value as? BiometricTimesScreenState.Success ?: return@launchIO
            preferences.authenticatorTimeRanges().set(
                state.timeRanges.filterNot { it == timeRange }.map { it.timeRange.toPreferenceString() }.toSet(),
            )
        }
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun timeRangeConflicts(timeRange: TimeRange): Boolean {
        val state = state.value as? BiometricTimesScreenState.Success ?: return false
        return state.timeRanges.any { timeRange.conflictsWith(it.timeRange) }
    }

    fun showDialog(dialog: BiometricTimesDialog) {
        mutableState.update {
            when (it) {
                BiometricTimesScreenState.Loading -> it
                is BiometricTimesScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                BiometricTimesScreenState.Loading -> it
                is BiometricTimesScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class BiometricTimesEvent {
    sealed class LocalizedMessage(@StringRes val stringRes: Int) : BiometricTimesEvent()
    object TimeConflicts : LocalizedMessage(R.string.biometric_lock_time_conflicts)
    object InternalError : LocalizedMessage(R.string.internal_error)
}

sealed class BiometricTimesDialog {
    object Create : BiometricTimesDialog()
    data class Delete(val timeRange: TimeRangeItem) : BiometricTimesDialog()
}

sealed class BiometricTimesScreenState {

    @Immutable
    object Loading : BiometricTimesScreenState()

    @Immutable
    data class Success(
        val timeRanges: List<TimeRangeItem>,
        val dialog: BiometricTimesDialog? = null,
    ) : BiometricTimesScreenState() {

        val isEmpty: Boolean
            get() = timeRanges.isEmpty()
    }
}
