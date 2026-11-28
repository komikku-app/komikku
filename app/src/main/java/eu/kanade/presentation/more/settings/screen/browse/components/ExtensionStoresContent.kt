package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.R
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extension.model.ANIKKU_SIGNATURE
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.KOMIKKU_SIGNATURE
import mihon.domain.extension.model.REPO_SIGNATURE
import mihon.domain.extension.model.githubAvatarUrl
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Discord

@Composable
fun ExtensionStoresContent(
    repos: List<ExtensionStore>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onCopy: (ExtensionStore) -> Unit,
    onOpenWebsite: (ExtensionStore) -> Unit,
    onOpenDiscord: (ExtensionStore) -> Unit,
    onClickDelete: (ExtensionStore) -> Unit,
    // KMK -->
    onClickEnable: (ExtensionStore) -> Unit,
    onClickDisable: (ExtensionStore) -> Unit,
    disabledRepos: Set<String>,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = modifier,
    ) {
        repos.forEach {
            item {
                ExtensionStoresListItem(
                    modifier = Modifier.animateItem(),
                    store = it,
                    onOpenWebsite = { onOpenWebsite(it) },
                    onOpenDiscord = { onOpenDiscord(it) },
                    onCopy = { onCopy(it) },
                    onDelete = { onClickDelete(it) },
                    // KMK -->
                    onEnable = { onClickEnable(it) },
                    onDisable = { onClickDisable(it) },
                    isDisabled = it.indexUrl in disabledRepos,
                    // KMK <--
                )
            }
        }
    }
}

@Composable
private fun ExtensionStoresListItem(
    store: ExtensionStore,
    onOpenWebsite: () -> Unit,
    onOpenDiscord: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // KMK -->
    isDisabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    // KMK <--
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        // KMK -->
        Row(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium),
        ) {
            ExtensionStoreIcon(
                store = store,
                isDisabled = isDisabled,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .align(Alignment.CenterVertically),
            )
            Column {
                // KMK <--
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = MaterialTheme.padding.medium,
                            top = MaterialTheme.padding.medium,
                            end = MaterialTheme.padding.medium,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = store.name,
                        // KMK: modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                        style = MaterialTheme.typography.titleMedium,
                        // KMK -->
                        color = LocalContentColor.current.let { if (isDisabled) it.copy(alpha = 0.6f) else it },
                        textDecoration = TextDecoration.LineThrough.takeIf { isDisabled },
                        // KMK <--
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onOpenWebsite) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = stringResource(MR.strings.action_open_in_browser),
                        )
                    }

                    if (store.contact.discord != null) {
                        IconButton(onClick = onOpenDiscord) {
                            Icon(
                                imageVector = CustomIcons.Discord,
                                contentDescription = null,
                            )
                        }
                    }

                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                        )
                    }

                    // KMK -->
                    IconButton(onClick = if (isDisabled) onEnable else onDisable) {
                        Icon(
                            imageVector = if (isDisabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = stringResource(MR.strings.action_disable),
                        )
                    }
                    // KMK <--

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(MR.strings.action_delete),
                        )
                    }
                }
            }
        }
    }
}

// KMK -->
/**
 * Bundled icon for stores signed by a known key, otherwise the GitHub owner's avatar for GitHub-hosted stores,
 * otherwise the generic extension icon.
 */
@Composable
private fun ExtensionStoreIcon(
    store: ExtensionStore,
    isDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (isDisabled) 0.4f else 1f
    val knownResId = repoResId(store.signingKey)
    val avatarUrl = if (knownResId == null) store.githubAvatarUrl else null

    if (avatarUrl != null) {
        val fallback = painterResource(id = R.mipmap.extension)
        val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
        val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            alpha = alpha,
            placeholder = placeholder,
            error = fallback,
            fallback = fallback,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(id = knownResId ?: R.mipmap.extension),
            contentDescription = null,
            alpha = alpha,
            modifier = modifier,
        )
    }
}

/** Bundled icon of a store signed by a known key, or null for other stores. */
fun repoResId(signKey: String) = when (signKey) {
    KOMIKKU_SIGNATURE, ANIKKU_SIGNATURE -> R.mipmap.komikku
    REPO_SIGNATURE -> R.mipmap.repo
    else -> null
}

@PreviewLightDark
@Composable
fun ExtensionReposContentPreview() {
    val repos = persistentListOf(
        ExtensionStore("https://komikku", "Komikku", "", KOMIKKU_SIGNATURE, ExtensionStore.Contact("", ""), false, null),
        ExtensionStore("https://repo", "Repo", "", REPO_SIGNATURE, ExtensionStore.Contact("", ""), false, null),
        ExtensionStore("https://other", "Other", "", "key2", ExtensionStore.Contact("", ""), true, null),
        ExtensionStore(
            "https://raw.githubusercontent.com/yuzono/manga-repo/repo",
            "Yuzono",
            "",
            "key3",
            ExtensionStore.Contact("", ""),
            false,
            null,
        ),
    )
    TachiyomiPreviewTheme {
        Surface {
            ExtensionStoresContent(
                repos = repos,
                lazyListState = LazyListState(),
                paddingValues = PaddingValues(),
                onCopy = {},
                onOpenWebsite = {},
                onOpenDiscord = {},
                onClickDelete = {},
                onClickEnable = {},
                onClickDisable = {},
                disabledRepos = setOf("https://repo"),
            )
        }
    }
}
// KMK <--
