package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet(
        // KMK -->
        REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(
        // KMK -->
        DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    fun downloadNewChapterCategoriesExclude() = preferenceStore.getStringSet(
        // KMK -->
        DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)

    // KMK -->
    fun downloadCacheRenewInterval() = preferenceStore.getInt("download_cache_renew_interval", 1)

    companion object {
        const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
    }
    // KMK <--
}
