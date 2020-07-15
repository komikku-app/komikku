package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes

/**
 * nhentai Settings fragment
 */

class SettingsNhController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_nh

        switchPreference {
            titleRes = R.string.high_quality_thumbnails
            summaryRes = R.string.high_quality_thumbnails_summary
            key = PreferenceKeys.eh_nh_useHighQualityThumbs
            defaultValue = false
        }
    }
}
