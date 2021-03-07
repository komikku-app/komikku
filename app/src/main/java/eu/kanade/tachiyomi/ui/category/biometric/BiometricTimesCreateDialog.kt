package eu.kanade.tachiyomi.ui.category.biometric

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.datetime.timePicker
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import exh.log.xLogD
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.hours
import kotlin.time.minutes

/**
 * Dialog to create a new category for the library.
 */
@OptIn(ExperimentalTime::class)
class BiometricTimesCreateDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
        where T : Controller, T : BiometricTimesCreateDialog.Listener {

    /**
     * Name of the new category. Value updated with each input from the user.
     */
    private var startTime: Duration? = null

    private var endTime: Duration? = null

    constructor(target: T) : this() {
        targetController = target
    }

    constructor(target: T, startTime: Duration) : this() {
        targetController = target
        this.startTime = startTime
    }

    /**
     * Called when creating the dialog for this controller.
     *
     * @param savedViewState The saved state of this dialog.
     * @return a new dialog instance.
     */
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog(activity!!)
            .title(if (startTime == null) R.string.biometric_lock_start_time else R.string.biometric_lock_end_time)
            .timePicker(show24HoursView = false) { _, datetime ->
                val hour = datetime.get(Calendar.HOUR_OF_DAY)
                xLogD(hour)
                val minute = datetime.get(Calendar.MINUTE)
                xLogD(minute)
                if (hour !in 0..24 || minute !in 0..60) return@timePicker
                if (startTime != null) {
                    endTime = hour.hours + minute.minutes
                } else {
                    startTime = hour.hours + minute.minutes
                }
            }
            .negativeButton(android.R.string.cancel)
            .positiveButton(android.R.string.ok) {
                if (endTime != null) {
                    (targetController as? Listener)?.createTimeRange(startTime, endTime)
                } else {
                    (targetController as? Listener)?.startNextDialog(startTime)
                }
            }
    }

    interface Listener {
        fun startNextDialog(startTime: Duration?)
        fun createTimeRange(startTime: Duration?, endTime: Duration?)
    }
}
