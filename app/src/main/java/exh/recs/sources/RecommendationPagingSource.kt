package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.similar.MangaDexSimilarPagingSource
import exh.source.COMICK_IDS
import exh.source.MANGADEX_IDS
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.BaseSourcePagingSource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * General class for recommendation sources.
 */
abstract class RecommendationPagingSource(
    protected val manga: Manga,
    source: CatalogueSource? = null,
) : BaseSourcePagingSource(source) {
    // Display name
    abstract val name: String

    // Localized category name
    open val category: StringResource = SYMR.strings.similar_titles

    /**
     * Recommendation sources that display results from a source extension,
     * can override this property to associate results with a specific source.
     * This is used to redirect the user directly to the corresponding MangaScreen.
     * If null, the user will be prompted to choose a source via SmartSearch when clicking on a recommendation.
     */
    open val associatedSourceId: Long? = null

    companion object {
        internal fun createSources(
            manga: Manga,
            // KMK -->
            recommendationSource: RecommendationSource,
            // KMK <--
        ): List<RecommendationPagingSource> {
            return buildList {
                add(AniListPagingSource(manga))
                add(MangaUpdatesCommunityPagingSource(manga))
                add(MangaUpdatesSimilarPagingSource(manga))
                add(MyAnimeListPagingSource(manga))

                // Only include MangaDex if the delegate sources are enabled and the source is MD-based
                if (
                    // KMK -->
                    recommendationSource.isMangaDexSource()
                    // KMK <--
                ) {
                    add(
                        MangaDexSimilarPagingSource(
                            manga,
                            // KMK -->
                            recommendationSource,
                            // KMK <--
                        ),
                    )
                }

                // Only include Comick if the source manga is from there
                if (
                    // KMK -->
                    recommendationSource.isComickSource()
                    // KMK <--
                ) {
                    add(
                        ComickPagingSource(
                            manga,
                            // KMK -->
                            recommendationSource,
                            // KMK <--
                        ),
                    )
                }
            }.sortedWith(compareBy({ it.name }, { it.category.resourceId }))
        }
    }
}

/**
 * General class for recommendation sources backed by trackers.
 */
abstract class TrackerRecommendationPagingSource(
    protected val endpoint: String,
    manga: Manga,
) : RecommendationPagingSource(manga) {
    private val getTracks: GetTracks by injectLazy()

    protected val trackerManager: TrackerManager by injectLazy()
    protected val client by lazy { Injekt.get<NetworkHelper>().client }
    protected val json by injectLazy<Json>()

    /**
     * Tracker id associated with the recommendation source.
     *
     * If not null and the tracker is attached to the source manga,
     * the remote id will be used to directly identify the manga on the tracker.
     * Otherwise, a search will be performed using the manga title.
     */
    abstract val associatedTrackerId: Long?

    abstract suspend fun getRecsBySearch(search: String): List<SManga>
    abstract suspend fun getRecsById(id: String): List<SManga>

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val tracks = getTracks.await(manga.id)

        val recs = try {
            val id = tracks.find { it.trackerId == associatedTrackerId }?.remoteId
            val results = if (id != null) {
                getRecsById(id.toString())
            } else {
                getRecsBySearch(manga.ogTitle)
            }
            logcat { name + " > Results: " + results.size }

            results.ifEmpty { throw NoResultsException() }
        } catch (e: Exception) {
            // 'No results' should not be logged as it happens frequently and is expected
            if (e !is NoResultsException) {
                logcat(LogPriority.ERROR, e) { name }
            }
            throw e
        }

        return MangasPage(recs, false)
    }
}

// KMK -->
internal class RecommendationSource(
    internal val sourceId: Long,
    sourceManager: SourceManager = Injekt.get(),
) {
    val source = sourceManager.get(sourceId)
        ?.let { it as CatalogueSource }

    fun isComickSource(): Boolean = sourceId in COMICK_IDS

    fun isMangaDexSource(): Boolean = sourceId in MANGADEX_IDS
}
// KMK <--
