package exh

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import exh.log.xLogStack
import exh.source.getMainSource
import exh.util.executeOnIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class GalleryAdder {

    private val db: DatabaseHelper by injectLazy()

    private val sourceManager: SourceManager by injectLazy()

    private val filters: Pair<Set<String>, Set<Long>> = run {
        val preferences = Injekt.get<PreferencesHelper>()
        preferences.enabledLanguages().get() to preferences.disabledSources().get().map { it.toLong() }.toSet()
    }

    private val logger = xLogStack()

    fun pickSource(url: String): List<UrlImportableSource> {
        val uri = url.toUri()
        return sourceManager.getVisibleCatalogueSources()
            .map { it.getMainSource() }
            .filterIsInstance<UrlImportableSource>()
            .filter {
                it.lang in filters.first && it.id !in filters.second && try {
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
        throttleFunc: () -> Unit = {}
    ): GalleryAddEvent {
        logger.d(context.getString(R.string.gallery_adder_importing_manga, url, fav.toString(), forceSource))
        try {
            val uri = url.toUri()

            // Find matching source
            val source = if (forceSource != null) {
                try {
                    if (forceSource.matchesUri(uri)) forceSource
                    else return GalleryAddEvent.Fail.UnknownSource(url, context)
                } catch (e: Exception) {
                    logger.e(context.getString(R.string.gallery_adder_source_uri_must_match), e)
                    return GalleryAddEvent.Fail.UnknownType(url, context)
                }
            } else {
                sourceManager.getVisibleCatalogueSources()
                    .map { it.getMainSource() }
                    .filterIsInstance<UrlImportableSource>()
                    .find {
                        it.lang in filters.first && it.id !in filters.second && try {
                            it.matchesUri(uri)
                        } catch (e: Exception) {
                            false
                        }
                    } ?: return GalleryAddEvent.Fail.UnknownSource(url, context)
            }

            val realChapterUrl = try {
                source.mapUrlToChapterUrl(uri)
            } catch (e: Exception) {
                logger.e(context.getString(R.string.gallery_adder_uri_map_to_chapter_error), e)
                null
            }

            val cleanedChapterUrl = if (realChapterUrl != null) {
                try {
                    source.cleanChapterUrl(realChapterUrl)
                } catch (e: Exception) {
                    logger.e(context.getString(R.string.gallery_adder_uri_clean_error), e)
                    null
                }
            } else null

            val chapterMangaUrl = if (realChapterUrl != null) {
                source.mapChapterUrlToMangaUrl(realChapterUrl.toUri())
            } else null

            // Map URL to manga URL
            val realMangaUrl = try {
                chapterMangaUrl ?: source.mapUrlToMangaUrl(uri)
            } catch (e: Exception) {
                logger.e(context.getString(R.string.gallery_adder_uri_map_to_manga_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Clean URL
            val cleanedMangaUrl = try {
                source.cleanMangaUrl(realMangaUrl)
            } catch (e: Exception) {
                logger.e(context.getString(R.string.gallery_adder_uri_clean_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Use manga in DB if possible, otherwise, make a new manga
            val manga = db.getManga(cleanedMangaUrl, source.id).executeOnIO()
                ?: Manga.create(source.id).apply {
                    this.url = cleanedMangaUrl
                    title = realMangaUrl
                }

            // Insert created manga if not in DB before fetching details
            // This allows us to keep the metadata when fetching details
            if (manga.id == null) {
                db.insertManga(manga).executeOnIO().insertedId()?.let {
                    manga.id = it
                }
            }

            // Fetch and copy details
            val newManga = source.getMangaDetails(manga.toMangaInfo())
            manga.copyFrom(newManga.toSManga())
            manga.initialized = true

            if (fav) {
                manga.favorite = true
                manga.date_added = System.currentTimeMillis()
            }

            db.insertManga(manga).executeOnIO()

            // Fetch and copy chapters
            try {
                val chapterList = if (source is EHentai) {
                    source.getChapterList(manga.toMangaInfo(), throttleFunc)
                } else {
                    source.getChapterList(manga.toMangaInfo())
                }.map { it.toSChapter() }

                if (chapterList.isNotEmpty()) {
                    syncChaptersWithSource(db, chapterList, manga, source)
                }
            } catch (e: Exception) {
                logger.w(context.getString(R.string.gallery_adder_chapter_fetch_error, manga.title), e)
                return GalleryAddEvent.Fail.Error(url, context.getString(R.string.gallery_adder_chapter_fetch_error, url))
            }

            return if (cleanedChapterUrl != null) {
                val chapter = db.getChapter(cleanedChapterUrl, manga.id!!).executeOnIO()
                if (chapter != null) {
                    GalleryAddEvent.Success(url, manga, context, chapter)
                } else {
                    GalleryAddEvent.Fail.Error(url, context.getString(R.string.gallery_adder_could_not_identify_chapter, url))
                }
            } else {
                GalleryAddEvent.Success(url, manga, context)
            }
        } catch (e: Exception) {
            logger.w(context.getString(R.string.gallery_adder_could_not_add_manga, url), e)

            if (e is EHentai.GalleryNotFoundException) {
                return GalleryAddEvent.Fail.NotFound(url, context)
            }

            return GalleryAddEvent.Fail.Error(
                url,
                ((e.message ?: "Unknown error!") + " (Gallery: $url)").trim()
            )
        }
    }
}

sealed class GalleryAddEvent {
    abstract val logMessage: String
    abstract val galleryUrl: String
    open val galleryTitle: String? = null

    class Success(
        override val galleryUrl: String,
        val manga: Manga,
        val context: Context,
        val chapter: Chapter? = null
    ) : GalleryAddEvent() {
        override val galleryTitle = manga.title
        override val logMessage = context.getString(R.string.batch_add_success_log_message, galleryTitle)
    }

    sealed class Fail : GalleryAddEvent() {
        class UnknownType(override val galleryUrl: String, val context: Context) : Fail() {
            override val logMessage = context.getString(R.string.batch_add_unknown_type_log_message, galleryUrl)
        }

        open class Error(
            override val galleryUrl: String,
            override val logMessage: String
        ) : Fail()

        class NotFound(galleryUrl: String, context: Context) :
            Error(galleryUrl, context.getString(R.string.batch_add_not_exist_log_message, galleryUrl))

        class UnknownSource(override val galleryUrl: String, val context: Context) : Fail() {
            override val logMessage = context.getString(R.string.batch_add_unknown_source_log_message, galleryUrl)
        }
    }
}
