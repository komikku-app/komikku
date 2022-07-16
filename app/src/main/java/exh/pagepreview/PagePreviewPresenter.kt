package exh.pagepreview

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PagePreviewPresenter(
    private val mangaId: Long,
    private val getPagePreviews: GetPagePreviews = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : BasePresenter<PagePreviewController>() {

    private val _state = MutableStateFlow<PagePreviewState>(PagePreviewState.Loading)
    val state = _state.asStateFlow()

    private val page = MutableStateFlow(1)

    var pageDialogOpen by mutableStateOf(false)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        presenterScope.launchIO {
            val manga = getManga.await(mangaId)!!
            val source = sourceManager.getOrStub(manga.source)
            page
                .onEach { page ->
                    when (
                        val previews = getPagePreviews.await(manga, source, page)
                    ) {
                        is GetPagePreviews.Result.Error -> _state.update {
                            PagePreviewState.Error(previews.error)
                        }
                        is GetPagePreviews.Result.Success -> _state.update {
                            when (it) {
                                PagePreviewState.Loading, is PagePreviewState.Error -> {
                                    PagePreviewState.Success(
                                        page,
                                        previews.pagePreviews,
                                        previews.hasNextPage,
                                        previews.pageCount,
                                        manga,
                                        source,
                                    )
                                }
                                is PagePreviewState.Success -> it.copy(
                                    page = page,
                                    pagePreviews = previews.pagePreviews,
                                    hasNextPage = previews.hasNextPage,
                                    pageCount = previews.pageCount,
                                ).also { logcat { page.toString() } }
                            }
                        }
                        GetPagePreviews.Result.Unused -> Unit
                    }
                }
                .catch { e ->
                    _state.update {
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
    object Loading : PagePreviewState()

    data class Success(
        val page: Int,
        val pagePreviews: List<PagePreview>,
        val hasNextPage: Boolean,
        val pageCount: Int?,
        val manga: Manga,
        val source: Source,
    ) : PagePreviewState()

    data class Error(val error: Throwable) : PagePreviewState()
}
