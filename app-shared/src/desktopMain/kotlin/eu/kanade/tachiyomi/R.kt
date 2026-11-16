package eu.kanade.tachiyomi

/**
 * KMK: desktop counterpart of the Android resource class, limited to the ids borrowed files reference
 * (tracker logos: `Tracker.getLogo()`). The values are arbitrary stable ids: desktop has no Android
 * resources; the desktop UI maps them to its own visuals (see `trackerLogoKey`).
 */
@Suppress("ClassName", "ktlint:standard:property-naming")
object R {
    object drawable {
        const val brand_anilist = 1
        const val brand_bangumi = 2
        const val brand_kavita = 3
        const val brand_kitsu = 4
        const val brand_komga = 5
        const val brand_mangadex = 6
        const val brand_mangaupdates = 7
        const val brand_myanimelist = 8
        const val brand_shikimori = 9
        const val brand_suwayomi = 10
    }
}
