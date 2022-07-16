package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okhttp3.CacheControl
import okhttp3.Response
import tachiyomi.source.model.MangaInfo

interface PagePreviewSource : Source {

    suspend fun getPagePreviewList(manga: MangaInfo, page: Int): PagePreviewPage

    suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl? = null): Response
}

@Serializable
data class PagePreviewPage(
    val page: Int,
    val pagePreviews: List<PagePreviewInfo>,
    val hasNextPage: Boolean,
    val pagePreviewPages: Int?,
)

@Serializable
data class PagePreviewInfo(
    val index: Int,
    val imageUrl: String,
    @Transient
    private val _progress: MutableStateFlow<Int> = MutableStateFlow(-1),
) : ProgressListener {
    @Transient
    val progress = _progress.asStateFlow()

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        _progress.value = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }
}
