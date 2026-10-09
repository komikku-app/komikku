package eu.kanade.tachiyomi.ui.browse.recommendations

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.personalRecommendationsTab(bulkFavoriteScreenModel: BulkFavoriteScreenModel): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val tabNavigator = LocalTabNavigator.current
    val screenModel = rememberScreenModel { PersonalRecommendationsScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = KMR.strings.personal_recommendations,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_webview_refresh),
                icon = Icons.Outlined.Refresh,
                onClick = screenModel::refresh,
            ),
        ),
        content = { contentPadding, _ ->
            LaunchedEffect(screenModel) { screenModel.start() }
            val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
            val haptic = LocalHapticFeedback.current
            when {
                state.isPreparing -> LoadingScreen()
                state.needsRatings -> EmptyScreen(
                    KMR.strings.personal_recommendations_need_ratings,
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.label_library,
                            icon = Icons.Outlined.CollectionsBookmark,
                            onClick = { tabNavigator.current = LibraryTab },
                        ),
                    ),
                )
                state.preparationFailed -> EmptyScreen(
                    KMR.strings.personal_recommendations_load_failed,
                    actions = retryAction(screenModel::refresh),
                )
                state.isFinished && state.allSourcesFailed -> EmptyScreen(
                    KMR.strings.personal_recommendations_load_failed,
                    actions = retryAction(screenModel::refresh),
                )
                state.isFinished && !state.hasResults -> EmptyScreen(
                    KMR.strings.personal_recommendations_no_results,
                    actions = retryAction(screenModel::refresh),
                )
                else -> PersonalRecommendationsContent(
                    state = state,
                    contentPadding = contentPadding,
                    onClickSource = { navigator.push(BrowseSourceScreen(it.id, GetRemoteManga.QUERY_POPULAR)) },
                    onClickManga = { navigator.push(MangaScreen(it.id, true)) },
                    onLongClickManga = { bulkFavoriteScreenModel.addRemoveManga(it, haptic) },
                    getManga = screenModel::getManga,
                )
            }
            BulkFavoriteDialogs(
                bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                dialog = bulkFavoriteState.dialog,
            )
        },
    )
}

@Composable
private fun PersonalRecommendationsContent(
    state: PersonalRecommendationsScreenModel.State,
    contentPadding: PaddingValues,
    onClickSource: (eu.kanade.tachiyomi.source.Source) -> Unit,
    onClickManga: (tachiyomi.domain.manga.model.Manga) -> Unit,
    onLongClickManga: (tachiyomi.domain.manga.model.Manga) -> Unit,
    getManga: @Composable (tachiyomi.domain.manga.model.Manga) -> androidx.compose.runtime.State<tachiyomi.domain.manga.model.Manga>,
) {
    LazyColumn(contentPadding = contentPadding) {
        state.items.forEach { (source, result) ->
            item(key = "personal-recommendations-${source.id}") {
                GlobalSearchResultItem(
                    title = source.name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                ) {
                    when (result) {
                        PersonalRecommendationsScreenModel.Result.Loading -> GlobalSearchLoadingResultItem()
                        is PersonalRecommendationsScreenModel.Result.Error -> {
                            GlobalSearchErrorResultItem(
                                stringResource(KMR.strings.personal_recommendations_source_failed),
                            )
                        }
                        is PersonalRecommendationsScreenModel.Result.Success -> {
                            GlobalSearchCardRow(
                                titles = result.manga,
                                getManga = getManga,
                                onClick = onClickManga,
                                onLongClick = onLongClickManga,
                                selection = emptyList(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun retryAction(onClick: () -> Unit) = persistentListOf(
    EmptyScreenAction(
        stringRes = MR.strings.action_retry,
        icon = Icons.Outlined.Refresh,
        onClick = onClick,
    ),
)
