package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoreScreenState
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.KOMIKKU_SIGNATURE
import mihon.domain.extension.model.REPO_HELP
import mihon.domain.extension.model.REPO_SIGNATURE
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun ExtensionStoresScreen(
    state: ExtensionStoreScreenState.Success,
    onClickCreate: () -> Unit,
    onCopy: (ExtensionStore) -> Unit,
    onOpenWebsite: (ExtensionStore) -> Unit,
    onOpenDiscord: (ExtensionStore) -> Unit,
    onClickDelete: (ExtensionStore) -> Unit,
    // KMK -->
    onClickEnable: (ExtensionStore) -> Unit,
    onClickDisable: (ExtensionStore) -> Unit,
    // KMK <--
    onClickRefresh: () -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(MR.strings.extensionStores),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onClickRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(resource = MR.strings.action_webview_refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            val context = LocalContext.current
            EmptyScreen(
                MR.strings.extensionStoresScreen_emptyLabel,
                modifier = Modifier.padding(paddingValues),
                // KMK -->
                help = {
                    TextButton(
                        onClick = { context.openInBrowser(REPO_HELP) },
                        modifier = Modifier.padding(top = MaterialTheme.padding.small),
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.Help, contentDescription = null)
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                        Text(text = stringResource(MR.strings.label_help))
                    }
                },
                // KMK <--
            )
            return@Scaffold
        }

        ExtensionStoresContent(
            repos = state.stores,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topSmallPaddingValues +
                PaddingValues(horizontal = MaterialTheme.padding.medium),
            onCopy = onCopy,
            onOpenWebsite = onOpenWebsite,
            onOpenDiscord = onOpenDiscord,
            onClickDelete = onClickDelete,
            // KMK -->
            onClickEnable = onClickEnable,
            onClickDisable = onClickDisable,
            disabledRepos = state.disabledRepos,
            // KMK <--
        )
    }
}

// KMK -->
@PreviewLightDark
@Composable
private fun ExtensionStoresScreenPreview() {
    val state = ExtensionStoreScreenState.Success(
        stores = persistentListOf(
            ExtensionStore("https://komikku", "Komikku", "", KOMIKKU_SIGNATURE, ExtensionStore.Contact("", ""), false, null),
            ExtensionStore("https://repo", "Repo", "", REPO_SIGNATURE, ExtensionStore.Contact("", ""), false, null),
            ExtensionStore("https://other", "Other", "", "key2", ExtensionStore.Contact("", ""), true, null),
        ),
        disabledRepos = setOf("https://repo"),
    )
    TachiyomiPreviewTheme {
        Surface {
            ExtensionStoresScreen(
                state = state,
                onClickCreate = { },
                onCopy = { },
                onOpenWebsite = { },
                onOpenDiscord = { },
                onClickDelete = { },
                onClickEnable = { },
                onClickDisable = { },
                onClickRefresh = { },
                navigateUp = { },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ExtensionStoresScreenEmptyPreview() {
    val state = ExtensionStoreScreenState.Success(stores = persistentListOf())
    TachiyomiPreviewTheme {
        Surface {
            ExtensionStoresScreen(
                state = state,
                onClickCreate = { },
                onCopy = { },
                onOpenWebsite = { },
                onOpenDiscord = { },
                onClickDelete = { },
                onClickEnable = { },
                onClickDisable = { },
                onClickRefresh = { },
                navigateUp = { },
            )
        }
    }
}
// KMK <--
