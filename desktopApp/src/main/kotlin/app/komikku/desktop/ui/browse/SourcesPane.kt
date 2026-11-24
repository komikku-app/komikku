package app.komikku.desktop.ui.browse

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.shared.jssource.JsSourceLabel
import app.komikku.sourcejs.mihon.MangayomiJsSource
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

/**
 * Desktop counterpart of Android's `SourcesScreenModel` (sources tab): the enabled sources from the same
 * [GetEnabledSources] (enabled languages, disabled sources, pins, last used source, SY source categories) grouped the
 * same way (last used, pinned, categories, then languages; sources without a language last), pin / disable through the
 * same interactors. The Android model is not borrowed: it needs the SY smart-search / source category / data saver
 * screens and Android presentation types.
 */
class SourcesPaneModel(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    getSourceCategories: GetSourceCategories = Injekt.get(),
    private val setSourceCategories: SetSourceCategories = Injekt.get(),
) : StateScreenModel<SourcesPaneModel.State>(State()) {

    init {
        // P21: SY's source categories, for the source's "Categories" dialog
        getSourceCategories.subscribe()
            .onEach { categories -> mutableState.update { it.copy(categories = categories) } }
            .catch { xLogE("Failed to read the source categories", it) }
            .launchIn(screenModelScope)
        // A failure (e.g. a malformed SY source category entry) shows an error instead of "no sources"; the flow is
        // subscribed again on the next change of what it reads, so a fixed preference brings the list back.
        getEnabledSources.subscribe()
            .onEach { sources ->
                mutableState.update { it.copy(isLoading = false, error = null, items = groupSources(sources)) }
            }
            .retryWhen { e, _ ->
                if (e is CancellationException) return@retryWhen false
                xLogE("Failed to read the enabled sources", e)
                mutableState.update { it.copy(isLoading = false, error = e.message ?: e::class.simpleName.orEmpty()) }
                inputChanges().first()
                true
            }
            .launchIn(screenModelScope)
    }

    /** The next change of anything [GetEnabledSources] reads. */
    private fun inputChanges(): Flow<Any?> = with(sourcePreferences) {
        merge(
            pinnedSources().changes().drop(1),
            enabledLanguages().changes().drop(1),
            disabledSources().changes().drop(1),
            lastUsedSource().changes().drop(1),
            dataSaverExcludedSources().changes().drop(1),
            sourcesTabSourcesInCategories().changes().drop(1),
            sourcesTabCategoriesFilter().changes().drop(1),
            sourceManager.sources.drop(1),
        )
    }

    fun togglePin(source: Source) {
        screenModelScope.launchIO { toggleSourcePin.await(source) }
    }

    /** Android's "Disable" action: the source is hidden until it is enabled again in the source filter screen. */
    fun disable(source: Source) {
        screenModelScope.launchIO { toggleSource.await(source.id, enable = false) }
        closeDialog()
    }

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = source) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null, categoriesDialog = null) }
    }

    /** P21: SY's "Categories" of the source options dialog. */
    fun showCategoriesDialog(source: Source) {
        mutableState.update { it.copy(dialog = null, categoriesDialog = source) }
    }

    /** P21: the source in exactly these SY source categories (Android's `SetSourceCategories`). */
    fun setCategories(source: Source, categories: List<String>) {
        screenModelScope.launchIO { setSourceCategories.await(source, categories) }
        closeDialog()
    }

    sealed interface Item {
        data class Header(val key: String, val isCategory: Boolean) : Item
        data class Entry(val source: Source, val groupKey: String) : Item
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: List<Item> = emptyList(),
        /** Why the enabled sources could not be read, if they could not. */
        val error: String? = null,
        val dialog: Source? = null,
        /** P21: the source whose SY categories are being chosen. */
        val categoriesDialog: Source? = null,
        /** P21: SY's source categories, by name. */
        val categories: List<String> = emptyList(),
    )

    companion object {
        const val PINNED_KEY = LocaleHelper.PINNED_KEY
        const val LAST_USED_KEY = LocaleHelper.LAST_USED_KEY
        const val CATEGORY_KEY_PREFIX = "category-"

        /** The grouping of Android's `SourcesScreenModel.collectLatestSources`. */
        fun groupSources(sources: List<Source>): List<Item> {
            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                when {
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    d1.startsWith(CATEGORY_KEY_PREFIX) && !d2.startsWith(CATEGORY_KEY_PREFIX) -> -1
                    d2.startsWith(CATEGORY_KEY_PREFIX) && !d1.startsWith(CATEGORY_KEY_PREFIX) -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byGroup = sources.groupByTo(map) {
                when {
                    it.category != null -> "$CATEGORY_KEY_PREFIX${it.category}"
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
            }
            return byGroup.flatMap { (key, group) ->
                listOf(Item.Header(key.removePrefix(CATEGORY_KEY_PREFIX), key.startsWith(CATEGORY_KEY_PREFIX))) +
                    group.map { Item.Entry(it, key) }
            }
        }
    }
}

/**
 * The sources tab: global search and source filter actions (Android's sources tab toolbar), the grouped enabled sources;
 * click opens the catalogue, the pin button pins, long press or right click offers pin / disable (Android's source
 * options dialog), configurable sources have a settings button.
 */
@Composable
internal fun SourcesPane(model: SourcesPaneModel) {
    val navigator = LocalNavigator.currentOrThrow
    val state by model.state.collectAsState()
    // P13: like Android's sources tab, a source opens its feed (landing page) while SY's "new source navigation" is on
    val useNewSourceNavigation by remember { Injekt.get<UiPreferences>().useNewSourceNavigation() }.collectAsState()
    // P22: Android's "Extension info" for a source of an installed APK extension (not the local source)
    val extensionInfo: (Source) -> (() -> Unit)? = { source ->
        source.installedExtension?.takeUnless { source.isLocal() }?.let { ext -> { navigator.push(ExtensionDetailsScreen(ext.pkgName)) } }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { navigator.push(GlobalSearchScreen()) }) {
                Icon(Icons.Outlined.TravelExplore, contentDescription = stringResource(MR.strings.action_global_search))
            }
            IconButton(onClick = { navigator.push(SourcesFilterScreen) }) {
                Icon(Icons.Outlined.FilterList, contentDescription = stringResource(MR.strings.action_filter))
            }
        }
        val error = state.error
        when {
            state.isLoading -> LoadingScreen()
            error != null -> EmptyScreen(message = stringResource(KMR.strings.desktop_source_error, error))
            state.items.isEmpty() -> EmptyScreen(stringRes = KMR.strings.desktop_no_sources)
            else -> SourcesList(
                items = state.items,
                onClick = { navigator.push(if (useNewSourceNavigation) SourceFeedScreen(it.id) else CatalogueScreen(it.id)) },
                onClickSettings = { navigator.push(SourcePreferencesScreen(it.id)) },
                onClickPin = model::togglePin,
                onLongClick = model::showSourceDialog,
                onDisable = model::disable,
                onSetCategories = model::showCategoriesDialog.takeIf { state.categories.isNotEmpty() },
                extensionInfo = extensionInfo,
            )
        }
    }
    state.dialog?.let { source ->
        SourceOptionsDialog(
            source = source,
            onClickPin = {
                model.togglePin(source)
                model.closeDialog()
            },
            onClickDisable = { model.disable(source) },
            onClickSetCategories = { model.showCategoriesDialog(source) }.takeIf { state.categories.isNotEmpty() },
            onClickExtensionInfo = extensionInfo(source)?.let { open ->
                {
                    model.closeDialog()
                    open()
                }
            },
            onDismiss = model::closeDialog,
        )
    }
    state.categoriesDialog?.let { source ->
        SourceCategoriesDialog(
            source = source,
            categories = state.categories,
            onConfirm = { model.setCategories(source, it) },
            onDismiss = model::closeDialog,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourcesList(
    items: List<SourcesPaneModel.Item>,
    onClick: (Source) -> Unit,
    onClickSettings: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClick: (Source) -> Unit,
    onDisable: (Source) -> Unit,
    onSetCategories: ((Source) -> Unit)?,
    extensionInfo: (Source) -> (() -> Unit)?,
) {
    val context = LocalContext.current
    val sourceManager = remember { Injekt.get<SourceManager>() }
    // Read again whenever the installed sources change, so an updated or reinstalled source is not a stale instance
    val installedSources by remember { sourceManager.sources }.collectAsState(initial = null)
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("sources-list")) {
        items.forEach { item ->
            when (item) {
                is SourcesPaneModel.Item.Header -> stickyHeader(key = "header-${item.key}-${item.isCategory}") {
                    ListGroupHeader(
                        text = if (item.isCategory) item.key else LocaleHelper.getSourceDisplayName(item.key, context),
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
                    )
                }
                is SourcesPaneModel.Item.Entry -> item(key = "source-${item.source.key()}") {
                    val source = item.source
                    val live = remember(source.id, installedSources) { sourceManager.get(source.id) }
                    val isPinned = Pin.Pinned in source.pin
                    val pinLabel = stringResource(if (isPinned) MR.strings.action_unpin else MR.strings.action_pin)
                    val disableLabel = stringResource(MR.strings.action_disable)
                    // P22: the options dialog's "Categories" and "Extension info" here too
                    val categoriesLabel = stringResource(MR.strings.categories)
                    val extensionInfoLabel = stringResource(MR.strings.label_extension_info)
                    ContextMenuArea(
                        items = {
                            listOfNotNull(
                                ContextMenuItem(pinLabel) { onClickPin(source) },
                                ContextMenuItem(disableLabel) { onDisable(source) }.takeUnless { source.isLocal() },
                                onSetCategories?.let { ContextMenuItem(categoriesLabel) { it(source) } },
                                extensionInfo(source)?.let { ContextMenuItem(extensionInfoLabel, it) },
                            )
                        },
                    ) {
                        ListItem(
                            modifier = Modifier
                                .testTag("source-${source.key()}")
                                .combinedClickable(onClick = { onClick(source) }, onLongClick = { onLongClick(source) }),
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = source.name)
                                    if (live is MangayomiJsSource) JsSourceLabel()
                                }
                            },
                            // Android shows the language of sources outside their language group
                            supportingContent = if (item.groupKey != source.lang) {
                                { Text(LocaleHelper.getSourceDisplayName(source.lang, context)) }
                            } else {
                                null
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (source.isLocal()) Icons.Outlined.Folder else Icons.Outlined.Public,
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onClickPin(source) }) {
                                        Icon(
                                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            tint = if (isPinned) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            contentDescription = pinLabel,
                                        )
                                    }
                                    if (live is ConfigurableSource) {
                                        IconButton(onClick = { onClickSettings(source) }) {
                                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Android's `SourceOptionsDialog` without SY's data saver entry (P21: with SY's categories; P22: with Komikku's extension
 * info).
 */
@Composable
private fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onClickSetCategories: (() -> Unit)?,
    onClickExtensionInfo: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = source.visualName) },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier.clickable(onClick = onClickPin).fillMaxWidth().padding(vertical = 16.dp),
                )
                if (!source.isLocal()) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier.clickable(onClick = onClickDisable).fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
                // P21: like Android, only when there are source categories
                if (onClickSetCategories != null) {
                    Text(
                        text = stringResource(MR.strings.categories),
                        modifier = Modifier
                            .clickable(onClick = onClickSetCategories)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .testTag("source-options-categories"),
                    )
                }
                // P22: like Android, for a source of an installed extension
                if (onClickExtensionInfo != null) {
                    Text(
                        text = stringResource(MR.strings.label_extension_info),
                        modifier = Modifier
                            .clickable(onClick = onClickExtensionInfo)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .testTag("source-options-extension-info"),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

/** P21: Android's `SourceCategoriesDialog` (SY): the source's categories as checkboxes, OK sets them. */
@Composable
private fun SourceCategoriesDialog(
    source: Source,
    categories: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember(source) { mutableStateListOf<String>().also { it += source.categories } }
    AlertDialog(
        title = { Text(text = source.visualName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    LabeledCheckbox(
                        label = category,
                        checked = category in chosen,
                        onCheckedChange = { if (it) chosen += category else chosen -= category },
                        modifier = Modifier.testTag("source-categories-$category"),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen.toList()) }, modifier = Modifier.testTag("source-categories-ok")) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
