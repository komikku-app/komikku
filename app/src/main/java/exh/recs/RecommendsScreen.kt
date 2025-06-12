package exh.recs

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import exh.recs.RecommendsScreen.Args.MergedSourceMangas
import exh.recs.RecommendsScreen.Args.SingleSourceManga
import exh.recs.batch.RankedSearchResults
import exh.recs.components.RecommendsScreen
import exh.recs.sources.RECOMMENDS_SOURCE
import exh.recs.sources.StaticResultPagingSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.io.Serializable

class RecommendsScreen(private val args: Args) : Screen() {

    sealed interface Args : Serializable {
        data class SingleSourceManga(val mangaId: Long, val sourceId: Long) : Args
        data class MergedSourceMangas(val mergedResults: List<RankedSearchResults>) : Args
    }

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { RecommendsScreenModel(args) }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (manga.source) {
                    RECOMMENDS_SOURCE -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    else -> MangaScreen(manga.id, true)
                },
            )
        }

        val onLongClickItem = { manga: Manga ->
            when (manga.source) {
                RECOMMENDS_SOURCE -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                else -> {
                    // KMK -->
                    // Add to favorite
                    bulkFavoriteScreenModel.addRemoveManga(
                        manga,
                        haptic,
                    )
                    // KMK <--
                }
            }
        }

        RecommendsScreen(
            title = if (args is SingleSourceManga) {
                stringResource(SYMR.strings.similar, state.title.orEmpty())
            } else {
                stringResource(SYMR.strings.rec_common_recommendations)
            },
            state = state,
            navigateUp = navigator::pop,
            getManga = @Composable { manga: Manga -> screenModel.getManga(manga) },
            onClickSource = { pagingSource ->
                // Pass class name of paging source as screens need to be serializable
                navigator.push(
                    BrowseRecommendsScreen(
                        when (args) {
                            is SingleSourceManga ->
                                BrowseRecommendsScreen.Args.SingleSourceManga(
                                    args.mangaId,
                                    args.sourceId,
                                    pagingSource::class.qualifiedName!!,
                                )
                            is MergedSourceMangas ->
                                BrowseRecommendsScreen.Args.MergedSourceMangas(
                                    (pagingSource as StaticResultPagingSource).data,
                                )
                        },
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
