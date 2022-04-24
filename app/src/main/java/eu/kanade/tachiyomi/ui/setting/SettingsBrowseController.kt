package eu.kanade.tachiyomi.ui.setting

import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.category.repos.RepoController
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryController
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.infoPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.requireAuthentication
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsBrowseController : SettingsController() {

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.browse

        // SY -->
        preferenceCategory {
            titleRes = R.string.label_sources

            preference {
                key = "pref_edit_source_categories"
                titleRes = R.string.action_edit_categories

                val catCount = preferences.sourcesTabCategories().get().count()
                summary = context.resources.getQuantityString(R.plurals.num_categories, catCount, catCount)

                onClick {
                    router.pushController(SourceCategoryController())
                }
            }
            switchPreference {
                bindTo(preferences.sourcesTabCategoriesFilter())
                titleRes = R.string.pref_source_source_filtering
                summaryRes = R.string.pref_source_source_filtering_summery
            }
            switchPreference {
                bindTo(preferences.useNewSourceNavigation())
                titleRes = R.string.pref_source_navigation
                summaryRes = R.string.pref_source_navigation_summery
            }
            switchPreference {
                bindTo(preferences.allowLocalSourceHiddenFolders())
                titleRes = R.string.pref_local_source_hidden_folders
                summaryRes = R.string.pref_local_source_hidden_folders_summery
            }
        }

        preferenceCategory {
            titleRes = R.string.feed

            switchPreference {
                bindTo(preferences.feedTabInFront())
                titleRes = R.string.pref_feed_position
                summaryRes = R.string.pref_feed_position_summery
            }
        }
        // SY <--

        preferenceCategory {
            titleRes = R.string.label_sources

            switchPreference {
                bindTo(preferences.duplicatePinnedSources())
                titleRes = R.string.pref_duplicate_pinned_sources
                summaryRes = R.string.pref_duplicate_pinned_sources_summary
            }
        }

        preferenceCategory {
            titleRes = R.string.label_extensions

            switchPreference {
                bindTo(preferences.automaticExtUpdates())
                titleRes = R.string.pref_enable_automatic_extension_updates

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
                    router.pushController(RepoController())
                }
            }
            // SY <--
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

            switchPreference {
                bindTo(preferences.showNsfwSource())
                titleRes = R.string.pref_show_nsfw_source
                summaryRes = R.string.requires_app_restart

                if (context.isAuthenticationSupported() && activity != null) {
                    requireAuthentication(
                        activity as? FragmentActivity,
                        context.getString(R.string.pref_category_nsfw_content),
                        context.getString(R.string.confirm_lock_change),
                    )
                }
            }

            infoPreference(R.string.parental_controls_info)
        }
    }
}
