package exh.recs.batch

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.net.toUri
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.SManga
import exh.log.xLog
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.RecommendationSource
import exh.recs.sources.TrackerRecommendationPagingSource
import exh.smartsearch.SmartLibrarySearchEngine
import exh.util.ThrottleManager
import exh.util.createPartialWakeLock
import exh.util.createWifiLock
import exh.util.ignore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.injectLazy
import java.io.Serializable
import java.util.Collections
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RecommendationSearchHelper(val context: Context) {
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getTracks: GetTracks by injectLazy()
    private val networkToLocalManga: NetworkToLocalManga by injectLazy()
    private val preferences: SourcePreferences by injectLazy()

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val smartSearchEngine by lazy { SmartLibrarySearchEngine() }

    private val logger by lazy { xLog() }

    val status: MutableStateFlow<SearchStatus> = MutableStateFlow(SearchStatus.Idle)

    @Synchronized
    fun runSearch(scope: CoroutineScope, mangaList: List<Manga>): Job? {
        if (status.value !is SearchStatus.Idle) {
            return null
        }

        status.value = SearchStatus.Initializing

        return scope.launch(Dispatchers.IO) { beginSearch(mangaList) }
    }

    private suspend fun beginSearch(mangaList: List<Manga>) {
        val flags = preferences.recommendationSearchFlags().get()
        val libraryManga = getLibraryManga.await()
        val tracks = getTracks.await()

        // Trackers such as MAL need to be throttled more strictly
        val stricterThrottling = SearchFlags.hasIncludeTrackers(flags)

        val throttleManager =
            ThrottleManager(
                max = 3.seconds,
                inc = 50.milliseconds,
                initial = if (stricterThrottling) 2.seconds else 0.seconds,
            )

        try {
            // Take wake + wifi locks
            ignore { wakeLock?.release() }
            wakeLock = ignore { context.createPartialWakeLock("tsy:RecommendationSearchWakelock") }
            ignore { wifiLock?.release() }
            wifiLock = ignore { context.createWifiLock("tsy:RecommendationSearchWifiLock") }

            // Map of results grouped by recommendation source
            val resultsMap = Collections.synchronizedMap(mutableMapOf<String, SearchResults>())

            mangaList.forEachIndexed { index, sourceManga ->
                // Check if the job has been cancelled
                coroutineContext.ensureActive()

                status.value = SearchStatus.Processing(sourceManga.toSManga(), index + 1, mangaList.size)

                val jobs = RecommendationPagingSource.createSources(
                    sourceManga,
                    // KMK -->
                    RecommendationSource(sourceManga.source),
                    // KMK <--
                ).mapNotNull { source ->
                    // Apply source filters
                    if (source is TrackerRecommendationPagingSource && !SearchFlags.hasIncludeTrackers(flags)) {
                        return@mapNotNull null
                    }

                    if (source.associatedSourceId != null && !SearchFlags.hasIncludeSources(flags)) {
                        return@mapNotNull null
                    }

                    // Parallelize fetching recommendations from all sources in the current context
                    CoroutineScope(coroutineContext).async(Dispatchers.IO) {
                        val recSourceId = source::class.qualifiedName!!

                        try {
                            val page = source.requestNextPage(1)

                            // Try to filter out mangas that are already in the library
                            val mangas = page.mangas
                                .filterLibraryItemsIfEnabled(source, libraryManga, tracks)

                            // Add or update the result collection for the current source
                            resultsMap.getOrPut(recSourceId) {
                                SearchResults(
                                    recSourceName = source.name,
                                    recSourceCategoryResId = source.category.resourceId,
                                    recAssociatedSourceId = source.associatedSourceId,
                                    results = mutableListOf(),
                                )
                            }.results.addAll(mangas)
                        } catch (_: NoResultsException) {
                        } catch (e: Exception) {
                            logger.e("Error while fetching recommendations for $recSourceId", e)
                        }
                    }
                }
                jobs.awaitAll()

                // Continuously slow down the search to avoid hitting rate limits
                throttleManager.throttle()
            }

            val rankedMap = resultsMap.map {
                RankedSearchResults(
                    recSourceName = it.value.recSourceName,
                    recSourceCategoryResId = it.value.recSourceCategoryResId,
                    recAssociatedSourceId = it.value.recAssociatedSourceId,
                    results = it.value.results
                        // Group by URL and count occurrences
                        .groupingBy(SManga::url)
                        .eachCount()
                        .entries
                        // Sort by occurrences desc
                        .sortedByDescending(Map.Entry<String, Int>::value)
                        // Resolve SManga instances from URL keys
                        .associate { (url, count) ->
                            val manga = it.value.results.first { manga -> manga.url == url }
                            manga to count
                        },
                )
            }

            status.value = when {
                rankedMap.isNotEmpty() -> SearchStatus.Finished.WithResults(rankedMap)
                else -> SearchStatus.Finished.WithoutResults
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            status.value = SearchStatus.Error(e.message.orEmpty())
            logger.e("Error during recommendation search", e)
            return
        } finally {
            // Release wake + wifi locks
            ignore {
                wakeLock?.release()
                wakeLock = null
            }
            ignore {
                wifiLock?.release()
                wifiLock = null
            }
        }
    }

    private suspend fun List<SManga>.filterLibraryItemsIfEnabled(
        recSource: RecommendationPagingSource,
        libraryManga: List<LibraryManga>,
        tracks: List<Track>,
    ): List<SManga> {
        val flags = preferences.recommendationSearchFlags().get()

        if (!SearchFlags.hasHideLibraryResults(flags)) {
            return this
        }

        // KMK -->
        // Source recommendations can be directly resolved, if the recommendation is from the same source
        recSource.associatedSourceId?.let { srcId ->
            return networkToLocalManga(map { it.toDomainManga(srcId) })
                .filterNot { local -> libraryManga.any { it.id == local.id } }
                .map { it.toSManga() }
        }
        // KMK <--

        return filterNot { manga ->
            // Tracker recommendations can be resolved by checking if the tracker is attached to the recommendation
            if (recSource is TrackerRecommendationPagingSource) {
                recSource.associatedTrackerId?.let { trackerId ->
                    return@filterNot tracks.any {
                        it.trackerId == trackerId && it.remoteUrl.toUri().path == manga.url.toUri().path
                    }
                }
            }

            // Fallback to smart search otherwise
            smartSearchEngine.smartSearch(libraryManga, manga.title) != null
        }
    }
}

// Contains the search results for a single source
private typealias SearchResults = Results<MutableList<SManga>>

// Contains the ranked search results for a single source
typealias RankedSearchResults = Results<Map<SManga, Int>>

data class Results<T>(
    val recSourceName: String,
    @StringRes val recSourceCategoryResId: Int,
    val recAssociatedSourceId: Long?,
    val results: T,
) : Serializable

sealed interface SearchStatus {
    data object Idle : SearchStatus
    data object Initializing : SearchStatus
    data class Processing(val manga: SManga, val current: Int, val total: Int) : SearchStatus
    data class Error(val message: String) : SearchStatus
    data object Cancelling : SearchStatus

    sealed interface Finished : SearchStatus {
        data class WithResults(val results: List<RankedSearchResults>) : Finished
        data object WithoutResults : Finished
    }
}
