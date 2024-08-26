package eu.kanade.tachiyomi.ui.storage

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaStorageScreenModel(
    downloadCache: DownloadCache = Injekt.get(),
    private val getLibraries: GetLibraryManga = Injekt.get(),
    getCategories: GetCategories = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : CommonStorageScreenModel<LibraryManga>(
    downloadCacheChanges = downloadCache.changes,
    downloadCacheIsInitializing = downloadCache.isInitializing,
    libraries = getLibraries.subscribe(),
    categories = getCategories.subscribe(),
    getDownloadSize = { downloadManager.getDownloadSize(manga) },
    getDownloadCount = { downloadManager.getDownloadCount(manga) },
    getId = { id },
    getCategoryId = { category },
    getTitle = { manga.title },
    getThumbnail = { manga.thumbnailUrl },
) {
    override fun deleteEntry(id: Long) {
        screenModelScope.launchNonCancellable {
            val manga = getLibraries.await().find {
                it.id == id
            }?.manga ?: return@launchNonCancellable
            val source = sourceManager.get(manga.source) ?: return@launchNonCancellable
            downloadManager.deleteManga(manga, source)
        }
    }
}
