package exh

import android.content.Context
import android.net.Uri
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import java.util.Date
import uy.kohesive.injekt.injectLazy

class GalleryAdder {

    private val db: DatabaseHelper by injectLazy()

    private val sourceManager: SourceManager by injectLazy()

    fun addGallery(
        context: Context,
        url: String,
        fav: Boolean = false,
        forceSource: UrlImportableSource? = null,
        throttleFunc: () -> Unit = {}
    ): GalleryAddEvent {
        XLog.d(context.getString(R.string.gallery_adder_importing_gallery, url, fav.toString(), forceSource))
        try {
            val uri = Uri.parse(url)

            // Find matching source
            val source = if (forceSource != null) {
                try {
                    if (forceSource.matchesUri(uri)) forceSource
                    else return GalleryAddEvent.Fail.UnknownType(url, context)
                } catch (e: Exception) {
                    XLog.e(context.getString(R.string.gallery_adder_source_uri_must_match), e)
                    return GalleryAddEvent.Fail.UnknownType(url, context)
                }
            } else {
                sourceManager.getVisibleCatalogueSources()
                    .filterIsInstance<UrlImportableSource>()
                    .find {
                        try {
                            it.matchesUri(uri)
                        } catch (e: Exception) {
                            XLog.e(context.getString(R.string.gallery_adder_source_uri_must_match), e)
                            false
                        }
                    } ?: sourceManager.getDelegatedCatalogueSources()
                    .filterIsInstance<UrlImportableSource>()
                    .find {
                        try {
                            it.matchesUri(uri)
                        } catch (e: Exception) {
                            XLog.e(context.getString(R.string.gallery_adder_source_uri_must_match), e)
                            false
                        }
                    } ?: return GalleryAddEvent.Fail.UnknownType(url, context)
            }

            // Map URL to manga URL
            val realUrl = try {
                source.mapUrlToMangaUrl(uri)
            } catch (e: Exception) {
                XLog.e(context.getString(R.string.gallery_adder_uri_map_to_manga_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Clean URL
            val cleanedUrl = try {
                source.cleanMangaUrl(realUrl)
            } catch (e: Exception) {
                XLog.e(context.getString(R.string.gallery_adder_uri_clean_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Use manga in DB if possible, otherwise, make a new manga
            val manga = db.getManga(cleanedUrl, source.id).executeAsBlocking()
                ?: Manga.create(source.id).apply {
                    this.url = cleanedUrl
                    title = realUrl
                }

            // Insert created manga if not in DB before fetching details
            // This allows us to keep the metadata when fetching details
            if (manga.id == null) {
                db.insertManga(manga).executeAsBlocking().insertedId()?.let {
                    manga.id = it
                }
            }

            // Fetch and copy details
            val newManga = source.fetchMangaDetails(manga).toBlocking().first()
            manga.copyFrom(newManga)
            manga.initialized = true

            if (fav) {
                manga.favorite = true
                manga.date_added = Date().time
            }

            db.insertManga(manga).executeAsBlocking()

            // Fetch and copy chapters
            try {
                val chapterListObs = if (source is EHentai) {
                    source.fetchChapterList(manga, throttleFunc)
                } else {
                    source.fetchChapterList(manga)
                }
                chapterListObs.map {
                    syncChaptersWithSource(db, it, manga, source)
                }.toBlocking().first()
            } catch (e: Exception) {
                XLog.w(context.getString(R.string.gallery_adder_chapter_fetch_error, manga.title), e)
                return GalleryAddEvent.Fail.Error(url, context.getString(R.string.gallery_adder_chapter_fetch_error, url))
            }

            return GalleryAddEvent.Success(url, manga, context)
        } catch (e: Exception) {
            XLog.w(context.getString(R.string.gallery_adder_could_not_add_gallery, url), e)

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
        val context: Context
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
    }
}
