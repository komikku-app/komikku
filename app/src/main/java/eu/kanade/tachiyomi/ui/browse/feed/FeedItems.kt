@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

/*
 * KMK: the platform-neutral part of the global feed (Android's FeedScreenModel), moved out of it unchanged so the
 * desktop Feed tab builds and loads its rows the same way (a `shared` borrowed source, see
 * app-shared/borrowed-app-sources.gradle.kts). FeedScreenModel calls these.
 */

/** The most rows the global feed takes: adding is refused once there are more (Android's limit). */
const val MaxFeedItems = 20

/** Whether the global feed is full ([count] = its rows, `CountFeedSavedSearchGlobal`). */
fun isFeedFull(count: Long): Boolean = count > MaxFeedItems

/**
 * The sources a feed row can be added for: enabled languages, not disabled, by "(lang) name", pinned ones first.
 */
fun feedSourceOptions(sourceManager: SourceManager, sourcePreferences: SourcePreferences): ImmutableList<Source> {
    val languages = sourcePreferences.enabledLanguages().get()
    val pinnedSources = sourcePreferences.pinnedSources().get()
    val disabledSources = sourcePreferences.disabledSources().get()
        .mapNotNull { it.toLongOrNull() }

    val list = sourceManager.getVisibleSources()
        .filter { it.lang in languages }
        .filterNot { it.id in disabledSources }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { "(${it.lang}) ${it.name}" })

    return list.sortedBy { it.id.toString() !in pinnedSources }.toImmutableList()
}

/** The feed rows' saved searches: each row with its saved search (none for a latest / popular row). */
fun pairFeedWithSavedSearches(
    feedSavedSearch: List<FeedSavedSearch>,
    savedSearches: List<SavedSearch>,
): List<Pair<FeedSavedSearch, SavedSearch?>> {
    val byId = savedSearches.associateBy { it.id }
    return feedSavedSearch.map { it to byId[it.savedSearch] }
}

/** A feed row's title and subtitle: its saved search's name and the source, or the source and its language. */
fun feedItemTitles(feed: FeedSavedSearch, savedSearch: SavedSearch?, source: Source?): Pair<String, String> {
    return Pair(
        savedSearch?.name ?: (source?.name ?: feed.source.toString()),
        if (savedSearch != null) {
            source?.name ?: feed.source.toString()
        } else {
            LocaleHelper.getLocalizedDisplayName(source?.lang)
        },
    )
}

/**
 * The first page of a feed row: its saved search (query and filters), else the source's latest updates (popular when
 * it has no latest). Throws what the source throws.
 */
suspend fun fetchFeedPage(source: Source, savedSearch: SavedSearch?, filterSerializer: FilterSerializer): MangasPage {
    return if (savedSearch == null) {
        if (source.supportsLatest) {
            source.getLatestUpdates(1)
        } else {
            source.getPopularManga(1)
        }
    } else {
        source.getSearchManga(
            1,
            savedSearch.query?.sanitize().orEmpty(),
            feedFilterList(savedSearch, source, filterSerializer),
        )
    }
}

/** A saved search's filters on the source's current list; none when they do not load. */
fun feedFilterList(savedSearch: SavedSearch, source: Source, filterSerializer: FilterSerializer): FilterList {
    val filters = savedSearch.filtersJson ?: return FilterList()
    return runCatching {
        val originalFilters = source.getFilterList()
        filterSerializer.deserialize(
            filters = originalFilters,
            json = Json.decodeFromString(filters),
        )
        originalFilters
    }.getOrElse { FilterList() }
}
