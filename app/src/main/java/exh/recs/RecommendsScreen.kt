package exh.recs

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.recs.components.RecommendsScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.screens.LoadingScreen

class RecommendsScreen(val mangaId: Long, val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            RecommendsScreenModel(mangaId = mangaId, sourceId = sourceId)
        }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val scope = rememberCoroutineScope()
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        val onClickItem = { manga: Manga ->
            when (manga.source) {
                RECOMMENDS_SOURCE -> navigator.push(
                    SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle)),
                )
                else -> {
                    // KMK -->
                    scope.launchIO {
                        val localManga = screenModel.networkToLocalManga(manga)
                        navigator.push(
                            // KMK <--
                            MangaScreen(localManga.id, true),
                        )
                    }
                }
            }
        }

        val onLongClickItem = { manga: Manga ->
            when (manga.source) {
                RECOMMENDS_SOURCE -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                else -> {
                    // KMK -->
                    scope.launchIO {
                        val localManga = screenModel.networkToLocalManga(manga)
                        bulkFavoriteScreenModel.addRemoveManga(
                            localManga,
                            haptic,
                        )
                    }
                    // KMK <--
                }
            }
        }

        RecommendsScreen(
            manga = state.manga,
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga -> screenModel.getManga(manga) },
            onClickSource = { pagingSource ->
                // Pass class name of paging source as screens need to be serializable
                navigator.push(
                    BrowseRecommendsScreen(
                        mangaId,
                        sourceId,
                        pagingSource::class.qualifiedName!!,
                        pagingSource.associatedSourceId == null,
                    ),
                )
            },
            onClickItem = { onClickItem(it) },
            onLongClickItem = { onLongClickItem(it) },
        )

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}
