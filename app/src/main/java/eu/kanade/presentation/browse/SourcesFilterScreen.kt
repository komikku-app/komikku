package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.FilterUiModel
import eu.kanade.tachiyomi.ui.browse.source.SourceFilterState
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper

@Composable
fun SourcesFilterScreen(
    nestedScrollInterop: NestedScrollConnection,
    presenter: SourcesFilterPresenter,
    onClickLang: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    // SY -->
    onClickSources: (Boolean, List<Source>) -> Unit,
    // SY <--
) {
    val state by presenter.state.collectAsState()

    when (state) {
        is SourceFilterState.Loading -> LoadingScreen()
        is SourceFilterState.Error -> Text(text = (state as SourceFilterState.Error).error.message!!)
        is SourceFilterState.Success ->
            SourcesFilterContent(
                nestedScrollInterop = nestedScrollInterop,
                items = (state as SourceFilterState.Success).models,
                onClickLang = onClickLang,
                onClickSource = onClickSource,
                // SY -->
                onClickSources = onClickSources,
                // SY <--
            )
    }
}

@Composable
fun SourcesFilterContent(
    nestedScrollInterop: NestedScrollConnection,
    items: List<FilterUiModel>,
    onClickLang: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    // SY -->
    onClickSources: (Boolean, List<Source>) -> Unit,
    // SY <--
) {
    if (items.isEmpty()) {
        EmptyScreen(textResource = R.string.source_filter_empty_screen)
        return
    }

    ScrollbarLazyColumn(
        modifier = Modifier.nestedScroll(nestedScrollInterop),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(
            items = items,
            contentType = {
                when (it) {
                    is FilterUiModel.Header -> "header"
                    // SY -->
                    is FilterUiModel.ToggleHeader -> "toggle"
                    // SY <--
                    is FilterUiModel.Item -> "item"
                }
            },
            key = {
                when (it) {
                    is FilterUiModel.Header, is FilterUiModel.ToggleHeader -> it.hashCode()
                    is FilterUiModel.Item -> it.source.key()
                }
            },
        ) { model ->
            when (model) {
                is FilterUiModel.Header -> {
                    SourcesFilterHeader(
                        modifier = Modifier.animateItemPlacement(),
                        language = model.language,
                        enabled = model.enabled,
                        onClickItem = onClickLang,
                    )
                }
                // SY -->
                is FilterUiModel.ToggleHeader -> {
                    SourcesFilterToggle(
                        modifier = Modifier.animateItemPlacement(),
                        isEnabled = model.enabled,
                        onClickItem = {
                            onClickSources(!model.enabled, model.sources)
                        },
                    )
                }
                // SY <--
                is FilterUiModel.Item -> SourcesFilterItem(
                    modifier = Modifier.animateItemPlacement(),
                    source = model.source,
                    enabled = model.enabled,
                    onClickItem = onClickSource,
                )
            }
        }
    }
}

@Composable
fun SourcesFilterHeader(
    modifier: Modifier,
    language: String,
    enabled: Boolean,
    onClickItem: (String) -> Unit,
) {
    PreferenceRow(
        modifier = modifier,
        title = LocaleHelper.getSourceDisplayName(language, LocalContext.current),
        action = {
            Switch(checked = enabled, onCheckedChange = null)
        },
        onClick = { onClickItem(language) },
    )
}

// SY -->
@Composable
fun SourcesFilterToggle(
    modifier: Modifier,
    isEnabled: Boolean,
    onClickItem: () -> Unit,
) {
    PreferenceRow(
        modifier = modifier,
        title = stringResource(R.string.pref_category_all_sources),
        action = {
            Switch(checked = isEnabled, onCheckedChange = null)
        },
        onClick = { onClickItem() },
        painter = remember { ColorPainter(Color.Transparent) },
    )
}

// SY <--

@Composable
fun SourcesFilterItem(
    modifier: Modifier,
    source: Source,
    enabled: Boolean,
    onClickItem: (Source) -> Unit,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        showLanguageInContent = false,
        onClickItem = { onClickItem(source) },
        action = {
            Checkbox(checked = enabled, onCheckedChange = null)
        },
    )
}
