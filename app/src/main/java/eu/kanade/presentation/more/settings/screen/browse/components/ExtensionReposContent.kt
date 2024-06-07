package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableSet
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ExtensionReposContent(
    repos: ImmutableSet<ExtensionRepo>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onOpenWebsite: (ExtensionRepo) -> Unit,
    onClickDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sourcePreferences = Injekt.get<SourcePreferences>()
    val disabledRepos by remember { sourcePreferences.disabledRepos().asState(scope) }

    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = modifier,
    ) {
        repos.forEach {
            item {
                val isDisabled = it.baseUrl in disabledRepos
                ExtensionRepoListItem(
                    modifier = Modifier.animateItemPlacement(),
                    repo = it,
                    onOpenWebsite = { onOpenWebsite(it) },
                    onDelete = { onClickDelete(it.baseUrl) },
                    isDisabled = isDisabled,
                    onEnableDisable = {
                        if (it.baseUrl in disabledRepos) {
                            sourcePreferences.disabledRepos().set(
                                disabledRepos.filterNot { baseUrl -> baseUrl == it.baseUrl }.toSet()
                            )
                        } else {
                            sourcePreferences.disabledRepos().set(
                                disabledRepos + it.baseUrl
                            )
                        }
                    },
                    onEdit = { },
                )
            }
        }
    }
}

@Composable
private fun ExtensionRepoListItem(
    repo: ExtensionRepo,
    onOpenWebsite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isDisabled: Boolean,
    onEnableDisable: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = modifier,
    ) {
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
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                tint = LocalContentColor.current.let { if (isDisabled) it.copy(alpha = 0.6f) else it },
            )
            Text(
                text = repo.name,
                modifier = Modifier.padding(start = MaterialTheme.padding.medium),
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current.let { if (isDisabled) it.copy(alpha = 0.6f) else it },
                textDecoration = TextDecoration.LineThrough.takeIf { isDisabled },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onOpenWebsite) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(MR.strings.action_open_in_browser),
                )
            }

            IconButton(
                onClick = {
                    val url = "${repo.baseUrl}/index.min.json"
                    context.copyToClipboard(url, url)
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(MR.strings.action_copy_to_clipboard),
                )
            }

            IconButton(onClick = onEnableDisable) {
                Icon(
                    imageVector = if (isDisabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(MR.strings.action_disable),
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_edit),
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
    }
}
