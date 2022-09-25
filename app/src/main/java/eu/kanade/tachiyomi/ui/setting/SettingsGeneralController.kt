package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceScreen
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.LocaleHelper
import org.xmlpull.v1.XmlPullParser
import uy.kohesive.injekt.injectLazy

class SettingsGeneralController : SettingsController() {

    private val libraryPreferences: LibraryPreferences by injectLazy()

    // SY -->
    private val uiPreferences: UiPreferences by injectLazy()
    private val unsortedPreferences: UnsortedPreferences by injectLazy()
    // SY <--

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_general

        switchPreference {
            bindTo(libraryPreferences.showUpdatesNavBadge())
            titleRes = R.string.pref_library_update_show_tab_badge
        }
        switchPreference {
            bindTo(preferences.confirmExit())
            titleRes = R.string.pref_confirm_exit
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            preference {
                key = "pref_manage_notifications"
                titleRes = R.string.pref_manage_notifications
                onClick {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    startActivity(intent)
                }
            }
        }
        listPreference {
            key = "app_lang"
            isPersistent = false
            titleRes = R.string.pref_app_language

            val langs = mutableListOf<Pair<String, String>>()

            val parser = context.resources.getXml(R.xml.locales_config)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "name") {
                            val langTag = parser.getAttributeValue(i)
                            val displayName = LocaleHelper.getDisplayName(langTag)
                            if (displayName.isNotEmpty()) {
                                langs.add(Pair(langTag, displayName))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            langs.sortBy { it.second }
            langs.add(0, Pair("", context.getString(R.string.label_default)))

            entryValues = langs.map { it.first }.toTypedArray()
            entries = langs.map { it.second }.toTypedArray()
            summary = "%s"
            value = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: ""

            onChange { newValue ->
                val locale = if ((newValue as String).isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(newValue)
                }
                AppCompatDelegate.setApplicationLocales(locale)
                true
            }
        }
        // --> EXH
        preferenceCategory {
            titleRes = R.string.pref_category_fork

            switchPreference {
                bindTo(uiPreferences.expandFilters())
                titleRes = R.string.toggle_expand_search_filters
            }

            switchPreference {
                bindTo(unsortedPreferences.autoSolveCaptcha())
                titleRes = R.string.auto_solve_captchas
                summaryRes = R.string.auto_solve_captchas_summary
            }

            switchPreference {
                bindTo(uiPreferences.recommendsInOverflow())
                titleRes = R.string.put_recommends_in_overflow
                summaryRes = R.string.put_recommends_in_overflow_summary
            }

            switchPreference {
                bindTo(uiPreferences.mergeInOverflow())
                titleRes = R.string.put_merge_in_overflow
                summaryRes = R.string.put_merge_in_overflow_summary
            }
        }
        // <-- EXH
    }
}
