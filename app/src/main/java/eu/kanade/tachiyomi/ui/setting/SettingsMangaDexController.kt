package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.toast
import exh.md.similar.SimilarUpdateJob
import exh.md.utils.MdUtil
import exh.widget.preference.MangaDexLoginPreference
import exh.widget.preference.MangadexLoginDialog
import exh.widget.preference.MangadexLogoutDialog
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn

class SettingsMangaDexController :
    SettingsController(),
    MangadexLoginDialog.Listener,
    MangadexLogoutDialog.Listener {

    private val mdex by lazy { MdUtil.getEnabledMangaDex(preferences) }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.mangadex_specific_settings
        val mdex = mdex ?: return@apply
        val sourcePreference = MangaDexLoginPreference(context, mdex).apply {
            title = mdex.name + " Login"
            key = getSourceKey(source.id)
            setOnLoginClickListener {
                if (mdex.isLogged()) {
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

        addPreference(sourcePreference)

        listPreference {
            titleRes = R.string.mangadex_preffered_source
            summaryRes = R.string.mangadex_preffered_source_summary
            key = PreferenceKeys.preferredMangaDexId
            val mangaDexs = MdUtil.getEnabledMangaDexs(preferences)
            entries = mangaDexs.map { it.toString() }.toTypedArray()
            entryValues = mangaDexs.map { it.id.toString() }.toTypedArray()
        }

        switchPreference {
            key = PreferenceKeys.mangaDexForceLatestCovers
            titleRes = R.string.mangadex_use_latest_cover
            summaryRes = R.string.mangadex_use_latest_cover_summary
            defaultValue = false
        }

        preference {
            key = "pref_sync_mangadex_into_this"
            titleRes = R.string.mangadex_sync_follows_to_library
            summaryRes = R.string.mangadex_sync_follows_to_library_summary

            onClick {
                LibraryUpdateService.start(
                    context,
                    target = LibraryUpdateService.Target.SYNC_FOLLOWS
                )
            }
        }

        preference {
            titleRes = R.string.mangadex_push_favorites_to_mangadex
            summaryRes = R.string.mangadex_push_favorites_to_mangadex_summary

            onClick {
                LibraryUpdateService.start(
                    context,
                    target = LibraryUpdateService.Target.PUSH_FAVORITES
                )
            }
        }

        preferenceCategory {
            titleRes = R.string.similar_settings

            preference {
                key = "pref_similar_screen"
                summaryRes = R.string.similar_screen_summary_message
                isIconSpaceReserved = true
            }

            switchPreference {
                key = PreferenceKeys.mangadexSimilarEnabled
                titleRes = R.string.similar_screen
                defaultValue = false
                onClick {
                    SimilarUpdateJob.setupTask(context)
                }
            }

            switchPreference {
                key = PreferenceKeys.mangadexSimilarOnlyOverWifi
                titleRes = R.string.pref_download_only_over_wifi
                defaultValue = true
                onClick {
                    SimilarUpdateJob.setupTask(context, true)
                }
            }

            preference {
                key = "pref_similar_manually_update"
                titleRes = R.string.similar_manually_update
                summaryRes = R.string.similar_manually_update_message
                onClick {
                    SimilarUpdateJob.doWorkNow(context)
                    context.toast(R.string.similar_manually_toast)
                }
            }

            intListPreference {
                key = PreferenceKeys.mangadexSimilarUpdateInterval
                titleRes = R.string.similar_update_fequency
                entriesRes = arrayOf(
                    R.string.update_never,
                    R.string.update_24hour,
                    R.string.update_48hour,
                    R.string.update_weekly,
                    R.string.update_monthly
                )
                entryValues = arrayOf("0", "1", "2", "7", "30")
                defaultValue = "2"

                preferences.mangadexSimilarUpdateInterval()
                    .asImmediateFlow {
                        SimilarUpdateJob.setupTask(context, true)
                    }
                    .drop(1)
                    .launchIn(viewScope)
            }

            preference {
                key = "similar_credits"
                titleRes = R.string.similar_credit
                val url = "https://github.com/goldbattle/MangadexRecomendations"
                summary = context.getString(R.string.similar_credit_message, url)
                onClick {
                    openInBrowser(url)
                }
                isIconSpaceReserved = true
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
