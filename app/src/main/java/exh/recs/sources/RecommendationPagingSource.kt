package exh.recs.sources

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.similar.MangaDexSimilarPagingSource
import exh.pref.DelegateSourcePreferences
import exh.source.getMainSource
import exh.source.isMdBasedSource
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.data.source.SourcePagingSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * General class for recommendation sources.
 */
abstract class RecommendationPagingSource(
    source: CatalogueSource,
    protected val manga: Manga,
) : SourcePagingSource(source) {
    // Display name
    abstract val name: String

    // Localized category name
    abstract val category: StringResource

    /**
     * Recommendation sources that display results from a source extension,
     * can override this property to associate results with a specific source.
     * This is used to redirect the user directly to the corresponding MangaScreen.
     * If null, the user will be prompted to choose a source via SmartSearch when clicking on a recommendation.
     */
    open val associatedSourceId: Long? = null

    companion object {
        fun createSources(manga: Manga, source: CatalogueSource): List<RecommendationPagingSource> {
            return buildList {
                add(AniListPagingSource(manga, source))
                add(MangaUpdatesCommunityPagingSource(manga, source))
                add(MangaUpdatesSimilarPagingSource(manga, source))
                add(MyAnimeListPagingSource(manga, source))

                // Only include MangaDex if the delegate sources are enabled and the source is MD-based
                if (source.isMdBasedSource() && Injekt.get<DelegateSourcePreferences>().delegateSources().get()) {
                    add(MangaDexSimilarPagingSource(manga, source.getMainSource() as MangaDex))
                }

                // Only include Comick if the source manga is from there
                if (source.isComickSource()) {
                    add(ComickPagingSource(manga, source))
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
    source: CatalogueSource,
    manga: Manga,
) : RecommendationPagingSource(source, manga) {
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
    protected abstract val associatedTrackerId: Long?

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
            logcat(LogPriority.ERROR, e) { name }
            throw e
        }

        return MangasPage(recs, false)
    }
}
