@file:JvmName("ExtensionReposScreenKt")

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.browse.RepoScreenState
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.persistentSetOf
import mihon.domain.extensionrepo.interactor.CreateExtensionRepo.Companion.OFFICIAL_REPO_WEBSITE
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun ExtensionReposScreen(
    state: RepoScreenState.Success,
    onClickCreate: () -> Unit,
    onOpenWebsite: (ExtensionRepo) -> Unit,
    onClickDelete: (String) -> Unit,
    // KMK -->
    onClickEnable: (String) -> Unit,
    onClickDisable: (String) -> Unit,
    // KMK <--
    onClickRefresh: () -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(MR.strings.label_extension_repos),
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
                MR.strings.information_empty_repos,
                modifier = Modifier.padding(paddingValues),
                help = {
                    TextButton(
                        onClick = { context.openInBrowser(OFFICIAL_REPO_WEBSITE) },
                        modifier = Modifier.padding(top = MaterialTheme.padding.small),
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.Help, contentDescription = null)
                        Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                        Text(text = stringResource(MR.strings.label_help))
                    }
                },
            )
            return@Scaffold
        }

        ExtensionReposContent(
            repos = state.repos,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topSmallPaddingValues +
                PaddingValues(horizontal = MaterialTheme.padding.medium),
            onOpenWebsite = onOpenWebsite,
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
@Preview
@Composable
private fun ExtensionReposScreenPreview() {
    val state = RepoScreenState.Success(
        repos = persistentSetOf(
            ExtensionRepo("https://raw.githubusercontent.com/komikku-app/extensions/repo", "Komikku", "", "", "key1"),
            ExtensionRepo("https://raw.githubusercontent.com/keiyoushi/extensions/repo", "Keiyoushi", "", "", "key2"),
            ExtensionRepo("https://repo", "Other", "", "", "key2"),
        ),
        disabledRepos = setOf("https://repo"),
    )
    ExtensionReposScreen(
        state = state,
        onClickCreate = { },
        onOpenWebsite = { },
        onClickDelete = { },
        onClickEnable = { },
        onClickDisable = { },
        onClickRefresh = { },
        navigateUp = { },
    )
}

@Preview
@Composable
private fun ExtensionReposScreenEmptyPreview() {
    val state = RepoScreenState.Success(repos = persistentSetOf())
    ExtensionReposScreen(
        state = state,
        onClickCreate = { },
        onOpenWebsite = { },
        onClickDelete = { },
        onClickEnable = { },
        onClickDisable = { },
        onClickRefresh = { },
        navigateUp = { },
    )
}
// KMK <--
