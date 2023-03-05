package eu.kanade.presentation.more.settings.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.repos.RepoScreen
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsBrowseScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.browse

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        // SY -->
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val unsortedPreferences = remember { Injekt.get<UnsortedPreferences>() }
        // SY <--
        return listOf(
            // SY -->
            Preference.PreferenceGroup(
                title = stringResource(R.string.label_sources),
                preferenceItems = listOf(
                    kotlin.run {
                        val navigator = LocalNavigator.currentOrThrow
                        val count by sourcePreferences.sourcesTabCategories().collectAsState()
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(R.string.action_edit_categories),
                            subtitle = pluralStringResource(R.plurals.num_categories, count.size, count.size),
                            onClick = {
                                navigator.push(SourceCategoryScreen())
                            },
                        )
                    },
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.sourcesTabCategoriesFilter(),
                        title = stringResource(R.string.pref_source_source_filtering),
                        subtitle = stringResource(R.string.pref_source_source_filtering_summery),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = uiPreferences.useNewSourceNavigation(),
                        title = stringResource(R.string.pref_source_navigation),
                        subtitle = stringResource(R.string.pref_source_navigation_summery),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = unsortedPreferences.allowLocalSourceHiddenFolders(),
                        title = stringResource(R.string.pref_local_source_hidden_folders),
                        subtitle = stringResource(R.string.pref_local_source_hidden_folders_summery),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.feed),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = uiPreferences.feedTabInFront(),
                        title = stringResource(R.string.pref_feed_position),
                        subtitle = stringResource(R.string.pref_feed_position_summery),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.label_extensions),
                preferenceItems = listOf(
                    kotlin.run {
                        val navigator = LocalNavigator.currentOrThrow
                        val count by unsortedPreferences.extensionRepos().collectAsState()
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(R.string.action_edit_repos),
                            subtitle = pluralStringResource(R.plurals.num_repos, count.size, count.size),
                            onClick = {
                                navigator.push(RepoScreen())
                            },
                        )
                    },
                ),
            ),
            // SY <--
            Preference.PreferenceGroup(
                title = stringResource(R.string.label_sources),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.searchPinnedSourcesOnly(),
                        title = stringResource(R.string.pref_search_pinned_sources_only),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.hideInLibraryItems(),
                        title = stringResource(R.string.pref_hide_in_library_items),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_nsfw_content),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.showNsfwSource(),
                        title = stringResource(R.string.pref_show_nsfw_source),
                        subtitle = stringResource(R.string.requires_app_restart),
                        onValueChanged = {
                            (context as FragmentActivity).authenticate(
                                title = context.getString(R.string.pref_category_nsfw_content),
                            )
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(R.string.parental_controls_info)),
                ),
            ),
        )
    }
}
