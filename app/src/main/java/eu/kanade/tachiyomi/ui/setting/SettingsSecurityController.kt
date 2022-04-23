package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Bundle
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesController
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.infoPreference
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.requireAuthentication
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsSecurityController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_security

        if (context.isAuthenticationSupported()) {
            switchPreference {
                bindTo(preferences.useAuthenticator())
                titleRes = R.string.lock_with_biometrics

                requireAuthentication(
                    activity as? FragmentActivity,
                    context.getString(R.string.lock_with_biometrics),
                    context.getString(R.string.confirm_lock_change),
                )
            }

            intListPreference {
                bindTo(preferences.lockAppAfter())
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
                summary = "%s"
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    if (value == newValue) return@OnPreferenceChangeListener false

                    (activity as? FragmentActivity)?.startAuthentication(
                        activity!!.getString(R.string.lock_when_idle),
                        activity!!.getString(R.string.confirm_lock_change),
                        callback = object : AuthenticatorUtil.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                activity: FragmentActivity?,
                                result: BiometricPrompt.AuthenticationResult,
                            ) {
                                super.onAuthenticationSucceeded(activity, result)
                                value = newValue as String
                            }

                            override fun onAuthenticationError(
                                activity: FragmentActivity?,
                                errorCode: Int,
                                errString: CharSequence,
                            ) {
                                super.onAuthenticationError(activity, errorCode, errString)
                                activity?.toast(errString.toString())
                            }
                        },
                    )
                    false
                }

                visibleIf(preferences.useAuthenticator()) { it }
            }
        }

        switchPreference {
            key = Keys.hideNotificationContent
            titleRes = R.string.hide_notification_content
            defaultValue = false
        }

        listPreference {
            bindTo(preferences.secureScreen())
            titleRes = R.string.secure_screen
            summary = "%s"
            entriesRes = PreferenceValues.SecureScreenMode.values().map { it.titleResId }.toTypedArray()
            entryValues = PreferenceValues.SecureScreenMode.values().map { it.name }.toTypedArray()
        }

        // SY -->
        preference {
            key = "pref_edit_lock_times"
            titleRes = R.string.action_edit_biometric_lock_times

            val timeRanges = preferences.authenticatorTimeRanges().get().size
            summary = context.resources.getQuantityString(R.plurals.num_lock_times, timeRanges, timeRanges)

            visibleIf(preferences.useAuthenticator()) { it }

            onClick {
                router.pushController(BiometricTimesController())
            }
        }
        preference {
            key = "pref_edit_lock_days"
            titleRes = R.string.biometric_lock_days
            summaryRes = R.string.biometric_lock_days_summary

            visibleIf(preferences.useAuthenticator()) { it }

            onClick {
                SetLockedDaysDialog().showDialog(router)
            }
        }
        // SY <--

        infoPreference(R.string.secure_screen_summary)
    }

    // SY -->
    class SetLockedDaysDialog(bundle: Bundle? = null) : DialogController(bundle) {
        val preferences: PreferencesHelper by injectLazy()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val options = listOf(
                R.string.sunday,
                R.string.monday,
                R.string.tuesday,
                R.string.wednesday,
                R.string.thursday,
                R.string.friday,
                R.string.saturday,
            )
                .map { activity.getString(it) }
                .toTypedArray()

            val lockDays = preferences.authenticatorDays().get()
            val selection = BooleanArray(7) {
                when (it) {
                    0 -> (lockDays and SecureActivityDelegate.LOCK_SUNDAY) == SecureActivityDelegate.LOCK_SUNDAY
                    1 -> (lockDays and SecureActivityDelegate.LOCK_MONDAY) == SecureActivityDelegate.LOCK_MONDAY
                    2 -> (lockDays and SecureActivityDelegate.LOCK_TUESDAY) == SecureActivityDelegate.LOCK_TUESDAY
                    3 -> (lockDays and SecureActivityDelegate.LOCK_WEDNESDAY) == SecureActivityDelegate.LOCK_WEDNESDAY
                    4 -> (lockDays and SecureActivityDelegate.LOCK_THURSDAY) == SecureActivityDelegate.LOCK_THURSDAY
                    5 -> (lockDays and SecureActivityDelegate.LOCK_FRIDAY) == SecureActivityDelegate.LOCK_FRIDAY
                    6 -> (lockDays and SecureActivityDelegate.LOCK_SATURDAY) == SecureActivityDelegate.LOCK_SATURDAY
                    else -> false
                }
            }

            return MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.biometric_lock_days)
                .setMultiChoiceItems(
                    options,
                    selection,
                ) { _, which, selected ->
                    selection[which] = selected
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    var flags = 0
                    selection.forEachIndexed { index, checked ->
                        if (checked) {
                            when (index) {
                                0 -> flags = flags or SecureActivityDelegate.LOCK_SUNDAY
                                1 -> flags = flags or SecureActivityDelegate.LOCK_MONDAY
                                2 -> flags = flags or SecureActivityDelegate.LOCK_TUESDAY
                                3 -> flags = flags or SecureActivityDelegate.LOCK_WEDNESDAY
                                4 -> flags = flags or SecureActivityDelegate.LOCK_THURSDAY
                                5 -> flags = flags or SecureActivityDelegate.LOCK_FRIDAY
                                6 -> flags = flags or SecureActivityDelegate.LOCK_SATURDAY
                            }
                        }
                    }

                    preferences.authenticatorDays().set(flags)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }
    // SY <--
}
