package exh.ui.metadata

import androidx.annotation.ColorInt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.plus

class MetadataViewScreen(
    private val mangaId: Long,
    private val sourceId: Long,
    // KMK -->
    @ColorInt private val seedColor: Int?,
    // KMK <--
) : Screen() {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { MetadataViewScreenModel(mangaId, sourceId) }
        val navigator = LocalNavigator.currentOrThrow

        val state by screenModel.state.collectAsState()

        @Composable
        fun content() = Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = screenModel.manga.collectAsState().value?.title,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            when (
                @Suppress("NAME_SHADOWING")
                val state = state
            ) {
                MetadataViewState.Loading -> LoadingScreen()
                MetadataViewState.MetadataNotFound -> EmptyScreen(MR.strings.no_results_found)
                MetadataViewState.SourceNotFound -> EmptyScreen(MR.strings.source_empty_screen)
                is MetadataViewState.Success -> {
                    val context = LocalContext.current
                    val items = remember(state.meta) { state.meta.getExtraInfoPairs(context) }
                    ScrollbarLazyColumn(
                        contentPadding =
                        paddingValues + WindowInsets.navigationBars.asPaddingValues() + topSmallPaddingValues,
                    ) {
                        items(items) { (title, text) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickableNoIndication(
                                        onLongClick = {
                                            context.copyToClipboard(
                                                title,
                                                text,
                                            )
                                        },
                                        onClick = {},
                                    )
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    title,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .padding(start = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, end = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalContentColor.current.copy(alpha = 0.7F),
                                )
                            }
                        }
                    }
                }
            }
        }

        // KMK -->
        TachiyomiTheme(
            seedColor = seedColor?.let { Color(seedColor) }.takeIf { screenModel.themeCoverBased },
        ) {
            // KMK <--
            content()
        }
    }
}
