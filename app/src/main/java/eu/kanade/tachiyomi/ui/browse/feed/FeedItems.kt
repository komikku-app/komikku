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
 * desktop Feed tab builds and loads its rows the same way (a `desktopCopy` borrowed source: still compiled by `app`,
 * and again by `:app-shared` for desktop, because it uses the Android-only LocaleHelper; see
 * app-shared/borrowed-app-sources.gradle.kts). FeedScreenModel calls these.
 */

/** The most rows a feed (the global one, a source's own) takes: adding is refused once it holds that many. */
const val MaxFeedItems = 20

/**
 * Whether a feed is full ([count] = its rows: `CountFeedSavedSearchGlobal` for the global feed,
 * `CountFeedSavedSearchBySourceId` for a source's own feed). Both used to check `count > MaxFeedItems`, which let a 21st
 * row in although the message says 20 (bug fix).
 */
fun isFeedFull(count: Long): Boolean = count >= MaxFeedItems

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

/*
 * The platform-neutral part of a source's own feed (Android's SourceFeedScreenModel, the source landing page): its rows
 * and their first pages. SourceFeedScreenModel builds its SourceFeedUI rows from these; the desktop source feed too.
 */

/** A row of a source's own feed: its latest updates, its popular entries ("Browse"), or one of its feed saved searches. */
sealed interface SourceFeedRow {
    /** Android's ids: latest -1, popular -2, a saved search row its feed row's id. */
    val id: Long

    data object Latest : SourceFeedRow {
        override val id: Long = -1
    }

    data object Popular : SourceFeedRow {
        override val id: Long = -2
    }

    data class SavedSearchRow(val feed: FeedSavedSearch, val savedSearch: SavedSearch) : SourceFeedRow {
        override val id: Long get() = feed.id
    }
}

/**
 * The rows of [source]'s feed: latest (when the source has it), popular, then its feed saved searches ([feeds], in feed
 * order) with their saved searches ([savedSearches]). A feed row whose saved search is gone is left out (Android used to
 * fail on it; the database deletes such rows with their saved search).
 */
fun sourceFeedRows(source: Source, feeds: List<FeedSavedSearch>, savedSearches: List<SavedSearch>): List<SourceFeedRow> {
    val byId = savedSearches.associateBy { it.id }
    return listOfNotNull(
        SourceFeedRow.Latest.takeIf { source.supportsLatest },
        SourceFeedRow.Popular,
    ) + feeds.mapNotNull { feed -> byId[feed.savedSearch]?.let { SourceFeedRow.SavedSearchRow(feed, it) } }
}

/** The first page of a source feed row; a saved search with its sanitised query and filters. Throws what the source throws. */
suspend fun fetchSourceFeedPage(source: Source, row: SourceFeedRow, filterSerializer: FilterSerializer): MangasPage {
    return when (row) {
        SourceFeedRow.Latest -> source.getLatestUpdates(1)
        SourceFeedRow.Popular -> source.getPopularManga(1)
        is SourceFeedRow.SavedSearchRow -> source.getSearchManga(
            1,
            row.savedSearch.query?.sanitize().orEmpty(),
            feedFilterList(row.savedSearch, source, filterSerializer),
        )
    }
}
