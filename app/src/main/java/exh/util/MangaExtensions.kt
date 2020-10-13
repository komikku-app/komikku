package exh.util

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper

fun Manga.shouldDeleteChapters(db: DatabaseHelper, prefs: PreferencesHelper): Boolean {
    if (!favorite) return true

    val categoriesToNotDeleteFrom = prefs.dontDeleteFromCategories().get().map(String::toInt)
    if (categoriesToNotDeleteFrom.isEmpty()) return true

    // Get all categories, else default category (0)
    val categoriesForManga =
        db.getCategoriesForManga(this).executeAsBlocking()
            .mapNotNull { it.id }
            .takeUnless { it.isEmpty() } ?: listOf(0)

    return categoriesForManga.any { it !in categoriesToNotDeleteFrom }
}
