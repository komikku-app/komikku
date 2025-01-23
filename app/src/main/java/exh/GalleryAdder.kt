package exh

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.log.xLogStack
import exh.source.getMainSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GalleryAdder(
    private val getAnime: GetAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getEpisode: GetEpisode = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {

    private val filters: Pair<Set<String>, Set<Long>> = Injekt.get<SourcePreferences>().run {
        enabledLanguages().get() to disabledSources().get().map { it.toLong() }.toSet()
    }

    private val Pair<Set<String>, Set<Long>>.enabledLangs
        get() = first
    private val Pair<Set<String>, Set<Long>>.disabledSources
        get() = second

    private val logger = xLogStack()

    fun pickSource(url: String): List<UrlImportableSource> {
        val uri = url.toUri()
        return sourceManager.getVisibleCatalogueSources()
            .mapNotNull { it.getMainSource<UrlImportableSource>() }
            .filter {
                it.lang in filters.enabledLangs &&
                    it.id !in filters.disabledSources &&
                    try {
                        it.matchesUri(uri)
                    } catch (e: Exception) {
                        false
                    }
            }
    }

    suspend fun addGallery(
        context: Context,
        url: String,
        fav: Boolean = false,
        forceSource: UrlImportableSource? = null,
        throttleFunc: suspend () -> Unit = {},
        retry: Int = 1,
    ): GalleryAddEvent {
        logger.d(context.stringResource(SYMR.strings.gallery_adder_importing_gallery, url, fav.toString(), forceSource?.toString().orEmpty()))
        try {
            val uri = url.toUri()

            // Find matching source
            val source = if (forceSource != null) {
                try {
                    if (forceSource.matchesUri(uri)) {
                        forceSource
                    } else {
                        return GalleryAddEvent.Fail.UnknownSource(url, context)
                    }
                } catch (e: Exception) {
                    logger.e(context.stringResource(SYMR.strings.gallery_adder_source_uri_must_match), e)
                    return GalleryAddEvent.Fail.UnknownType(url, context)
                }
            } else {
                sourceManager.getVisibleCatalogueSources()
                    .mapNotNull { it.getMainSource<UrlImportableSource>() }
                    .find {
                        it.lang in filters.enabledLangs &&
                            it.id !in filters.disabledSources &&
                            try {
                                it.matchesUri(uri)
                            } catch (e: Exception) {
                                false
                            }
                    } ?: return GalleryAddEvent.Fail.UnknownSource(url, context)
            }

            val realChapterUrl = try {
                source.mapUrlToEpisodeUrl(uri)
            } catch (e: Exception) {
                logger.e(context.stringResource(SYMR.strings.gallery_adder_uri_map_to_chapter_error), e)
                null
            }

            val cleanedChapterUrl = if (realChapterUrl != null) {
                try {
                    source.cleanEpisodeUrl(realChapterUrl)
                } catch (e: Exception) {
                    logger.e(context.stringResource(SYMR.strings.gallery_adder_uri_clean_error), e)
                    null
                }
            } else {
                null
            }

            val chapterMangaUrl = if (realChapterUrl != null) {
                source.mapEpisodeUrlToAnimeUrl(realChapterUrl.toUri())
            } else {
                null
            }

            // Map URL to manga URL
            val realMangaUrl = try {
                chapterMangaUrl ?: source.mapUrlToAnimeUrl(uri)
            } catch (e: Exception) {
                logger.e(context.stringResource(SYMR.strings.gallery_adder_uri_map_to_gallery_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Clean URL
            val cleanedMangaUrl = try {
                source.cleanAnimeUrl(realMangaUrl)
            } catch (e: Exception) {
                logger.e(context.stringResource(SYMR.strings.gallery_adder_uri_clean_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Use manga in DB if possible, otherwise, make a new manga
            var manga = getAnime.await(cleanedMangaUrl, source.id)
                ?: networkToLocalAnime.await(
                    Anime.create().copy(
                        source = source.id,
                        url = cleanedMangaUrl,
                    ),
                )

            // Fetch and copy details
            val newManga = retry(retry) { source.getAnimeDetails(manga.toSAnime()) }
            updateAnime.awaitUpdateFromSource(manga, newManga, false)
            manga = getAnime.await(manga.id)!!

            if (fav) {
                updateAnime.awaitUpdateFavorite(manga.id, true)
                manga = manga.copy(favorite = true)
            }

            // Fetch and copy episodes
            try {
                val chapterList = retry(retry) {
                    if (source is EHentai) {
                        source.getChapterList(manga.toSAnime(), throttleFunc)
                    } else {
                        source.getEpisodeList(manga.toSAnime())
                    }
                }

                if (chapterList.isNotEmpty()) {
                    syncEpisodesWithSource.await(chapterList, manga, source)
                }
            } catch (e: Exception) {
                logger.w(context.stringResource(SYMR.strings.gallery_adder_chapter_fetch_error, manga.title), e)
                return GalleryAddEvent.Fail.Error(url, context.stringResource(SYMR.strings.gallery_adder_chapter_fetch_error, url))
            }

            return if (cleanedChapterUrl != null) {
                val chapter = getEpisode.await(cleanedChapterUrl, manga.id)
                if (chapter != null) {
                    GalleryAddEvent.Success(url, manga, context, chapter)
                } else {
                    GalleryAddEvent.Fail.Error(url, context.stringResource(SYMR.strings.gallery_adder_could_not_identify_chapter, url))
                }
            } else {
                GalleryAddEvent.Success(url, manga, context)
            }
        } catch (e: Exception) {
            logger.w(context.stringResource(SYMR.strings.gallery_adder_could_not_add_gallery, url), e)

            if (e is EHentai.GalleryNotFoundException) {
                return GalleryAddEvent.Fail.NotFound(url, context)
            }

            return GalleryAddEvent.Fail.Error(
                url,
                ((e.message ?: "Unknown error!") + " (Gallery: $url)").trim(),
            )
        }
    }

    private inline fun <T : Any> retry(retryCount: Int, block: () -> T): T {
        var result: T? = null
        var lastError: Exception? = null

        for (i in 1..retryCount) {
            try {
                result = block()
                break
            } catch (e: Exception) {
                if (e is EHentai.GalleryNotFoundException) {
                    throw e
                }
                lastError = e
            }
        }

        if (lastError != null) {
            throw lastError
        }

        return result!!
    }
}

sealed class GalleryAddEvent {
    abstract val logMessage: String
    abstract val galleryUrl: String
    open val galleryTitle: String? = null

    class Success(
        override val galleryUrl: String,
        val manga: Anime,
        val context: Context,
        val episode: Episode? = null,
    ) : GalleryAddEvent() {
        override val galleryTitle = manga.title
        override val logMessage = context.stringResource(SYMR.strings.batch_add_success_log_message, galleryTitle)
    }

    sealed class Fail : GalleryAddEvent() {
        class UnknownType(override val galleryUrl: String, val context: Context) : Fail() {
            override val logMessage = context.stringResource(SYMR.strings.batch_add_unknown_type_log_message, galleryUrl)
        }

        open class Error(
            override val galleryUrl: String,
            override val logMessage: String,
        ) : Fail()

        class NotFound(galleryUrl: String, context: Context) :
            Error(galleryUrl, context.stringResource(SYMR.strings.batch_add_not_exist_log_message, galleryUrl))

        class UnknownSource(override val galleryUrl: String, val context: Context) : Fail() {
            override val logMessage = context.stringResource(SYMR.strings.batch_add_unknown_source_log_message, galleryUrl)
        }
    }
}
