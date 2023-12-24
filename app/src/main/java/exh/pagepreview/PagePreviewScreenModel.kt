package exh.pagepreview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PagePreviewScreenModel(
    private val mangaId: Long,
    private val getPagePreviews: GetPagePreviews = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<PagePreviewState>(PagePreviewState.Loading) {

    private val page = MutableStateFlow(1)

    var pageDialogOpen by mutableStateOf(false)

    init {
        screenModelScope.launchIO {
            val manga = getManga.await(mangaId)!!
            val chapter = getChaptersByMangaId.await(mangaId).minByOrNull { it.sourceOrder }
            if (chapter == null) {
                mutableState.update {
                    PagePreviewState.Error(Exception("No chapters found"))
                }
                return@launchIO
            }
            val source = sourceManager.getOrStub(manga.source)
            page
                .onEach { page ->
                    when (
                        val previews = getPagePreviews.await(manga, source, page)
                    ) {
                        is GetPagePreviews.Result.Error -> mutableState.update {
                            PagePreviewState.Error(previews.error)
                        }
                        is GetPagePreviews.Result.Success -> mutableState.update {
                            when (it) {
                                PagePreviewState.Loading, is PagePreviewState.Error -> {
                                    PagePreviewState.Success(
                                        page,
                                        previews.pagePreviews,
                                        previews.hasNextPage,
                                        previews.pageCount,
                                        manga,
                                        chapter,
                                        source,
                                    )
                                }
                                is PagePreviewState.Success -> it.copy(
                                    page = page,
                                    pagePreviews = previews.pagePreviews,
                                    hasNextPage = previews.hasNextPage,
                                    pageCount = previews.pageCount,
                                )
                            }
                        }
                        GetPagePreviews.Result.Unused -> Unit
                    }
                }
                .catch { e ->
                    mutableState.update {
                        PagePreviewState.Error(e)
                    }
                }
                .collect()
        }
    }

    fun moveToPage(page: Int) {
        this.page.value = page
    }
}

sealed class PagePreviewState {
    data object Loading : PagePreviewState()

    data class Success(
        val page: Int,
        val pagePreviews: List<PagePreview>,
        val hasNextPage: Boolean,
        val pageCount: Int?,
        val manga: Manga,
        val chapter: Chapter,
        val source: Source,
    ) : PagePreviewState()

    data class Error(val error: Throwable) : PagePreviewState()
}
