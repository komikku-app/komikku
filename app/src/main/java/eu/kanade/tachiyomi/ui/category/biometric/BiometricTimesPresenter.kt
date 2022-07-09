package eu.kanade.tachiyomi.ui.category.biometric

import android.os.Bundle
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.plusAssign
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [BiometricTimesController]. Used to manage the categories of the library.
 */
class BiometricTimesPresenter : BasePresenter<BiometricTimesController>() {

    /**
     * List containing categories.
     */
    private var timeRanges: List<TimeRange> = emptyList()

    val preferences: PreferencesHelper = Injekt.get()

    /**
     * Called when the presenter is created.
     *
     * @param savedState The saved state of this presenter.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        preferences.authenticatorTimeRanges().asFlow().onEach { prefTimeRanges ->
            timeRanges = prefTimeRanges.toList()
                .mapNotNull(TimeRange::fromPreferenceString)

            withUIContext {
                view?.setBiometricTimeItems(timeRanges.map(::BiometricTimesItem))
            }
        }.launchIn(presenterScope)
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createTimeRange(timeRange: TimeRange) {
        // Do not allow duplicate categories.
        if (timeRangeConflicts(timeRange)) {
            launchUI {
                view?.onTimeRangeConflictsError()
            }
            return
        }

        preferences.authenticatorTimeRanges() += timeRange.toPreferenceString()
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param timeRanges The list of categories to delete.
     */
    fun deleteTimeRanges(timeRanges: List<TimeRange>) {
        preferences.authenticatorTimeRanges().set(
            this.timeRanges.filterNot { it in timeRanges }.map(TimeRange::toPreferenceString).toSet(),
        )
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun timeRangeConflicts(timeRange: TimeRange): Boolean {
        return timeRanges.any { timeRange.conflictsWith(it) }
    }
}
