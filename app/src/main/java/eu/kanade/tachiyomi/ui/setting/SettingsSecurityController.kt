package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Bundle
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesController
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import kotlinx.coroutines.flow.launchIn
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsSecurityController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_security

        if (AuthenticatorUtil.isSupported(context)) {
            switchPreference {
                key = Keys.useAuthenticator
                titleRes = R.string.lock_with_biometrics
                defaultValue = false
            }
            intListPreference {
                key = Keys.lockAppAfter
                titleRes = R.string.lock_when_idle
                val values = arrayOf("0", "1", "2", "5", "10", "-1")
                entries = values.mapNotNull {
                    when (it) {
                        "-1" -> context.getString(R.string.lock_never)
                        "0" -> context.getString(R.string.lock_always)
                        else -> resources?.getQuantityString(R.plurals.lock_after_mins, it.toInt(), it)
                    }
                }.toTypedArray()
                entryValues = values
                defaultValue = "0"
                summary = "%s"

                preferences.useAuthenticator().asImmediateFlow { isVisible = it }
                    .launchIn(viewScope)
            }
        }

        switchPreference {
            key = Keys.secureScreen
            titleRes = R.string.secure_screen
            summaryRes = R.string.secure_screen_summary
            defaultValue = false
        }
        switchPreference {
            key = Keys.hideNotificationContent
            titleRes = R.string.hide_notification_content
            defaultValue = false
        }
        preference {
            key = "pref_edit_lock_times"
            titleRes = R.string.action_edit_biometric_lock_times

            val timeRanges = preferences.authenticatorTimeRanges().get().size
            summary = context.resources.getQuantityString(R.plurals.num_lock_times, timeRanges, timeRanges)

            preferences.useAuthenticator().asImmediateFlow { isVisible = it }
                .launchIn(viewScope)

            onClick {
                router.pushController(BiometricTimesController().withFadeTransaction())
            }
        }
        preference {
            key = "pref_edit_lock_days"
            titleRes = R.string.biometric_lock_days
            summaryRes = R.string.biometric_lock_days_summary

            preferences.useAuthenticator().asImmediateFlow { isVisible = it }
                .launchIn(viewScope)

            onClick {
                SetLockedDaysDialog().showDialog(router)
            }
        }
    }

    class SetLockedDaysDialog(bundle: Bundle? = null) : DialogController(bundle) {
        val preferences: PreferencesHelper by injectLazy()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val options = arrayOf(
                R.string.sunday,
                R.string.monday,
                R.string.tuesday,
                R.string.wednesday,
                R.string.thursday,
                R.string.friday,
                R.string.saturday
            )
                .map { activity.getString(it) }

            val lockDays = preferences.authenticatorDays().get()
            val initialSelection = List(7) {
                val locked = when (it) {
                    0 -> (lockDays and SecureActivityDelegate.LOCK_SUNDAY) == SecureActivityDelegate.LOCK_SUNDAY
                    1 -> (lockDays and SecureActivityDelegate.LOCK_MONDAY) == SecureActivityDelegate.LOCK_MONDAY
                    2 -> (lockDays and SecureActivityDelegate.LOCK_TUESDAY) == SecureActivityDelegate.LOCK_TUESDAY
                    3 -> (lockDays and SecureActivityDelegate.LOCK_WEDNESDAY) == SecureActivityDelegate.LOCK_WEDNESDAY
                    4 -> (lockDays and SecureActivityDelegate.LOCK_THURSDAY) == SecureActivityDelegate.LOCK_THURSDAY
                    5 -> (lockDays and SecureActivityDelegate.LOCK_FRIDAY) == SecureActivityDelegate.LOCK_FRIDAY
                    6 -> (lockDays and SecureActivityDelegate.LOCK_SATURDAY) == SecureActivityDelegate.LOCK_SATURDAY
                    else -> false
                }
                if (locked) {
                    it
                } else null
            }.filterNotNull().toIntArray()

            return MaterialDialog(activity)
                .title(R.string.biometric_lock_days)
                .message(R.string.biometric_lock_days_summary)
                .listItemsMultiChoice(
                    items = options,
                    initialSelection = initialSelection
                ) { _, positions, _ ->
                    var flags = 0
                    positions.forEach {
                        when (it) {
                            0 -> flags = flags or SecureActivityDelegate.LOCK_SUNDAY
                            1 -> flags = flags or SecureActivityDelegate.LOCK_MONDAY
                            2 -> flags = flags or SecureActivityDelegate.LOCK_TUESDAY
                            3 -> flags = flags or SecureActivityDelegate.LOCK_WEDNESDAY
                            4 -> flags = flags or SecureActivityDelegate.LOCK_THURSDAY
                            5 -> flags = flags or SecureActivityDelegate.LOCK_FRIDAY
                            6 -> flags = flags or SecureActivityDelegate.LOCK_SATURDAY
                        }
                    }

                    preferences.authenticatorDays().set(flags)
                }
                .positiveButton(android.R.string.ok)
                .negativeButton(android.R.string.cancel)
        }
    }
}
