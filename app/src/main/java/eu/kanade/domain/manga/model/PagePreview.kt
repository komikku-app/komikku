package eu.kanade.domain.manga.model

import eu.kanade.tachiyomi.source.PagePreviewInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PagePreview(
    val index: Int,
    val imageUrl: String,
    val source: Long,
) {
    @Transient
    private val _progress: MutableStateFlow<Int> = MutableStateFlow(-1)

    @Transient
    val progress = _progress.asStateFlow()

    fun getPagePreviewInfo() = PagePreviewInfo(index, imageUrl, _progress)
}
