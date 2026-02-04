package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.category.genre.SortTagScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_EXISTING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_NEW
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsLibraryScreen : SearchableSettings {

    @Suppress("unused")
    private fun readResolve(): Any = SettingsLibraryScreen

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        val getCategories = remember { Injekt.get<GetCategories>() }
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val unsortedPreferences = remember { Injekt.get<UnsortedPreferences>() }

        val allCategories by getCategories.subscribe().collectAsState(emptyList())
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        return listOf(
            getCategoriesGroup(navigator, allCategories, libraryPreferences),
            getGlobalUpdateGroup(allCategories, libraryPreferences),
            getBehaviorGroup(libraryPreferences),
            getSortingCategory(navigator, libraryPreferences),
            getMigrationCategory(unsortedPreferences),
            // SY -->
            getSortingCategory(LocalNavigator.currentOrThrow, libraryPreferences),
            // SY <--
        )
    }

    @Composable
    private fun getCategoriesGroup(
        navigator: Navigator,
        allCategories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val userCategoriesCount = allCategories.filterNot(Category::isSystemCategory).size
        var showDefaultDialog by rememberSaveable { mutableStateOf(false) }

        if (showDefaultDialog) {
            DefaultCategoryDialog(
                categories = allCategories,
                selectedId = libraryPreferences.defaultCategory().get(),
                onDismiss = { showDefaultDialog = false },
                onConfirm = {
                    libraryPreferences.defaultCategory().set(it)
                    showDefaultDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.categories),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.action_edit_categories),
                    subtitle = pluralStringResource(
                        MR.plurals.num_categories,
                        userCategoriesCount,
                        userCategoriesCount,
                    ),
                    onClick = { navigator.push(CategoryScreen()) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.default_category),
                    subtitle = getDefaultCategoryLabel(allCategories, libraryPreferences),
                    onClick = { showDefaultDialog = true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.categorizedDisplaySettings(),
                    title = stringResource(MR.strings.categorized_display_settings),
                    onValueChanged = {
                        if (!it) {
                            scope.launch {
                                Injekt.get<ResetCategoryFlags>().await()
                            }
                        }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getGlobalUpdateGroup(
        allCategories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val autoUpdateIntervalPref = libraryPreferences.autoUpdateInterval()
        val autoUpdateCategoriesPref = libraryPreferences.updateCategories()
        val autoUpdateCategoriesExcludePref = libraryPreferences.updateCategoriesExclude()

        val autoUpdateInterval by autoUpdateIntervalPref.collectAsState()

        val included by autoUpdateCategoriesPref.collectAsState()
        val excluded by autoUpdateCategoriesExcludePref.collectAsState()

        var showCategoriesDialog by rememberSaveable { mutableStateOf(false) }

        if (showCategoriesDialog) {
            UpdateCategoriesDialog(
                categories = allCategories,
                included = included,
                excluded = excluded,
                onDismiss = { showCategoriesDialog = false },
                onConfirm = { newIncluded, newExcluded ->
                    autoUpdateCategoriesPref.set(newIncluded)
                    autoUpdateCategoriesExcludePref.set(newExcluded)
                    showCategoriesDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_library_update),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = autoUpdateIntervalPref,
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.update_never),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        72 to stringResource(MR.strings.update_72hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    title = stringResource(MR.strings.pref_library_update_interval),
                    onValueChanged = {
                        LibraryUpdateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.autoUpdateDeviceRestrictions(),
                    entries = persistentMapOf(
                        DEVICE_ONLY_ON_WIFI to stringResource(MR.strings.connected_to_wifi),
                        DEVICE_NETWORK_NOT_METERED to stringResource(MR.strings.network_not_metered),
                        DEVICE_CHARGING to stringResource(MR.strings.charging),
                    ),
                    title = stringResource(MR.strings.pref_library_update_restriction),
                    subtitle = stringResource(MR.strings.restrictions),
                    enabled = autoUpdateInterval > 0,
                    onValueChanged = {
                        ContextCompat.getMainExecutor(context)
                            .execute { LibraryUpdateJob.setupTask(context) }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    onClick = { showCategoriesDialog = true },
                ),
                // SY -->
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.groupLibraryUpdateType(),
                    entries = persistentMapOf(
                        GroupLibraryMode.GLOBAL to stringResource(SYMR.strings.library_group_updates_global),
                        GroupLibraryMode.ALL_BUT_UNGROUPED to
                            stringResource(SYMR.strings.library_group_updates_all_but_ungrouped),
                        GroupLibraryMode.ALL to stringResource(SYMR.strings.library_group_updates_all),
                    ),
                    title = stringResource(SYMR.strings.library_group_updates),
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.autoUpdateMetadata(),
                    title = stringResource(MR.strings.pref_library_update_refresh_metadata),
                    subtitle = stringResource(MR.strings.pref_library_update_refresh_metadata_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.autoUpdateMangaRestrictions(),
                    entries = persistentMapOf(
                        MANGA_HAS_UNREAD to stringResource(MR.strings.pref_update_only_completely_read),
                        MANGA_NON_READ to stringResource(MR.strings.pref_update_only_started),
                        MANGA_NON_COMPLETED to stringResource(MR.strings.pref_update_only_non_completed),
                        MANGA_OUTSIDE_RELEASE_PERIOD to stringResource(MR.strings.pref_update_only_in_release_period),
                    ),
                    title = stringResource(MR.strings.pref_library_update_smart_update),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.newShowUpdatesCount(),
                    title = stringResource(MR.strings.pref_library_update_show_tab_badge),
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.showUpdatingProgressBanner(),
                    title = stringResource(KMR.strings.pref_show_updating_progress_banner),
                ),
                // KMK <--
            ),
        )
    }

    @Composable
    private fun DefaultCategoryDialog(
        categories: List<Category>,
        selectedId: Int,
        onDismiss: () -> Unit,
        onConfirm: (Int) -> Unit,
    ) {
        var selected by remember { mutableStateOf(selectedId) }
        var expandedParents by remember { mutableStateOf(setOf<Long>()) }

        val hierarchy = remember(categories) {
            buildCategoryHierarchy(categories)
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(MR.strings.default_category)) },
            confirmButton = {
                TextButton(onClick = { onConfirm(selected) }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    hierarchy.forEach { entry ->
                        val cat = entry.category
                        val isParent = cat.parentId == null
                        val hasChildren = hierarchy.any { it.category.parentId == cat.id }
                        val isExpanded = expandedParents.contains(cat.id)

                        if (!isParent && !expandedParents.contains(cat.parentId)) return@forEach

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = cat.id.toInt() }
                                .padding(start = (entry.depth * 24).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected == cat.id.toInt(),
                                onClick = { selected = cat.id.toInt() },
                            )
                            Text(
                                text = cat.visualName,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            )
                            if (isParent && hasChildren) {
                                Icon(
                                    imageVector =
                                    if (isExpanded) {
                                        Icons.Default.KeyboardArrowDown
                                    } else {
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                                    },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable {
                                            expandedParents =
                                                if (isExpanded) {
                                                    expandedParents - cat.id
                                                } else {
                                                    expandedParents + cat.id
                                                }
                                        },
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    @Composable
    private fun UpdateCategoriesDialog(
        categories: List<Category>,
        included: Set<String>,
        excluded: Set<String>,
        onDismiss: () -> Unit,
        onConfirm: (Set<String>, Set<String>) -> Unit,
    ) {
        var includedSet by remember { mutableStateOf(included) }
        var excludedSet by remember { mutableStateOf(excluded) }
        var expandedParents by remember { mutableStateOf(setOf<Long>()) }

        val hierarchy = remember(categories) {
            buildCategoryHierarchy(categories)
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(MR.strings.categories)) },
            confirmButton = {
                TextButton(onClick = { onConfirm(includedSet, excludedSet) }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    hierarchy.forEach { entry ->
                        val category = entry.category
                        val id = category.id.toString()

                        val isParent = category.parentId == null
                        val hasChildren = hierarchy.any { it.category.parentId == category.id }
                        val isExpanded = expandedParents.contains(category.id)

                        if (!isParent && !expandedParents.contains(category.parentId)) return@forEach

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when {
                                        includedSet.contains(id) -> {
                                            includedSet -= id
                                            excludedSet += id
                                        }
                                        excludedSet.contains(id) -> {
                                            excludedSet -= id
                                        }
                                        else -> {
                                            includedSet += id
                                        }
                                    }
                                }
                                .padding(start = (entry.depth * 24).dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Checkbox state
                            when {
                                includedSet.contains(id) -> {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = null,
                                    )
                                }
                                excludedSet.contains(id) -> {
                                    TriStateCheckbox(
                                        state = ToggleableState.Indeterminate,
                                        onClick = null,
                                    )
                                }
                                else -> {
                                    Checkbox(
                                        checked = false,
                                        onCheckedChange = null,
                                    )
                                }
                            }

                            Text(
                                text = category.visualName,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            )

                            if (isParent && hasChildren) {
                                Icon(
                                    imageVector =
                                    if (isExpanded) {
                                        Icons.Default.KeyboardArrowDown
                                    } else {
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                                    },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable {
                                            expandedParents =
                                                if (isExpanded) {
                                                    expandedParents - category.id
                                                } else {
                                                    expandedParents + category.id
                                                }
                                        },
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    @Composable
    private fun getDefaultCategoryLabel(
        categories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): String {
        val defaultId by libraryPreferences.defaultCategory().collectAsState()
        return categories.firstOrNull { it.id.toInt() == defaultId }?.visualName
            ?: stringResource(MR.strings.default_category_summary)
    }

    @Composable
    private fun getBehaviorGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup =
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_behavior),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeToStartAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_start),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeToEndAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_end),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.markDuplicateReadChapterAsRead(),
                    entries = persistentMapOf(
                        MARK_DUPLICATE_CHAPTER_READ_EXISTING to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_existing),
                        MARK_DUPLICATE_CHAPTER_READ_NEW to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_new),
                    ),
                    title = stringResource(MR.strings.pref_mark_duplicate_read_chapter_read),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.hideMissingChapters(),
                    title = stringResource(MR.strings.pref_hide_missing_chapter_indicators),
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.showEmptyCategoriesSearch(),
                    title = stringResource(KMR.strings.pref_show_empty_categories_search),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.syncOnAdd(),
                    title = stringResource(KMR.strings.pref_sync_manga_on_add),
                    subtitle = stringResource(KMR.strings.pref_sync_manga_on_add_description),
                ),
                // KMK <--
            ),
        )

    // SY -->
    @Composable
    fun getSortingCategory(
        navigator: Navigator,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val tagCount by libraryPreferences.sortTagsForLibrary().collectAsState()
        return Preference.PreferenceGroup(
            stringResource(SYMR.strings.pref_sorting_settings),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.pref_tag_sorting),
                    subtitle = pluralStringResource(
                        SYMR.plurals.pref_tag_sorting_desc,
                        tagCount.size,
                        tagCount.size,
                    ),
                    onClick = { navigator.push(SortTagScreen()) },
                ),
            ),
        )
    }

    @Composable
    fun getMigrationCategory(
        unsortedPreferences: UnsortedPreferences,
    ): Preference.PreferenceGroup {
        val skipPreMigration by unsortedPreferences.skipPreMigration().collectAsState()
        val migrationSources by unsortedPreferences.migrationSources().collectAsState()
        return Preference.PreferenceGroup(
            stringResource(SYMR.strings.migration),
            enabled = skipPreMigration || migrationSources.isNotEmpty(),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = unsortedPreferences.skipPreMigration(),
                    title = stringResource(SYMR.strings.skip_pre_migration),
                    subtitle = stringResource(SYMR.strings.pref_skip_pre_migration_summary),
                ),
            ),
        )
    }
    // SY <--
}
