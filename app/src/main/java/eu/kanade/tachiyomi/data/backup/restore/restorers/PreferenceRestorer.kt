package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences.Companion.PINNED_SOURCES_PREF_KEY
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import exh.EXHMigrations
import exh.log.xLogE
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences.Companion.DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY
import tachiyomi.domain.download.service.DownloadPreferences.Companion.DOWNLOAD_NEW_CATEGORIES_PREF_KEY
import tachiyomi.domain.download.service.DownloadPreferences.Companion.REMOVE_EXCLUDE_CATEGORIES_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEFAULT_CATEGORY_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences.Companion.LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY
import tachiyomi.domain.library.service.LibraryPreferences.Companion.LIBRARY_UPDATE_CATEGORIES_PREF_KEY
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceRestorer(
    private val context: Context,
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    // KMK -->
    private val getCategories by lazy { Injekt.get<GetCategories>() }
    private val libraryPreferences by lazy { Injekt.get<LibraryPreferences>() }
    // <--

    /* KMK --> */ suspend /* KMK <-- */ fun restoreApp(
        preferences: List<BackupPreference>,
        // KMK -->
        backupCategories: List<BackupCategory>,
        // KMK <--
    ) {
        restorePreferences(
            preferences,
            preferenceStore,
            // KMK -->
            backupCategories,
            // KMK <--
        )

        LibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
        // KMK -->
        AppUpdateJob.setupTask(context)
        // KMK <--
    }

    /* KMK --> */ suspend /* KMK <-- */ fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private /* KMK --> */ suspend /* KMK <-- */ fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        // KMK -->
        backupCategories: List<BackupCategory> = emptyList(),
        // KMK <--
    ) {
        // KMK -->
        val allCategories = getCategories.await()
        val categoriesByName = allCategories.associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order.toString() }
        // KMK <--
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            // KMK -->
            try {
                // KMK <--
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            // KMK -->
                            when (key) {
                                // Convert CategoryOrder to CategoryId
                                DEFAULT_CATEGORY_PREF_KEY -> {
                                    if (backupCategories.isNotEmpty()) {
                                        val order = value.value.toLong()
                                        val newValue = backupCategories.find { it.order == order }
                                            ?.let {
                                                categoriesByName[it.name]?.id?.toInt()
                                            }
                                            ?: libraryPreferences.defaultCategory().defaultValue()

                                        preferenceStore.getInt(key).set(newValue)
                                    }
                                }
                                else ->
                                    // KMK <--
                                    preferenceStore.getInt(key).set(value.value)
                            }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            // KMK -->
                            when (key) {
                                PINNED_SOURCES_PREF_KEY -> {
                                    EXHMigrations.migratePinnedSources(value.value)
                                }
                                // Convert CategoryOrder to CategoryId
                                LIBRARY_UPDATE_CATEGORIES_PREF_KEY, LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
                                DOWNLOAD_NEW_CATEGORIES_PREF_KEY, DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
                                REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
                                -> {
                                    val newValue = value.value.mapNotNull { order ->
                                        backupCategoriesByOrder[order]?.let { backupCategory ->
                                            categoriesByName[backupCategory.name]?.id?.toString()
                                        }
                                    }.toSet()
                                    if (newValue.isNotEmpty()) {
                                        preferenceStore.getStringSet(key).set(newValue)
                                    }
                                }
                                else ->
                                    // KMK <--
                                    preferenceStore.getStringSet(key).set(value.value)
                            }
                        }
                    }
                }
                // KMK -->
            } catch (e: Exception) {
                xLogE("Failed to restore preference <$key>", e)
                // KMK <--
            }
        }
    }
}
