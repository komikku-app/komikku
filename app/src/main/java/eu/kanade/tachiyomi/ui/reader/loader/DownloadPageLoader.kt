package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.translation.TextTranslations
import eu.kanade.translation.TranslationManager
import mihon.core.archive.archiveReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val translationManager: TranslationManager,
) : PageLoader() {

    private val context: Application by injectLazy()

    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(dbChapter.name, dbChapter.scanlator, /* SY --> */ manga.ogTitle /* SY <-- */, source)
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile): List<ReaderPage> {
        val loader = ArchivePageLoader(
            file.archiveReader(context),
            chapter,
            manga,
            source,
            translationManager,
        ).also { archivePageLoader = it }
        return loader.getPages()
    }

    private fun getPagesFromDirectory(): List<ReaderPage> {
        val dbChapter = chapter.chapter.toDomainChapter()!!
        val chapterDir = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            manga.title,
            source,
        )
        val files = chapterDir?.listFiles().orEmpty()
            .filter { "image" in it.type.orEmpty() }.sortedBy { it.name }

        if (files.isEmpty()) {
            throw Exception(context.stringResource(MR.strings.page_list_empty_error))
        }
        val pageTranslations: Map<String, TextTranslations> = translationManager.getChapterTranslation(
            chapter.chapter.name,
            chapter.chapter.scanlator,
            manga.title,
            source,
        )
        val pages = files.mapIndexed { i, file ->
            Page(i, uri = file.uri).apply {
                status = Page.State.READY
            }
        }
        return pages.mapIndexed { i, page ->
            ReaderPage(page.index, page.url, page.imageUrl, translations = pageTranslations[files[i].name]) {
                context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!!
            }.apply {
                status = Page.State.READY
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}
