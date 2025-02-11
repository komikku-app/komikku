package mihon.core.migration

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.data.DatabaseHandler

object MigrateUtils {
    fun updateSourceId(migrationContext: MigrationContext, newId: Long, oldId: Long) {
        val handler = migrationContext.get<DatabaseHandler>() ?: return
        runBlocking {
            handler.await { ehQueries.migrateSource(newId, oldId) }
            // KMK -->
            handler.await { ehQueries.migrateMergedSource(newId, oldId) }
            // Migrate saved searches & feeds
            handler.await { ehQueries.migrateSourceSavedSearch(newId, oldId) }
            handler.await { ehQueries.migrateSourceFeed(newId, oldId) }
        }

        // Also update pin
        val preferences = migrationContext.get<SourcePreferences>() ?: return
        val isPinned = oldId.toString() in preferences.pinnedSources().get()
        if (isPinned) {
            preferences.pinnedSources().getAndSet { pinned ->
                pinned.minus(oldId.toString())
                    .plus(newId.toString())
            }
        }
        // KMK <--
    }

    @Suppress("UNCHECKED_CAST")
    fun replacePreferences(
        preferenceStore: PreferenceStore,
        filterPredicate: (Map.Entry<String, Any?>) -> Boolean,
        newKey: (String) -> String,
    ) {
        preferenceStore.getAll()
            .filter(filterPredicate)
            .forEach { (key, value) ->
                when (value) {
                    is Int -> {
                        preferenceStore.getInt(newKey(key)).set(value)
                        preferenceStore.getInt(key).delete()
                    }
                    is Long -> {
                        preferenceStore.getLong(newKey(key)).set(value)
                        preferenceStore.getLong(key).delete()
                    }
                    is Float -> {
                        preferenceStore.getFloat(newKey(key)).set(value)
                        preferenceStore.getFloat(key).delete()
                    }
                    is String -> {
                        preferenceStore.getString(newKey(key)).set(value)
                        preferenceStore.getString(key).delete()
                    }
                    is Boolean -> {
                        preferenceStore.getBoolean(newKey(key)).set(value)
                        preferenceStore.getBoolean(key).delete()
                    }
                    is Set<*> -> (value as? Set<String>)?.let {
                        preferenceStore.getStringSet(newKey(key)).set(value)
                        preferenceStore.getStringSet(key).delete()
                    }
                }
            }
    }
}
