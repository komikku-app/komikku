package eu.kanade.tachiyomi.ui.reader.setting

import android.text.format.Formatter
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.upscale.ModelDownloadManager
import eu.kanade.tachiyomi.util.upscale.ModelDownloadState
import eu.kanade.tachiyomi.util.upscale.ModelManifestLoader
import eu.kanade.tachiyomi.util.upscale.UpscaleModel
import eu.kanade.tachiyomi.util.upscale.progressPercent
import eu.kanade.tachiyomi.util.upscale.toChapterDownloadState
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private data class ModelVariantRow(
    val model: UpscaleModel,
    val batchSize: Int,
)

class UpscaleModelSelectionScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val readerPreferences = remember { Injekt.get<ReaderPreferences>() }
        val downloadManager = remember { Injekt.get<ModelDownloadManager>() }
        val wifiOnly by readerPreferences.aiUpscaleWifiOnlyDownloads().collectAsState()
        val selectedModel by readerPreferences.aiUpscaleModel().collectAsState()
        val selectedBatch by readerPreferences.aiUpscaleBatchSize().collectAsState()
        val context = LocalContext.current
        var infoDialogVariant by remember { mutableStateOf<UpscaleModel?>(null) }

        val allVariants = remember {
            UpscaleModel.entries.flatMap { model -> model.availableBatchSizes.map { ModelVariantRow(model, it) } }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.pref_ai_upscale_model),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { navigator.push(ModelLicensesScreen()) }) {
                            Icon(
                                imageVector = Icons.Outlined.Gavel,
                                contentDescription = stringResource(KMR.strings.action_model_licenses),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            LazyColumn(contentPadding = contentPadding) {
                items(allVariants, key = { "${it.model.name}_${it.batchSize}" }) { variant ->
                    val downloadState by downloadManager
                        .observeState(variant.model, variant.batchSize)
                        .collectAsState(initial = ModelDownloadState.NotDownloaded)

                    val isSelected = selectedModel == variant.model && selectedBatch == variant.batchSize

                    val entry = remember(variant) { ModelManifestLoader.entryFor(context, variant.model, variant.batchSize) }
                    val formattedSize = entry?.let { Formatter.formatShortFileSize(context, it.sizeBytes) }

                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(variant.model.displayName)
                                IconButton(
                                    onClick = { infoDialogVariant = variant.model },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                        contentDescription = stringResource(KMR.strings.action_model_info),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                formattedSize?.let {
                                    stringResource(KMR.strings.upscale_model_batch_size, variant.batchSize, it)
                                } ?: variant.batchSize.toString(),
                            )
                        },
                        leadingContent = {
                            if (downloadState is ModelDownloadState.Downloaded) {
                                IconButton(onClick = {
                                    readerPreferences.aiUpscaleModel().set(variant.model)
                                    readerPreferences.aiUpscaleBatchSize().set(variant.batchSize)
                                }) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        },
                        trailingContent = {
                            if (downloadState is ModelDownloadState.Downloaded) {
                                IconButton(
                                    onClick = {
                                        if (isSelected) {
                                            context.toast(KMR.strings.pref_ai_upscale_cannot_delete_active_model)
                                        } else {
                                            downloadManager.deleteDownloaded(variant.model, variant.batchSize)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(KMR.strings.action_delete_upscale_model),
                                    )
                                }
                            } else {
                                ChapterDownloadIndicator(
                                    enabled = true,
                                    downloadStateProvider = { downloadState.toChapterDownloadState() },
                                    downloadProgressProvider = { downloadState.progressPercent() },
                                    onClick = { action ->
                                        when (action) {
                                            ChapterDownloadAction.START, ChapterDownloadAction.START_NOW ->
                                                downloadManager.enqueueDownload(variant.model, variant.batchSize, wifiOnly)
                                            ChapterDownloadAction.CANCEL ->
                                                downloadManager.cancelDownload(variant.model, variant.batchSize)
                                            ChapterDownloadAction.DELETE -> Unit
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            }
            infoDialogVariant?.let { model ->
                AlertDialog(
                    onDismissRequest = { infoDialogVariant = null },
                    confirmButton = {
                        TextButton(onClick = { infoDialogVariant = null }) {
                            Text(stringResource(MR.strings.action_ok))
                        }
                    },
                    title = { Text(model.displayName) },
                    text = { Text(stringResource(model.descriptionRes)) },
                )
            }
        }
    }
}
