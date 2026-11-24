package eu.kanade.presentation.more.settings.screen

import eu.kanade.presentation.more.settings.Preference

/*
 * KMK: the platform-neutral part of Android's settings search (`SettingsSearchScreen`, `PreferenceScreen`): what a
 * query finds in the settings pages, the breadcrumb of a result and where the highlighted item sits in a page's list.
 * Android's screens call it; the desktop settings search (`:desktopApp`) builds its pages from its own settings screens.
 */

/** One searchable settings page: its [title], where a result leads ([route]) and its [contents]. */
data class SettingsSearchPage<R>(
    val title: String,
    val route: R,
    val contents: List<Preference>,
)

/** One search result: the page it is on ([route]), its [title], its [breadcrumbs] and the item to highlight there. */
data class SettingsSearchResult<R>(
    val route: R,
    val title: String,
    val breadcrumbs: String,
    val highlightKey: String,
)

/**
 * Android's settings search: the enabled items with a title (those of enabled groups too, info items left out) whose
 * title or subtitle contains [searchKey] (ignoring case), at most [limit] of them, in page order.
 */
fun <R> searchSettings(
    pages: List<SettingsSearchPage<R>>,
    searchKey: String,
    isLtr: Boolean,
    limit: Int = 10,
): List<SettingsSearchResult<R>> {
    if (searchKey.isEmpty()) return emptyList()
    return pages.asSequence()
        .flatMap { settingsData ->
            settingsData.contents.asSequence()
                // Only search from enabled prefs and one with valid title
                .filter { it.enabled && it.title.isNotBlank() }
                // Flatten items contained inside *enabled* PreferenceGroup
                .flatMap { p ->
                    when (p) {
                        is Preference.PreferenceGroup -> {
                            if (p.enabled) {
                                p.preferenceItems.asSequence()
                                    .filter { it.enabled && it.title.isNotBlank() }
                                    .map { p.title to it }
                            } else {
                                emptySequence()
                            }
                        }
                        is Preference.PreferenceItem<*, *> -> sequenceOf(null to p)
                    }
                }
                // Don't show info preference
                .filterNot { it.second is Preference.PreferenceItem.InfoPreference }
                // Filter by search query
                .filter { (_, p) ->
                    val inTitle = p.title.contains(searchKey, true)
                    val inSummary = p.subtitle?.contains(searchKey, true) ?: false
                    inTitle || inSummary
                }
                // Map result data
                .map { (categoryTitle, p) ->
                    SettingsSearchResult(
                        route = settingsData.route,
                        title = p.title,
                        breadcrumbs = settingsBreadcrumb(
                            path = settingsData.title,
                            node = categoryTitle,
                            isLtr = isLtr,
                        ),
                        highlightKey = p.title,
                    )
                }
        }
        .take(limit) // Just take top 10 result for quicker result
        .toList()
}

/** A result's breadcrumb: its page, then its group, in the reading direction. */
fun settingsBreadcrumb(path: String, node: String?, isLtr: Boolean): String {
    return if (node == null) {
        path
    } else {
        if (isLtr) {
            // This locale reads left to right.
            "$path > $node"
        } else {
            // This locale reads right to left.
            "$node < $path"
        }
    }
}

/**
 * The index of the item titled [highlightKey] among the lazy list items Android's `PreferenceScreen` makes of these
 * preferences (a group: its header, its items, a spacer; a disabled group: nothing, it is not shown), or -1.
 * Android's former copy counted disabled groups too, so a disabled group above the item scrolled past it.
 */
fun List<Preference>.findHighlightedIndex(highlightKey: String): Int {
    return flatMap {
        if (it is Preference.PreferenceGroup) {
            if (!it.enabled) return@flatMap emptyList()
            buildList<String?> {
                add(null) // Header
                addAll(it.preferenceItems.map { groupItem -> groupItem.title })
                add(null) // Spacer
            }
        } else {
            listOf(it.title)
        }
    }.indexOfFirst { it == highlightKey }
}
