package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.content.Context
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.manga.model.Manga
import java.text.DecimalFormat
import kotlin.coroutines.CoroutineContext

class MigratingManga(
    val manga: Manga,
    val chapterInfo: ChapterInfo,
    val sourcesString: String,
    parentContext: CoroutineContext,
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
    ) {
        fun getFormattedLatestChapter(context: Context): String {
            return if (latestChapter != null && latestChapter > 0f) {
                context.getString(
                    R.string.latest_,
                    DecimalFormat("#.#").format(latestChapter),
                )
            } else {
                context.getString(
                    R.string.latest_,
                    context.getString(R.string.unknown),
                )
            }
        }
    }
}
