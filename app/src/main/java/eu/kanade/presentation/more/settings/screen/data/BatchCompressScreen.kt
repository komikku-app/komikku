// KMK -->
package eu.kanade.presentation.more.settings.screen.data

import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.OptimizableChapter
import eu.kanade.tachiyomi.data.download.DownloadOptimizerJob
import eu.kanade.tachiyomi.data.download.DownloadOptimizerState
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

class BatchCompressScreen : Screen() {

    private class BatchCompressScreenModel : cafe.adriel.voyager.core.model.ScreenModel {
        override fun onDispose() {
            super.onDispose()
            DownloadOptimizerState.clearCache()
        }
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val screenModel = rememberScreenModel { BatchCompressScreenModel() }

        val eligibleSeries by DownloadOptimizerState.eligibleSeries.collectAsStateWithLifecycle()
        val selectedChapterUris by DownloadOptimizerState.selectedChapterUris.collectAsStateWithLifecycle()

        var format by remember {
            mutableStateOf(DownloadOptimizerState.format.value.ifEmpty { downloadPreferences.downloadCompressionFormat().get() })
        }
        var quality by remember {
            mutableStateOf(
                if (DownloadOptimizerState.quality.value > 0) {
                    DownloadOptimizerState.quality.value
                } else {
                    downloadPreferences.downloadCompressionQuality().get()
                },
            )
        }
        var effort by remember {
            mutableStateOf(
                if (DownloadOptimizerState.effort.value > 0) {
                    DownloadOptimizerState.effort.value
                } else {
                    downloadPreferences.downloadEncoderEffort().get()
                },
            )
        }
        var autoGrayscale by remember {
            mutableStateOf(
                DownloadOptimizerState.autoGrayscale.value ?: downloadPreferences.autoGrayscaleBWManga().get(),
            )
        }
        var stripMetadata by remember {
            mutableStateOf(
                DownloadOptimizerState.stripMetadata.value ?: downloadPreferences.stripImageMetadata().get(),
            )
        }
        var onlyWhileCharging by remember {
            mutableStateOf(DownloadOptimizerState.onlyWhileCharging.value)
        }
        val storageManager = remember { Injekt.get<StorageManager>() }
        var isLoading by remember { mutableStateOf(DownloadOptimizerState.eligibleSeries.value == null) }

        LaunchedEffect(eligibleSeries) {
            val series = DownloadOptimizerState.eligibleSeries.value
            if (series != null) {
                // Prune any chapters that no longer exist on disk
                val pruned = series.mapNotNull { s ->
                    val existing = s.chapters.filter { ch ->
                        val u = UniFile.fromUri(context, Uri.parse(ch.uriString))
                        u != null && u.exists()
                    }
                    if (existing.isNotEmpty()) s.copy(chapters = existing) else null
                }
                if (pruned.size != series.size || pruned.any { it.chapters.size != series.find { s -> s.id == it.id }?.chapters?.size }) {
                    DownloadOptimizerState.eligibleSeries.value = pruned
                }
            } else {
                isLoading = true
                val downloadsDir = storageManager.getDownloadsDirectory()
                val freshSeries = if (downloadsDir != null) {
                    DownloadOptimizerJob.getEligibleChaptersBySeries(context, downloadsDir)
                } else {
                    emptyList()
                }
                DownloadOptimizerState.eligibleSeries.value = freshSeries
                DownloadOptimizerState.selectedChapterUris.value = freshSeries.flatMap { it.chapters }.map { it.uriString }.toSet()
                isLoading = false
            }
        }

        var isOptimizerRunning by remember { mutableStateOf(DownloadOptimizerJob.isRunning(context)) }
        LaunchedEffect(Unit) {
            DownloadOptimizerJob.isRunningFlow(context).collectLatest {
                isOptimizerRunning = it
            }
        }

        val currentSeries = eligibleSeries.orEmpty()
        val allChapters = currentSeries.flatMap { it.chapters }
        val currentSelectedUris = selectedChapterUris ?: allChapters.map { it.uriString }.toSet()
        val selectedChapters = allChapters.filter { currentSelectedUris.contains(it.uriString) }
        val selectedCount = selectedChapters.size
        val selectedSize = selectedChapters.sumOf { it.sizeBytes }
        val estSavings = (selectedSize * 0.85).toLong()

        val selectedSeriesCount = currentSeries.count { series ->
            series.chapters.any { currentSelectedUris.contains(it.uriString) }
        }

