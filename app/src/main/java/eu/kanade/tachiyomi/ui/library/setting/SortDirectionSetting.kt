package eu.kanade.tachiyomi.ui.library.setting

import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.library.LibraryGroup

enum class SortDirectionSetting(val flag: Long) {
    ASCENDING(0b01000000),
    DESCENDING(0b00000000);

    companion object {
        const val MASK = 0b01000000L

        fun fromFlag(flag: Long?): SortDirectionSetting {
            return values().find { mode -> mode.flag == flag } ?: ASCENDING
        }

        fun get(preferences: PreferencesHelper, category: Category?): SortDirectionSetting {
            return if (preferences.categorizedDisplaySettings().get() && category != null && category.id != 0L /* SY --> */ && preferences.groupLibraryBy().get() == LibraryGroup.BY_DEFAULT/* SY <-- */) {
                fromFlag(category.sortDirection)
            } else {
                preferences.librarySortingAscending().get()
            }
        }
    }
}
