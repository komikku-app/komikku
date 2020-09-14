package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import exh.md.utils.MdUtil
import exh.widget.preference.MangaDexLoginPreference
import exh.widget.preference.MangadexLoginDialog
import exh.widget.preference.MangadexLogoutDialog

class SettingsMangaDexController :
    SettingsController(),
    MangadexLoginDialog.Listener,
    MangadexLogoutDialog.Listener {

    private val mdex by lazy { MdUtil.getEnabledMangaDex() }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.mangadex_specific_settings
        if (mdex == null) router.popCurrentController()
        val sourcePreference = MangaDexLoginPreference(context, mdex!!).apply {
            title = mdex!!.name + " Login"
            key = getSourceKey(source.id)
            setOnLoginClickListener {
                if (mdex!!.isLogged()) {
                    val dialog = MangadexLogoutDialog(source)
                    dialog.targetController = this@SettingsMangaDexController
                    dialog.showDialog(router)
                } else {
                    val dialog = MangadexLoginDialog(source)
                    dialog.targetController = this@SettingsMangaDexController
                    dialog.showDialog(router)
                }
            }
        }

        preferenceScreen.addPreference(sourcePreference)

        listPreference {
            titleRes = R.string.mangadex_preffered_source
            summaryRes = R.string.mangadex_preffered_source_summary
            key = PreferenceKeys.preferredMangaDexId
            val mangaDexs = MdUtil.getEnabledMangaDexs()
            entries = mangaDexs.map { it.toString() }.toTypedArray()
            entryValues = mangaDexs.map { it.id.toString() }.toTypedArray()
            /*setOnPreferenceChangeListener { preference, newValue ->
                preferences.preferredMangaDexId().set((newValue as? String)?.toLongOrNull() ?: 0)
                true
            }*/
        }

        switchPreference {
            key = PreferenceKeys.mangaDexLowQualityCovers
            titleRes = R.string.mangadex_low_quality_covers
            defaultValue = false
        }

        switchPreference {
            key = PreferenceKeys.mangaDexForceLatestCovers
            titleRes = R.string.mangadex_use_latest_cover
            summaryRes = R.string.mangadex_use_latest_cover_summary
            defaultValue = false
        }

        preference {
            titleRes = R.string.mangadex_sync_follows_to_library
            summaryRes = R.string.mangadex_sync_follows_to_library_summary

            onClick {
                LibraryUpdateService.start(
                    context,
                    target = LibraryUpdateService.Target.SYNC_FOLLOWS
                )
            }
        }
    }

    override fun siteLoginDialogClosed(source: Source) {
        val pref = findPreference(getSourceKey(source.id)) as? MangaDexLoginPreference
        pref?.notifyChanged()
    }

    override fun siteLogoutDialogClosed(source: Source) {
        val pref = findPreference(getSourceKey(source.id)) as? MangaDexLoginPreference
        pref?.notifyChanged()
    }

    private fun getSourceKey(sourceId: Long): String {
        return "source_$sourceId"
    }
}
