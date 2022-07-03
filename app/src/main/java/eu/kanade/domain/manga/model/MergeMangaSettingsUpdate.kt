package eu.kanade.domain.manga.model

data class MergeMangaSettingsUpdate(
    val id: Long,
    var isInfoManga: Boolean?,
    var getChapterUpdates: Boolean?,
    var chapterPriority: Int?,
    var downloadChapters: Boolean?,
)
