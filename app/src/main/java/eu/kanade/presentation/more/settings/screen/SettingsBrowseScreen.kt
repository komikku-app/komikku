package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.browse.ExtensionReposScreen
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extensionrepo.interactor.GetExtensionRepoCount
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsBrowseScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsBrowseScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.browse

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val getExtensionRepoCount = remember { Injekt.get<GetExtensionRepoCount>() }

        val reposCount by getExtensionRepoCount.subscribe().collectAsState(0)

        // SY -->
        val scope = rememberCoroutineScope()
        val hideFeedTab by remember { Injekt.get<UiPreferences>().hideFeedTab().asState(scope) }
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        // SY <--
        // KMK -->
        val relatedMangasInOverflow by uiPreferences.expandRelatedMangas().collectAsState()
        // KMK <--
        return listOf(
            // SY -->
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_sources),
                preferenceItems = persistentListOf(
                    // KMK -->
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.relatedMangas(),
                        title = stringResource(KMR.strings.pref_source_related_mangas),
                        subtitle = stringResource(KMR.strings.pref_source_related_mangas_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.expandRelatedMangas(),
                        title = stringResource(KMR.strings.pref_expand_related_mangas),
                        subtitle = stringResource(KMR.strings.pref_expand_related_mangas_summary),
                        enabled = sourcePreferences.relatedMangas().get(),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.relatedMangasInOverflow(),
                        enabled = !relatedMangasInOverflow,
                        title = stringResource(KMR.strings.put_related_mangas_in_overflow),
                        subtitle = stringResource(KMR.strings.put_related_mangas_in_overflow_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.showHomeOnRelatedMangas(),
                        title = stringResource(KMR.strings.pref_show_home_on_related_mangas),
                        subtitle = stringResource(KMR.strings.pref_show_home_on_related_mangas_summary),
                        enabled = sourcePreferences.relatedMangas().get(),
                    ),
                    // KMK <--
                    kotlin.run {
                        val count by sourcePreferences.sourcesTabCategories().collectAsState()
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(MR.strings.action_edit_categories),
                            subtitle = pluralStringResource(MR.plurals.num_categories, count.size, count.size),
                            onClick = {
                                navigator.push(SourceCategoryScreen())
                            },
                        )
                    },
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.sourcesTabCategoriesFilter(),
                        title = stringResource(SYMR.strings.pref_source_source_filtering),
                        subtitle = stringResource(SYMR.strings.pref_source_source_filtering_summery),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.useNewSourceNavigation(),
                        title = stringResource(SYMR.strings.pref_source_navigation),
                        subtitle = stringResource(SYMR.strings.pref_source_navigation_summery),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.allowLocalSourceHiddenFolders(),
                        title = stringResource(SYMR.strings.pref_local_source_hidden_folders),
                        subtitle = stringResource(SYMR.strings.pref_local_source_hidden_folders_summery),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(SYMR.strings.feed),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.hideFeedTab(),
                        title = stringResource(SYMR.strings.pref_hide_feed),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.feedTabInFront(),
                        title = stringResource(SYMR.strings.pref_feed_position),
                        subtitle = stringResource(SYMR.strings.pref_feed_position_summery),
                        enabled = hideFeedTab.not(),
                    ),
                    // KMK -->
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInLibraryFeedItems(),
                        title = stringResource(MR.strings.pref_hide_in_library_items),
                    ),
                    // KMK <--
                ),
            ),
            // SY <--
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_sources),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInLibraryItems(),
                        title = stringResource(MR.strings.pref_hide_in_library_items),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.label_extension_repos),
                        subtitle = pluralStringResource(MR.plurals.num_repos, reposCount, reposCount),
                        onClick = {
                            navigator.push(ExtensionReposScreen())
                        },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_nsfw_content),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.showNsfwSource(),
                        title = stringResource(MR.strings.pref_show_nsfw_source),
                        subtitle = stringResource(MR.strings.requires_app_restart),
                        onValueChanged = {
                            (context as FragmentActivity).authenticate(
                                title = context.stringResource(MR.strings.pref_category_nsfw_content),
                            )
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.parental_controls_info)),
                ),
            ),
            getMigrationCategory(sourcePreferences),
        )
    }

    @Composable
    fun getMigrationCategory(sourcePreferences: SourcePreferences): Preference.PreferenceGroup {
        val skipPreMigration by sourcePreferences.skipPreMigration().collectAsState()
        val migrationSources by sourcePreferences.migrationSources().collectAsState()
        return Preference.PreferenceGroup(
            stringResource(SYMR.strings.migration),
            enabled = skipPreMigration || migrationSources.isNotEmpty(),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = sourcePreferences.skipPreMigration(),
                    title = stringResource(SYMR.strings.skip_pre_migration),
                    subtitle = stringResource(SYMR.strings.pref_skip_pre_migration_summary),
                ),
            ),
        )
    }
}
