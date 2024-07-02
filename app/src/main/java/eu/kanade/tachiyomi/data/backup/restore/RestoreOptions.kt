package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

data class RestoreOptions(
    val library: Boolean = true,
    val appSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    // SY -->
    val savedSearches: Boolean = true,
    // SY <--
) {

    fun asBooleanArray() = booleanArrayOf(
        library,
        appSettings,
        sourceSettings,
        // SY -->
        savedSearches
        // SY <--
    )

    fun anyEnabled() = library || appSettings || sourceSettings /* SY --> */ || savedSearches /* SY <-- */

    companion object {
        val options = persistentListOf(
            Entry(
                label = MR.strings.label_library,
                getter = RestoreOptions::library,
                setter = { options, enabled -> options.copy(library = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = RestoreOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = RestoreOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            // SY -->
            Entry(
                label = KMR.strings.saved_searches_feeds,
                getter = RestoreOptions::savedSearches,
                setter = { options, enabled -> options.copy(savedSearches = enabled) },
            ),
            // SY <--
        )

        fun fromBooleanArray(array: BooleanArray) = RestoreOptions(
            library = array[0],
            appSettings = array[1],
            sourceSettings = array[2],
            // SY -->
            savedSearches = array[3]
            // SY <--
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (RestoreOptions) -> Boolean,
        val setter: (RestoreOptions, Boolean) -> RestoreOptions,
    )
}
