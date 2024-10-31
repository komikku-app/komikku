package tachiyomi.domain.download.service

import mihon.domain.translation.translators.LanguageTranslators
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // TachiyomiAT
    fun translateOnDownload() = preferenceStore.getBoolean("auto_translate_on_download", false)
    fun translateFromLanguage() = preferenceStore.getInt("auto_translate_language_from", 0)
    fun translateToLanguage() = preferenceStore.getString("auto_translate_language_to", "en")
    fun translationFont() = preferenceStore.getInt("auto_translate_font", 0)
    fun translationEngine() = preferenceStore.getEnum("auto_translation_engine", LanguageTranslators.MLKIT)
    fun translationEngineModel() = preferenceStore.getString("translation_engine_model", "google/gemma-2-9b-it:free")
    fun translationApiKey() = preferenceStore.getString("auto_translation_api_key", "")

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
        "remove_exclude_categories",
        emptySet(),
    )

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(
        "download_new_categories",
        emptySet(),
    )

    fun downloadNewChapterCategoriesExclude() = preferenceStore.getStringSet(
        "download_new_categories_exclude",
        emptySet(),
    )

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)

    // KMK -->
    fun downloadCacheRenewInterval() = preferenceStore.getInt("download_cache_renew_interval", 1)
    // KMK <--
}
