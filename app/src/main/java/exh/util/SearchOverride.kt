package exh.util

import android.content.Context
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import exh.GalleryAddEvent
import exh.GalleryAdder
import rx.Observable
import tachiyomi.core.util.lang.runAsObservable

private val galleryAdder by lazy {
    GalleryAdder()
}

/**
 * A version of fetchSearchManga that supports URL importing
 */
fun UrlImportableSource.urlImportFetchSearchManga(
    context: Context,
    query: String,
    fail: () -> Observable<MangasPage>,
): Observable<MangasPage> =
    when {
        query.startsWith("http://") || query.startsWith("https://") -> {
            runAsObservable {
                galleryAdder.addGallery(context, query, false, this@urlImportFetchSearchManga)
            }
                .map { res ->
                    MangasPage(
                        if (res is GalleryAddEvent.Success) {
                            listOf(res.manga.toSManga())
                        } else {
                            emptyList()
                        },
                        false,
                    )
                }
        }
        else -> fail()
    }

/**
 * A version of fetchSearchManga that supports URL importing
 */
suspend fun UrlImportableSource.urlImportFetchSearchMangaSuspend(
    context: Context,
    query: String,
    fail: suspend () -> MangasPage,
): MangasPage =
    when {
        query.startsWith("http://") || query.startsWith("https://") -> {
            val res = galleryAdder.addGallery(
                context = context,
                url = query,
                fav = false,
                forceSource = this,
            )

            MangasPage(
                if (res is GalleryAddEvent.Success) {
                    listOf(res.manga.toSManga())
                } else {
                    emptyList()
                },
                false,
            )
        }
        else -> fail()
    }
