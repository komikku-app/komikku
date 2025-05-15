package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun sourceDisplayMode() = preferenceStore.getObject(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun disabledSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun incognitoExtensions() = preferenceStore.getStringSet("incognito_extensions", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet(
        // KMK -->
        PINNED_SOURCES_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    fun lastUsedSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun hideInLibraryItems() = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    // KMK -->
    fun hideInLibraryFeedItems() = preferenceStore.getBoolean("feed_hide_in_library_items", false)
    // KMK <--

    @Deprecated("Use ExtensionRepoRepository instead", replaceWith = ReplaceWith("ExtensionRepoRepository.getAll()"))
    fun extensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    // KMK -->
    fun globalSearchPinnedState() = preferenceStore.getEnum(
        Preference.appStateKey("global_search_pinned_toggle_state"),
        SourceFilter.PinnedOnly,
    )

    fun disabledRepos() = preferenceStore.getStringSet("disabled_repos", emptySet())
    // KMK <--

    // SY -->
    fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    fun sourcesTabCategories() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    fun sourcesTabCategoriesFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    fun sourcesTabSourcesInCategories() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean("data_saver_image_format_jpeg", false)

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
    }

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun defaultMangaOrder() = preferenceStore.getString("default_manga_order", "")

    fun migrationSources() = preferenceStore.getString("migrate_sources", "")

    fun smartMigration() = preferenceStore.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = preferenceStore.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = preferenceStore.getBoolean(Preference.appStateKey("skip_pre_migration"), false)

    fun hideNotFoundMigration() = preferenceStore.getBoolean("hide_not_found_migration", false)

    fun showOnlyUpdatesMigration() = preferenceStore.getBoolean("show_only_updates_migration", false)

    fun allowLocalSourceHiddenFolders() = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    fun preferredMangaDexId() = preferenceStore.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = preferenceStore.getStringSet(
        "pref_mangadex_sync_to_library_indexes",
        emptySet(),
    )

    fun recommendationSearchFlags() = preferenceStore.getInt("rec_search_flags", Int.MAX_VALUE)
    // SY <--

    // KMK -->
    fun relatedMangas() = preferenceStore.getBoolean("related_mangas", true)

    companion object {
        const val PINNED_SOURCES_PREF_KEY = "pinned_catalogues"
    }
    // KMK <--
}
