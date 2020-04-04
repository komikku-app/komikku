package eu.kanade.tachiyomi.ui.setting

import android.os.Build
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.preference.*
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.ui.lock.FingerLockPreference
import exh.ui.lock.LockPreference
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

class SettingsGeneralController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_general

        listPreference {
            key = Keys.lang
            titleRes = R.string.pref_language
            entryValues = arrayOf("", "ar", "bg", "bn", "ca", "cs", "de", "el", "en-US", "en-GB",
                    "es", "fr", "hi", "hu", "in", "it", "ja", "ko", "lv", "ms", "nb-rNO", "nl", "pl", "pt",
                    "pt-BR", "ro", "ru", "sc", "sr", "sv", "th", "tl", "tr", "uk", "vi", "zh-rCN")
            entries = entryValues.map { value ->
                val locale = LocaleHelper.getLocaleFromString(value.toString())
                locale?.getDisplayName(locale)?.capitalize()
                        ?: context.getString(R.string.system_default)
            }.toTypedArray()
            defaultValue = ""
            summary = "%s"

            onChange { newValue ->
                val activity = activity ?: return@onChange false
                val app = activity.application
                LocaleHelper.changeLocale(newValue.toString())
                LocaleHelper.updateConfiguration(app, app.resources.configuration)
                activity.recreate()
                true
            }
        }
        listPreference {
            key = Keys.dateFormat
            titleRes = R.string.pref_date_format
            entryValues = arrayOf("", "MM/dd/yy", "dd/MM/yy", "yyyy-MM-dd")
            entries = entryValues.map { value ->
                if (value == "") {
                    context.getString(R.string.system_default)
                } else {
                    value
                }
            }.toTypedArray()
            defaultValue = ""
            summary = "%s"
        }
        listPreference {
            key = Keys.themeMode
            titleRes = R.string.pref_theme_mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                entriesRes = arrayOf(
                        R.string.theme_light,
                        R.string.theme_dark,
                        R.string.theme_system)
                entryValues = arrayOf(
                        Values.THEME_MODE_LIGHT,
                        Values.THEME_MODE_DARK,
                        Values.THEME_MODE_SYSTEM)
            } else {
                entriesRes = arrayOf(
                        R.string.theme_light,
                        R.string.theme_dark)
                entryValues = arrayOf(
                        Values.THEME_MODE_LIGHT,
                        Values.THEME_MODE_DARK)
            }
            defaultValue = Values.THEME_MODE_LIGHT
            summary = "%s"

            onChange {
                activity?.recreate()
                true
            }
        }
        listPreference {
            key = Keys.themeDark
            titleRes = R.string.pref_theme_dark
            entriesRes = arrayOf(
                    R.string.theme_dark_default,
                    R.string.theme_dark_amoled,
                    R.string.theme_dark_blue)
            entryValues = arrayOf(
                    Values.THEME_DARK_DEFAULT,
                    Values.THEME_DARK_AMOLED,
                    Values.THEME_DARK_BLUE)
            defaultValue = Values.THEME_DARK_DEFAULT
            summary = "%s"

            onChange {
                if (preferences.themeMode() != Values.THEME_MODE_LIGHT) {
                    activity?.recreate()
                }
                true
            }
        }
        intListPreference {
            key = Keys.startScreen
            titleRes = R.string.pref_start_screen
            entriesRes = arrayOf(R.string.label_library, R.string.label_recent_manga,
                    R.string.label_recent_updates)
            entryValues = arrayOf("1", "2", "3")
            defaultValue = "1"
            summary = "%s"
        }

        // --> EXH
        switchPreference {
            key = Keys.eh_expandFilters
            title = "Expand all search filters by default"
            defaultValue = false
        }

        switchPreference {
            key = Keys.eh_autoSolveCaptchas
            title = "Automatically solve captcha"
            summary = "Use HIGHLY EXPERIMENTAL automatic ReCAPTCHA solver. Will be grayed out if unsupported by your device."
            defaultValue = false
            shouldDisableView = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        }

        preferenceCategory {
            title = "Application lock"

            LockPreference(context).apply {
                key = "pref_app_lock" // Not persistent so use random key
                isPersistent = false

                addPreference(this)
            }

            FingerLockPreference(context).apply {
                key = "pref_lock_finger" // Not persistent so use random key
                isPersistent = false

                addPreference(this)

                //Call after addPreference
                dependency = "pref_app_lock"
            }

            switchPreference {
                key = Keys.eh_lock_manually

                title = "Lock manually only"
                summary = "Disable automatic app locking. The app can still be locked manually by long-pressing the three-lines/back button in the top left corner."
                defaultValue = false
            }
            switchPreference {
                key = Keys.secureScreen
                title = "Enable Secure Screen"
                defaultValue = false
            }
        }
        // <-- EXH
    }

}
