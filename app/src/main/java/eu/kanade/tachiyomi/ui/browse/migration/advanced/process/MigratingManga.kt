package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import eu.kanade.domain.manga.model.Manga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext

class MigratingManga(
    val manga: Manga,
    val chapterInfo: ChapterInfo,
    val sourcesString: String,
    parentContext: CoroutineContext,
    val getManga: suspend (SearchResult.Result) -> Manga?,
    val getChapterInfo: suspend (SearchResult.Result) -> ChapterInfo,
    val getSourceName: (Manga) -> String,
) {
    val migrationScope = CoroutineScope(parentContext + SupervisorJob() + Dispatchers.Default)

    val searchResult = MutableStateFlow<SearchResult>(SearchResult.Searching)

    // <MAX, PROGRESS>
    val progress = MutableStateFlow(1 to 0)

    sealed class SearchResult {
        object Searching : SearchResult()
        object NotFound : SearchResult()
        data class Result(val id: Long) : SearchResult()
    }

    data class ChapterInfo(
        val latestChapter: Float?,
        val chapterCount: Int,
    )

    fun toModal(): MigrationProcessItem {
        // Create the model object.
        return MigrationProcessItem(this)
    }
}
