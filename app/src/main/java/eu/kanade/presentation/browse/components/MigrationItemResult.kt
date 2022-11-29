package eu.kanade.presentation.browse.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga
import eu.kanade.tachiyomi.util.lang.withIOContext

@Composable
fun MigrationItemResult(
    modifier: Modifier,
    migrationItem: MigratingManga,
    result: MigratingManga.SearchResult,
    getManga: suspend (MigratingManga.SearchResult.Result) -> Manga?,
    getChapterInfo: suspend (MigratingManga.SearchResult.Result) -> MigratingManga.ChapterInfo,
    getSourceName: (Manga) -> String,
    onMigrationItemClick: (Manga) -> Unit,
) {
    Box(modifier) {
        when (result) {
            MigratingManga.SearchResult.Searching -> Box(
                modifier = Modifier
                    .widthIn(max = 150.dp)
                    .fillMaxWidth()
                    .aspectRatio(MangaCover.Book.ratio),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            MigratingManga.SearchResult.NotFound -> Image(
                painter = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            is MigratingManga.SearchResult.Result -> {
                val item by produceState<Triple<Manga, MigratingManga.ChapterInfo, String>?>(
                    initialValue = null,
                    migrationItem,
                    result,
                ) {
                    value = withIOContext {
                        val manga = getManga(result) ?: return@withIOContext null
                        Triple(
                            manga,
                            getChapterInfo(result),
                            getSourceName(manga),
                        )
                    }
                }
                if (item != null) {
                    val (manga, chapterInfo, source) = item!!
                    MigrationItem(
                        modifier = Modifier,
                        manga = manga,
                        sourcesString = source,
                        chapterInfo = chapterInfo,
                        onClick = {
                            onMigrationItemClick(manga)
                        },
                    )
                }
            }
        }
    }
}
