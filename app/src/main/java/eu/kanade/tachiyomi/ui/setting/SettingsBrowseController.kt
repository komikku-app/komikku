package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.extension.repos.RepoController
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes

class SettingsBrowseController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.browse

        preferenceCategory {
            titleRes = R.string.label_extensions

            switchPreference {
                key = Keys.automaticExtUpdates
                titleRes = R.string.pref_enable_automatic_extension_updates
                defaultValue = true

                onChange { newValue ->
                    val checked = newValue as Boolean
                    ExtensionUpdateJob.setupTask(activity!!, checked)
                    true
                }
            }
            // SY -->
            preference {
                key = "pref_edit_extension_repos"
                titleRes = R.string.action_edit_repos

                val catCount = preferences.extensionRepos().get().count()
                summary = context.resources.getQuantityString(R.plurals.num_repos, catCount, catCount)

                onClick {
                    router.pushController(RepoController().withFadeTransaction())
                }
            }
            // SY <--

            listPreference {
                key = Keys.allowNsfwSource
                titleRes = R.string.pref_allow_nsfw_sources
                entriesRes = arrayOf(
                    R.string.pref_allow_nsfw_sources_allowed,
                    R.string.pref_allow_nsfw_sources_allowed_multisource,
                    R.string.pref_allow_nsfw_sources_blocked
                )
                entryValues = arrayOf(
                    Values.NsfwAllowance.ALLOWED.name,
                    Values.NsfwAllowance.PARTIAL.name,
                    Values.NsfwAllowance.BLOCKED.name
                )
                defaultValue = Values.NsfwAllowance.ALLOWED.name
                summary = "%s"
            }
        }

        preferenceCategory {
            titleRes = R.string.action_global_search

            switchPreference {
                key = Keys.searchPinnedSourcesOnly
                titleRes = R.string.pref_search_pinned_sources_only
                defaultValue = false
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_nsfw_content

            listPreference {
                key = Keys.allowNsfwSource
                titleRes = R.string.pref_allow_nsfw_sources
                entriesRes = arrayOf(
                    R.string.pref_allow_nsfw_sources_allowed,
                    R.string.pref_allow_nsfw_sources_allowed_multisource,
                    R.string.pref_allow_nsfw_sources_blocked
                )
                entryValues = arrayOf(
                    Values.NsfwAllowance.ALLOWED.name,
                    Values.NsfwAllowance.PARTIAL.name,
                    Values.NsfwAllowance.BLOCKED.name
                )
                defaultValue = Values.NsfwAllowance.ALLOWED.name
                summary = "%s"
            }
        }
    }
}
