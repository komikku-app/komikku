package app.komikku.desktop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Minimal manga details: cover, title, author, description and the chapter list fetched from the
 * source, plus add to / remove from library (`UpdateManga.awaitUpdateFavorite`, as on Android).
 * The full shared manga screen is Phase 6.2.
 */
class MangaDetailsScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { MangaDetailsScreenModel(mangaId) }
        val state by model.state.collectAsState()
        val manga = state.manga
        BrowseScaffold(title = manga?.title.orEmpty(), onBack = navigator::pop) {
            if (manga == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                DetailsContent(state, manga, onToggleFavorite = model::toggleFavorite)
            }
        }
    }
}

class MangaDetailsScreenModel(
    private val mangaId: Long,
    private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
) : StateScreenModel<MangaDetailsScreenModel.State>(State()) {

    data class State(
        val manga: Manga? = null,
        val chapters: List<SChapter> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    init {
        screenModelScope.launch {
            getManga.subscribe(mangaId).collectLatest { manga -> mutableState.update { it.copy(manga = manga) } }
        }
        screenModelScope.launch { fetchFromSource() }
    }

    private suspend fun fetchFromSource() {
        try {
            val manga = withIOContext { getManga.await(mangaId) } ?: return
            val source = sourceManager.get(manga.source) ?: error("Source ${manga.source} is not installed")
            val update = withIOContext {
                source.getMangaUpdate(manga.toSourceManga(), emptyList(), fetchDetails = true, fetchChapters = true)
            }
            val details = update.manga
            withIOContext {
                updateManga.await(
                    MangaUpdate(
                        id = mangaId,
                        title = details.title.takeIf { it.isNotBlank() },
                        author = details.author,
                        artist = details.artist,
                        description = details.description,
                        genre = details.getGenres(),
                        status = details.status.toLong(),
                        thumbnailUrl = details.thumbnail_url ?: manga.thumbnailUrl,
                        initialized = true,
                    ),
                )
            }
            mutableState.update { it.copy(chapters = update.chapters, isLoading = false) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            mutableState.update { it.copy(isLoading = false, error = e.message ?: e::class.simpleName) }
        }
    }

    fun toggleFavorite() {
        val manga = state.value.manga ?: return
        screenModelScope.launch { withIOContext { updateManga.awaitUpdateFavorite(manga.id, !manga.favorite) } }
    }

    /** The source-side view of a stored manga (Android: `Manga.toSManga()`, in `app`). */
    private fun Manga.toSourceManga(): SManga = SManga.create().also {
        it.url = url
        it.title = ogTitle
        it.artist = ogArtist
        it.author = ogAuthor
        it.description = ogDescription
        it.genre = ogGenre?.joinToString()
        it.status = ogStatus.toInt()
        it.thumbnail_url = ogThumbnailUrl
        it.initialized = initialized
    }
}

@Composable
private fun DetailsContent(state: MangaDetailsScreenModel.State, manga: Manga, onToggleFavorite: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item(key = "header") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.width(160.dp)) { CoverItem(manga = manga, onClick = {}) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(manga.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(manga.author, manga.artist?.takeIf { it != manga.author })
                            .joinToString().ifEmpty { stringResource(MR.strings.unknown_author) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (manga.favorite) {
                        Button(onClick = onToggleFavorite) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(MR.strings.in_library))
                        }
                    } else {
                        OutlinedButton(onClick = onToggleFavorite) {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(MR.strings.add_to_library))
                        }
                    }
                    manga.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    manga.genre?.takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(key = "chapters-header") {
            Text(
                text = stringResource(MR.strings.chapters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            )
        }
        when {
            state.isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.error != null -> item(key = "error") { Text(stringResource(KMR.strings.desktop_source_error, state.error)) }
            else -> items(state.chapters, key = { "chapter-${it.url}" }) { chapter ->
                ListItem(
                    headlineContent = { Text(chapter.name) },
                    supportingContent = chapter.scanlator?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                )
            }
        }
    }
}
