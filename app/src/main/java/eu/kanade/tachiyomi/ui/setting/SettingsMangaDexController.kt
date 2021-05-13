package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.titleRes
import exh.md.utils.MdUtil
import exh.widget.preference.MangaDexLoginPreference
import exh.widget.preference.MangadexLoginDialog
import exh.widget.preference.MangadexLogoutDialog

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

        /*switchPreference {
            key = PreferenceKeys.mangaDexForceLatestCovers
            titleRes = R.string.mangadex_use_latest_cover
            summaryRes = R.string.mangadex_use_latest_cover_summary
            defaultValue = false
        }*/

        preference {
            key = "pref_sync_mangadex_into_this"
            titleRes = R.string.mangadex_sync_follows_to_library
            summaryRes = R.string.mangadex_sync_follows_to_library_summary

            onClick {
                MaterialDialog(context)
                    .title(R.string.mangadex_sync_follows_to_library)
                    .message(R.string.mangadex_sync_follows_to_library_message)
                    .listItemsMultiChoice(
                        items = context.resources.getStringArray(R.array.md_follows_options).toList().let { it.subList(1, it.size) },
                        initialSelection = intArrayOf(0, 5)
                    ) { _, indices, _ ->
                        preferences.mangadexSyncToLibraryIndexes().set(indices.map { (it + 1).toString() }.toSet())
                        LibraryUpdateService.start(
                            context,
                            target = LibraryUpdateService.Target.SYNC_FOLLOWS
                        )
                    }
                    .positiveButton(android.R.string.ok)
                    .negativeButton(android.R.string.cancel)
                    .show()
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
