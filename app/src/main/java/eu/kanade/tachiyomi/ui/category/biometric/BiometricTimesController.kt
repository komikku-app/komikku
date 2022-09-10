package eu.kanade.tachiyomi.ui.category.biometric

import androidx.compose.runtime.Composable
import com.google.android.material.timepicker.MaterialTimePicker
import eu.kanade.presentation.category.BiometricTimesScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Controller to manage the lock times for the biometric lock.
 */
class BiometricTimesController : FullComposeController<BiometricTimesPresenter>() {

    override fun createPresenter() = BiometricTimesPresenter()

    @Composable
    override fun ComposeContent() {
        BiometricTimesScreen(
            presenter = presenter,
            navigateUp = router::popCurrentController,
            openCreateDialog = ::showTimePicker,
        )
    }

    private fun showTimePicker(startTime: Duration? = null) {
        val picker = MaterialTimePicker.Builder()
            .setTitleText(if (startTime == null) R.string.biometric_lock_start_time else R.string.biometric_lock_end_time)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()
        picker.addOnPositiveButtonClickListener {
            val timeRange = picker.hour.hours + picker.minute.minutes
            if (startTime != null) {
                presenter.dialog = null
                presenter.createTimeRange(TimeRange(startTime, timeRange))
            } else {
                showTimePicker(timeRange)
            }
        }
        picker.addOnDismissListener {
            presenter.dialog = null
        }
        picker.show((activity as MainActivity).supportFragmentManager, null)
    }
}