        val isAvifSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.batch_compress_title),
                    navigateUp = {
                        navigator.pop()
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                if (!isLoading && currentSeries.isNotEmpty()) {
                    Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(KMR.strings.optimize_selected_count, selectedCount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(
                                        KMR.strings.optimize_selected_saving_est,
                                        Formatter.formatFileSize(context, estSavings),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Button(
                                onClick = {
                                    DownloadOptimizerState.format.value = format
                                    DownloadOptimizerState.quality.value = quality
                                    DownloadOptimizerState.effort.value = effort
                                    DownloadOptimizerState.autoGrayscale.value = autoGrayscale
                                    DownloadOptimizerState.stripMetadata.value = stripMetadata
                                    DownloadOptimizerState.onlyWhileCharging.value = onlyWhileCharging

                                    DownloadOptimizerJob.start(
                                        context = context,
                                        onlyWhileCharging = onlyWhileCharging,
                                        format = format,
                                        quality = quality,
                                        effort = effort,
                                        autoGrayscale = autoGrayscale,
                                        stripMetadata = stripMetadata,
                                        selectedChapterUris = currentSelectedUris,
                                    )
                                    context.toast(KMR.strings.batch_compress_job_started)
                                    navigator.pop()
                                },
                                enabled = selectedCount > 0 && !isOptimizerRunning,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(KMR.strings.batch_compress_start))
                            }
                        }
                    }
                }
            },
        ) { contentPadding ->
            if (isLoading) {
                LoadingScreen(modifier = Modifier.padding(contentPadding))
            } else if (currentSeries.isEmpty()) {
                EmptyScreen(
                    message = stringResource(KMR.strings.optimize_nothing_to_optimize),
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Warning Banner
                    item {
                        WarningBanner(
                            textRes = KMR.strings.optimize_dialog_irreversible_warning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }

                    // 1. Target Chapters Card
                    item {
                        TargetChaptersCard(
                            format = format, quality = quality, effort = effort,
                            autoGrayscale = autoGrayscale, stripMetadata = stripMetadata,
                            onlyWhileCharging = onlyWhileCharging,
                            selectedCount = selectedCount, selectedSize = selectedSize,
                            estSavings = estSavings, selectedSeriesCount = selectedSeriesCount,
                            navigator = navigator,
                        )
                    }

                    // 2. Compression Settings Card
                    item {
                        CompressionSettingsCard(
                            format = format,
                            onFormatChange = { format = it },
                            quality = quality,
                            onQualityChange = { quality = it },
                            effort = effort,
                            onEffortChange = { effort = it },
                            autoGrayscale = autoGrayscale,
                            onAutoGrayscaleChange = { autoGrayscale = it },
                            stripMetadata = stripMetadata,
                            onStripMetadataChange = { stripMetadata = it },
                            onlyWhileCharging = onlyWhileCharging,
                            onOnlyWhileChargingChange = { onlyWhileCharging = it },
                            isAvifSupported = isAvifSupported,
                            downloadPreferences = downloadPreferences,
                            allChapters = allChapters,
                        )
                    }

                    // 3. Advanced Options Card
                    item {
                        AdvancedOptionsCard(
                            autoGrayscale = autoGrayscale,
                            onAutoGrayscaleChange = { autoGrayscale = it },
                            stripMetadata = stripMetadata,
                            onStripMetadataChange = { stripMetadata = it },
                            onlyWhileCharging = onlyWhileCharging,
                            onOnlyWhileChargingChange = { onlyWhileCharging = it },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun TargetChaptersCard(
        format: String,
        quality: Int,
        effort: Int,
        autoGrayscale: Boolean,
        stripMetadata: Boolean,
        onlyWhileCharging: Boolean,
        selectedCount: Int,
        selectedSize: Long,
        estSavings: Long,
        selectedSeriesCount: Int,
        navigator: cafe.adriel.voyager.navigator.Navigator,
    ) {
        val context = LocalContext.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(KMR.strings.optimize_target_chapters).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        onClick = { DownloadOptimizerState.clearCache() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(KMR.strings.batch_compress_rescan_storage))
                    }
                }

                // Single Clickable Tile for Chapters Selection
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            DownloadOptimizerState.format.value = format
                            DownloadOptimizerState.quality.value = quality
                            DownloadOptimizerState.effort.value = effort
                            DownloadOptimizerState.autoGrayscale.value = autoGrayscale
                            DownloadOptimizerState.stripMetadata.value = stripMetadata
                            DownloadOptimizerState.onlyWhileCharging.value = onlyWhileCharging
                            navigator.push(StorageOptimizationScreen())
                        },
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = selectedCount.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = stringResource(KMR.strings.optimize_selected_count, selectedCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${Formatter.formatFileSize(context, selectedSize)} • " +
                                        stringResource(KMR.strings.optimize_selected_saving_est, Formatter.formatFileSize(context, estSavings)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                text = stringResource(KMR.strings.optimize_target_chapters_change),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Footer info with "X series" and estimated reduction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(KMR.strings.batch_compress_series_count, selectedSeriesCount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(KMR.strings.batch_compress_est_reduction, "85%"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    @Composable
    private fun CompressionSettingsCard(
        format: String,
        onFormatChange: (String) -> Unit,
        quality: Int,
        onQualityChange: (Int) -> Unit,
        effort: Int,
        onEffortChange: (Int) -> Unit,
        autoGrayscale: Boolean,
        onAutoGrayscaleChange: (Boolean) -> Unit,
        stripMetadata: Boolean,
        onStripMetadataChange: (Boolean) -> Unit,
        onlyWhileCharging: Boolean,
        onOnlyWhileChargingChange: (Boolean) -> Unit,
        isAvifSupported: Boolean,
        downloadPreferences: DownloadPreferences,
        allChapters: List<OptimizableChapter>,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(KMR.strings.pref_download_compression_category).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Format selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(KMR.strings.pref_download_compression_format),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = format == "WEBP",
                            onClick = { onFormatChange("WEBP") },
                            label = { Text("WebP") },
                        )
                        FilterChip(
                            selected = format == "AVIF",
                            onClick = { if (isAvifSupported) onFormatChange("AVIF") },
                            label = {
                                Text(
                                    if (isAvifSupported) "AVIF" else stringResource(KMR.strings.pref_download_compression_format_avif_disabled),
                                )
                            },
                            enabled = isAvifSupported,
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.secondaryItemAlpha())

                // Quality slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(KMR.strings.pref_download_compression_quality),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "$quality%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { onQualityChange(it.roundToInt()) },
                        valueRange = 50f..100f,
                        steps = 50,
                    )
                    Text(
                        text = getQualityDescription(quality),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.secondaryItemAlpha())

                // Encoder effort
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(KMR.strings.pref_download_encoder_effort),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val effortOptions = remember {
                        listOf(
                            1 to KMR.strings.batch_compress_effort_fast,
                            4 to KMR.strings.batch_compress_effort_balanced,
                            6 to KMR.strings.batch_compress_effort_maximum,
                        )
                    }
                    MultiChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        effortOptions.forEachIndexed { index, (value, labelRes) ->
                            SegmentedButton(
                                checked = effort == value,
                                onCheckedChange = { onEffortChange(value) },
                                shape = SegmentedButtonDefaults.itemShape(index, effortOptions.size),
                            ) {
                                Text(stringResource(labelRes))
                            }
                        }
                    }
                    Text(
                        text = getEffortDescription(effort),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.secondaryItemAlpha())

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = {
                            onFormatChange(downloadPreferences.downloadCompressionFormat().get())
                            onQualityChange(downloadPreferences.downloadCompressionQuality().get())
                            onEffortChange(downloadPreferences.downloadEncoderEffort().get())
                            onAutoGrayscaleChange(downloadPreferences.autoGrayscaleBWManga().get())
                            onStripMetadataChange(downloadPreferences.stripImageMetadata().get())
                            onOnlyWhileChargingChange(false)
                            DownloadOptimizerState.selectedChapterUris.value = allChapters.map { it.uriString }.toSet()
                        },
                    ) {
                        Text(text = stringResource(KMR.strings.batch_compress_restore_defaults))
                    }
                }
            }
        }
    }

    @Composable
    private fun AdvancedOptionsCard(
        autoGrayscale: Boolean,
        onAutoGrayscaleChange: (Boolean) -> Unit,
        stripMetadata: Boolean,
        onStripMetadataChange: (Boolean) -> Unit,
        onlyWhileCharging: Boolean,
        onOnlyWhileChargingChange: (Boolean) -> Unit,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.pref_category_advanced).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Grayscale switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAutoGrayscaleChange(!autoGrayscale) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(KMR.strings.pref_auto_grayscale_bw_manga),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(KMR.strings.pref_auto_grayscale_bw_manga_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoGrayscale,
                        onCheckedChange = { onAutoGrayscaleChange(it) },
                    )
                }

                HorizontalDivider(modifier = Modifier.secondaryItemAlpha())

                // Metadata switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStripMetadataChange(!stripMetadata) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(KMR.strings.pref_strip_image_metadata),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(KMR.strings.pref_strip_image_metadata_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = stripMetadata,
                        onCheckedChange = { onStripMetadataChange(it) },
                    )
                }

                HorizontalDivider(modifier = Modifier.secondaryItemAlpha())

                // Only charging switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOnlyWhileChargingChange(!onlyWhileCharging) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(KMR.strings.optimize_dialog_only_charging),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = onlyWhileCharging,
                        onCheckedChange = { onOnlyWhileChargingChange(it) },
                    )
                }
            }
        }
    }

    @Composable
    private fun getQualityDescription(quality: Int): String {
        return when {
            quality >= 100 -> stringResource(KMR.strings.pref_download_compression_quality_desc_100)
            quality >= 90 -> stringResource(KMR.strings.pref_download_compression_quality_desc_90)
            quality >= 80 -> stringResource(KMR.strings.pref_download_compression_quality_desc_80)
            quality >= 65 -> stringResource(KMR.strings.pref_download_compression_quality_desc_65)
            else -> stringResource(KMR.strings.pref_download_compression_quality_desc_50)
        }
    }

    @Composable
    private fun getEffortDescription(effort: Int): String {
        return when {
            effort <= 2 -> stringResource(KMR.strings.batch_compress_effort_fast_desc)
            effort >= 6 -> stringResource(KMR.strings.batch_compress_effort_maximum_desc)
            else -> stringResource(KMR.strings.batch_compress_effort_balanced_desc)
        }
    }
}
// KMK <--
