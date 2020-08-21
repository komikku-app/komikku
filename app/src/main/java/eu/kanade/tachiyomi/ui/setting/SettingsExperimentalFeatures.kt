package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.editTextPreference
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes

class SettingsExperimentalFeatures : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.expermental_feature_sttings

        preferenceCategory {
            titleRes = R.string.data_saver
            summaryRes = R.string.data_saver_summary

            switchPreference {
                titleRes = R.string.enable_data_saver
                key = Keys.dataSaver
                defaultValue = false
            }

            switchPreference {
                titleRes = R.string.ignore_jpeg
                key = Keys.ignoreJpeg
                defaultValue = false
            }

            switchPreference {
                titleRes = R.string.ignore_gif
                key = Keys.ignoreGif
                defaultValue = true
            }

            intListPreference {
                titleRes = R.string.data_saver_image_quality
                key = Keys.dataSaverImageQuality
                entries = arrayOf("10", "20", "40", "50", "70", "80", "90", "95")
                entryValues = entries
                defaultValue = "80"
            }

            switchPreference {
                titleRes = R.string.data_saver_image_format
                key = Keys.dataSaverImageFormatJpeg
                defaultValue = false
                summaryRes = R.string.data_saver_image_format_summary
            }

            switchPreference {
                titleRes = R.string.data_saver_color_bw
                key = Keys.dataSaverColorBW
                defaultValue = false
            }

            editTextPreference {
                titleRes = R.string.data_saver_server
                key = Keys.dataSaverServer
                defaultValue = ""
                summaryRes = R.string.data_saver_server_summary
            }
        }
    }
}
