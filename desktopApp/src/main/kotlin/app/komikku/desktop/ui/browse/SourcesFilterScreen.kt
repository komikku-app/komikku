package app.komikku.desktop.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Android's source filter screen on desktop: the borrowed [SourcesFilterScreenModel] (same `SourcePreferences`), a
 * switch per language, an "all sources" switch and a check box per source of an enabled language.
 */
data object SourcesFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { SourcesFilterScreenModel() }
        val state by model.state.collectAsState()
        BrowseScaffold(title = stringResource(MR.strings.label_sources), onBack = navigator::pop) {
            when (val current = state) {
                SourcesFilterScreenModel.State.Loading -> LoadingScreen()
                is SourcesFilterScreenModel.State.Error -> EmptyScreen(stringRes = MR.strings.internal_error)
                is SourcesFilterScreenModel.State.Success -> if (current.isEmpty) {
                    EmptyScreen(stringRes = MR.strings.source_filter_empty_screen)
                } else {
                    SourcesFilterContent(
                        state = current,
                        onClickLanguage = model::toggleLanguage,
                        onClickSource = model::toggleSource,
                        onClickSources = model::toggleSources,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourcesFilterContent(
    state: SourcesFilterScreenModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    onClickSources: (Boolean, List<Source>) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("sources-filter-list")) {
        state.items.forEach { (language, sources) ->
            val enabled = language in state.enabledLanguages
            stickyHeader(key = "language-$language") {
                SwitchPreferenceWidget(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background).testTag("language-$language"),
                    title = LocaleHelper.getSourceDisplayName(language, context) +
                        (" (${LocaleHelper.getDisplayName(language)})".takeIf { language !in listOf("all", "other") } ?: ""),
                    checked = enabled,
                    onCheckedChanged = { onClickLanguage(language) },
                )
            }
            if (enabled) {
                item(key = "toggle-$language") {
                    val allEnabled = remember(state.disabledSources, sources) {
                        sources.none { it.id.toString() in state.disabledSources }
                    }
                    SwitchPreferenceWidget(
                        modifier = Modifier.testTag("language-all-$language"),
                        title = stringResource(SYMR.strings.pref_category_all_sources),
                        checked = allEnabled,
                        onCheckedChanged = { onClickSources(!allEnabled, sources) },
                    )
                }
                items(items = sources, key = { "source-${it.key()}" }) { source ->
                    val sourceEnabled = "${source.id}" !in state.disabledSources
                    ListItem(
                        modifier = Modifier.testTag("filter-source-${source.id}").clickable { onClickSource(source) },
                        headlineContent = { Text(source.name) },
                        trailingContent = { Checkbox(checked = sourceEnabled, onCheckedChange = null) },
                    )
                }
            }
        }
    }
}
