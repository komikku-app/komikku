package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.clearFocusOnSoftKeyboardHide
import tachiyomi.presentation.core.util.isScrollingUp
import tachiyomi.presentation.core.util.runOnEnterKeyPressed
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal

@Composable
fun SourcesScreen(
    state: SourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    // KMK -->
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    onChangeSearchQuery: (String?) -> Unit,
    // KMK <--
) {
    // KMK -->
    val lazyListState = rememberLazyListState()

    BackHandler(enabled = !state.searchQuery.isNullOrBlank()) {
        onChangeSearchQuery(null)
    }
    // KMK <--

    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        // KMK -->
        state.searchQuery.isNullOrBlank() &&
            // KMK <--
            state.isEmpty -> EmptyScreen(
            MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        // KMK -->
        else -> Column(
            // Wrap around so we can use stickyHeader
            modifier = Modifier.padding(contentPadding),
        ) {
            AnimatedFloatingSearchBox(
                listState = lazyListState,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                placeholderText = stringResource(KMR.strings.action_search_for_source),
            )

            FastScrollLazyColumn(
                state = lazyListState,
                // KMK <--
            ) {
                items(
                    items = state.items,
                    contentType = {
                        when (it) {
                            is SourceUiModel.Header -> "header"
                            is SourceUiModel.Item -> "item"
                        }
                    },
                    key = {
                        when (it) {
                            is SourceUiModel.Header -> "header-${it.hashCode()}"
                            is SourceUiModel.Item -> "source-${it.source.key()}"
                        }
                    },
                ) { model ->
                    when (model) {
                        is SourceUiModel.Header -> {
                            SourceHeader(
                                modifier = Modifier.animateItemFastScroll(),
                                language = model.language,
                                // SY -->
                                isCategory = model.isCategory,
                                // SY <--
                            )
                        }
                        is SourceUiModel.Item -> SourceItem(
                            modifier = Modifier.animateItemFastScroll(),
                            source = model.source,
                            // SY -->
                            showLatest = state.showLatest,
                            showPin = state.showPin,
                            // SY <--
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                        )
                    }
                }
            }
        }
    }
}

// KMK -->
@Composable
fun AnimatedFloatingSearchBox(
    listState: LazyListState,
    searchQuery: String?,
    onChangeSearchQuery: (String?) -> Unit,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    placeholderText: String? = null,
) {
    AnimatedVisibility(
        visible = listState.isScrollingUp(),
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        SourcesSearch(
            searchQuery = searchQuery,
            onChangeSearchQuery = onChangeSearchQuery,
            placeholderText = placeholderText,
        )
    }
}

@Composable
fun SourcesSearch(
    searchQuery: String?,
    onChangeSearchQuery: (String?) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val searchAndClearFocus: () -> Unit = f@{
        if (searchQuery.isNullOrBlank()) return@f
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val onClickClearSearch: () -> Unit = {
        onChangeSearchQuery(null)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    val onClickCloseSearch: () -> Unit = {
        onClickClearSearch()
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = searchQuery ?: "",
        onValueChange = onChangeSearchQuery,
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .padding(12.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .runOnEnterKeyPressed(action = searchAndClearFocus)
            .clearFocusOnSoftKeyboardHide(),
        enabled = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { searchAndClearFocus() }),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = searchQuery ?: "",
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = remember { MutableInteractionSource() },
                placeholder = {
                    Text(
                        modifier = Modifier.secondaryItemAlpha(),
                        text = (placeholderText ?: stringResource(MR.strings.action_search_hint)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    SearchBoxLeadingIcon(
                        isFocused || !searchQuery.isNullOrBlank(),
                        modifier = Modifier,
                        onClickCloseSearch,
                    )
                },
                trailingIcon = {
                    SearchBoxTrailingIcon(
                        searchQuery.isNullOrEmpty(),
                        modifier = Modifier,
                        onClickClearSearch,
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onBackground,
                ),
                contentPadding = PaddingValues(12.dp),
            )
        },
    )
}

@Composable
fun SearchBoxLeadingIcon(
    isSearching: Boolean,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    onClickCloseSearch: () -> Unit = {},
) {
    if (isSearching) {
        IconButton(
            onClick = onClickCloseSearch,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close",
            )
        }
    } else {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
        )
    }
}

@Composable
fun SearchBoxTrailingIcon(
    isEmpty: Boolean,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    onClickClearSearch: () -> Unit = {},
) {
    if (!isEmpty) {
        IconButton(
            onClick = onClickClearSearch,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
            )
        }
    }
}
// KMK <--

@Composable
private fun SourceHeader(
    language: String,
    // SY -->
    isCategory: Boolean,
    // SY <--
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Text(
        // SY -->
        text = if (!isCategory) {
            LocaleHelper.getSourceDisplayName(language, context)
        } else {
            language
        },
        // SY <--
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun SourceItem(
    source: Source,
    // SY -->
    showLatest: Boolean,
    showPin: Boolean,
    // SY <--
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest /* SY --> */ && showLatest /* SY <-- */) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            // SY -->
            if (showPin) {
                SourcePinButton(
                    isPinned = Pin.Pinned in source.pin,
                    onClick = { onClickPin(source) },
                )
            }
            // SY <--
        },
    )
}

@Composable
private fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    // SY -->
    onClickSetCategories: (() -> Unit)?,
    onClickToggleDataSaver: (() -> Unit)?,
    // SY <--
    onDismiss: () -> Unit,
    // KMK -->
    onClickSettings: (() -> Unit)? = null,
    // KMK <--
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (!source.isLocal()) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY -->
                if (onClickSetCategories != null) {
                    Text(
                        text = stringResource(MR.strings.categories),
                        modifier = Modifier
                            .clickable(onClick = onClickSetCategories)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                if (onClickToggleDataSaver != null) {
                    Text(
                        text = if (source.isExcludedFromDataSaver) {
                            stringResource(SYMR.strings.data_saver_stop_exclude)
                        } else {
                            stringResource(SYMR.strings.data_saver_exclude)
                        },
                        modifier = Modifier
                            .clickable(onClick = onClickToggleDataSaver)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY <--
                // KMK -->
                if (onClickSettings != null &&
                    source.installedExtension !== null &&
                    source.id !in listOf(LocalSource.ID, EH_SOURCE_ID, EXH_SOURCE_ID)
                ) {
                    Text(
                        text = stringResource(MR.strings.label_extension_info),
                        modifier = Modifier
                            .clickable(onClick = onClickSettings)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // KMK <--
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface SourceUiModel {
    data class Item(val source: Source) : SourceUiModel
    data class Header(val language: String, val isCategory: Boolean) : SourceUiModel
}

// SY -->
@Composable
fun SourceCategoriesDialog(
    source: Source,
    categories: ImmutableList<String>,
    onClickCategories: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val newCategories = remember(source) {
        mutableStateListOf<String>().also { it += source.categories }
    }
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    LabeledCheckbox(
                        label = category,
                        checked = category in newCategories,
                        onCheckedChange = {
                            if (it) {
                                newCategories += category
                            } else {
                                newCategories -= category
                            }
                        },
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onClickCategories(newCategories.toList()) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
// SY <--
