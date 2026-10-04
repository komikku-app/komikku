// KMK -->
package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.DownloadOptimizerJob
import eu.kanade.tachiyomi.data.download.DownloadOptimizerState
import eu.kanade.tachiyomi.data.download.OptimizableChapter
import eu.kanade.tachiyomi.data.download.OptimizableSeries
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
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

class StorageOptimizationScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { StorageOptimizationScreenModel(context) }
        val state by model.state.collectAsState()

        when (val s = state) {
            is StorageOptimizationScreenModel.State.Loading -> LoadingScreen()
            is StorageOptimizationScreenModel.State.Ready -> {
                Scaffold(
                    topBar = { scrollBehavior ->
                        SearchToolbar(
                            searchQuery = s.searchQuery,
                            onChangeSearchQuery = model::setSearchQuery,
                            titleContent = { AppBarTitle(stringResource(KMR.strings.optimize_select_chapters_title)) },
                            navigateUp = navigator::pop,
                            actions = {
                                val allVisibleUris = s.filteredSeries.flatMap { it.chapters }.map { it.uriString }
                                val areAllVisibleSelected = allVisibleUris.isNotEmpty() &&
                                    allVisibleUris.all { s.selectedChapterUris.contains(it) }

                                TextButton(onClick = model::toggleSelectAll) {
                                    Text(
                                        text = stringResource(
                                            if (areAllVisibleSelected) KMR.strings.optimize_deselect_all else KMR.strings.optimize_select_all,
                                        ),
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                    bottomBar = {
                        val selectedCount = s.selectedChapterUris.size
                        val selectedSize = s.allSeries.flatMap { it.chapters }
                            .filter { s.selectedChapterUris.contains(it.uriString) }
                            .sumOf { it.sizeBytes }
                        val estSavings = (selectedSize * 0.85).toLong()

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
                                    onClick = { model.applySelection(navigator) },
                                    enabled = selectedCount > 0,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(text = stringResource(KMR.strings.optimize_action_apply))
                                }
                            }
                        }
                    },
                ) { contentPadding ->
                    if (s.allSeries.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(KMR.strings.optimize_nothing_to_optimize),
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Filter and sort chips header
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val totalChapters = s.allSeries.sumOf { it.chapters.size }
                                    val readChapters = s.allSeries.sumOf { it.chapters.count { c -> c.isRead } }
                                    val unreadChapters = totalChapters - readChapters

                                    FilterChip(
                                        selected = s.filter == StorageOptimizationScreenModel.FilterMode.ALL,
                                        onClick = { model.setFilter(StorageOptimizationScreenModel.FilterMode.ALL) },
                                        label = { Text(stringResource(KMR.strings.optimize_filter_all, totalChapters)) },
                                    )
                                    FilterChip(
                                        selected = s.filter == StorageOptimizationScreenModel.FilterMode.READ,
                                        onClick = { model.setFilter(StorageOptimizationScreenModel.FilterMode.READ) },
                                        label = { Text(stringResource(KMR.strings.optimize_filter_read, readChapters)) },
                                    )
                                    FilterChip(
                                        selected = s.filter == StorageOptimizationScreenModel.FilterMode.UNREAD,
                                        onClick = { model.setFilter(StorageOptimizationScreenModel.FilterMode.UNREAD) },
                                        label = { Text(stringResource(KMR.strings.optimize_filter_unread, unreadChapters)) },
                                    )

                                    AssistChip(
                                        onClick = model::toggleSort,
                                        label = {
                                            Text(
                                                if (s.sortBySize) {
                                                    "${stringResource(KMR.strings.optimize_sort_size)} ↓"
                                                } else {
                                                    "${stringResource(KMR.strings.optimize_sort_title)} ↑"
                                                },
                                            )
                                        },
                                    )
                                }
                            }

                            // Series items
                            items(
                                items = s.filteredSeries,
                                key = { it.id },
                            ) { series ->
                                SeriesItem(
                                    series = series,
                                    selectedUris = s.selectedChapterUris,
                                    isExpanded = s.expandedSeriesIds.contains(series.id),
                                    actions = SeriesItemActions(
                                        onToggleExpand = { model.toggleExpand(series.id) },
                                        onToggleSeries = { shouldSelect -> model.toggleSeries(series, shouldSelect) },
                                        onToggleChapter = model::toggleChapter,
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    data class SeriesItemActions(
        val onToggleExpand: () -> Unit,
        val onToggleSeries: (Boolean) -> Unit,
        val onToggleChapter: (String) -> Unit,
    )

    @Composable
    private fun SeriesItem(
        series: OptimizableSeries,
        selectedUris: Set<String>,
        isExpanded: Boolean,
        actions: SeriesItemActions,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        val seriesChapters = series.chapters
        val selectedCount = seriesChapters.count { selectedUris.contains(it.uriString) }
        val triState = when {
            selectedCount == 0 -> ToggleableState.Off
            selectedCount == seriesChapters.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
        val totalBytes = seriesChapters.sumOf { it.sizeBytes }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Series header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = actions.onToggleExpand)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Manga cover
                    if (series.manga != null) {
                        MangaCover.Square(
                            data = series.manga,
                            modifier = Modifier
                                .size(width = 44.dp, height = 58.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 58.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = series.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "$selectedCount/${seriesChapters.size} chapters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = Formatter.formatFileSize(context, totalBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Series checkbox
                    TriStateCheckbox(
                        state = triState,
                        onClick = {
                            val nextSelect = triState != ToggleableState.On
                            actions.onToggleSeries(nextSelect)
                        },
                    )

                    // Expand / Collapse chevron
                    IconButton(onClick = actions.onToggleExpand) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Expandable Chapter rows
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(bottom = 8.dp),
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )

                        seriesChapters.forEach { chapter ->
                            ChapterRow(
                                chapter = chapter,
                                isSelected = selectedUris.contains(chapter.uriString),
                                onToggle = { actions.onToggleChapter(chapter.uriString) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ChapterRow(
        chapter: OptimizableChapter,
        isSelected: Boolean,
        onToggle: () -> Unit,
    ) {
        val context = LocalContext.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = chapter.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Read / New Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (chapter.isRead) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 2.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (chapter.isRead) KMR.strings.optimize_chapter_badge_read else KMR.strings.optimize_chapter_badge_new,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chapter.isRead) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Format tag
                Text(
                    text = if (chapter.isCbz) "CBZ" else "DIR",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = Formatter.formatFileSize(context, chapter.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                )
            }
        }
    }
}

private class StorageOptimizationScreenModel(
    private val context: Context,
) : StateScreenModel<StorageOptimizationScreenModel.State>(State.Loading) {

    enum class FilterMode {
        ALL,
        READ,
        UNREAD,
    }

    sealed interface State {
        data object Loading : State
        data class Ready(
            val allSeries: List<OptimizableSeries>,
            val filteredSeries: List<OptimizableSeries>,
            val selectedChapterUris: Set<String>,
            val expandedSeriesIds: Set<String> = emptySet(),
            val searchQuery: String? = null,
            val filter: FilterMode = FilterMode.ALL,
            val sortBySize: Boolean = true,
        ) : State
    }

    init {
        screenModelScope.launchIO {
            val cachedSeries = DownloadOptimizerState.eligibleSeries.value
            val initialSeries = if (!cachedSeries.isNullOrEmpty()) {
                val pruned = cachedSeries.mapNotNull { s ->
                    val existing = s.chapters.filter { ch ->
                        val u = UniFile.fromUri(context, Uri.parse(ch.uriString))
                        u != null && u.exists()
                    }
                    if (existing.isNotEmpty()) s.copy(chapters = existing) else null
                }
                if (pruned != cachedSeries) {
                    DownloadOptimizerState.eligibleSeries.value = pruned
                }
                pruned
            } else {
                val storageManager = Injekt.get<StorageManager>()
                val downloadsDir = storageManager.getDownloadsDirectory()
                val series = if (downloadsDir != null) {
                    DownloadOptimizerJob.getEligibleChaptersBySeries(context, downloadsDir)
                } else {
                    emptyList()
                }
                DownloadOptimizerState.eligibleSeries.value = series
                series
            }

            val cachedSelected = DownloadOptimizerState.selectedChapterUris.value
            val initialSelected = cachedSelected ?: initialSeries.flatMap { it.chapters }.map { it.uriString }.toSet()

            // Expand first series by default for discovery
            val initialExpanded = initialSeries.firstOrNull()?.id?.let { setOf(it) } ?: emptySet()

            mutableState.value = State.Ready(
                allSeries = initialSeries,
                filteredSeries = sortAndFilter(initialSeries, null, FilterMode.ALL, true),
                selectedChapterUris = initialSelected,
                expandedSeriesIds = initialExpanded,
            )
        }
    }

    fun setSearchQuery(query: String?) {
        val s = state.value as? State.Ready ?: return
        val newQuery = query?.takeIf { it.isNotBlank() }
        mutableState.update {
            s.copy(
                searchQuery = newQuery,
                filteredSeries = sortAndFilter(s.allSeries, newQuery, s.filter, s.sortBySize),
            )
        }
    }

    fun setFilter(filter: FilterMode) {
        val s = state.value as? State.Ready ?: return
        mutableState.update {
            s.copy(
                filter = filter,
                filteredSeries = sortAndFilter(s.allSeries, s.searchQuery, filter, s.sortBySize),
            )
        }
    }

    fun toggleSort() {
        val s = state.value as? State.Ready ?: return
        val newSortBySize = !s.sortBySize
        mutableState.update {
            s.copy(
                sortBySize = newSortBySize,
                filteredSeries = sortAndFilter(s.allSeries, s.searchQuery, s.filter, newSortBySize),
            )
        }
    }

    fun toggleExpand(seriesId: String) {
        val s = state.value as? State.Ready ?: return
        val newExpanded = if (s.expandedSeriesIds.contains(seriesId)) {
            s.expandedSeriesIds - seriesId
        } else {
            s.expandedSeriesIds + seriesId
        }
        mutableState.update { s.copy(expandedSeriesIds = newExpanded) }
    }

    fun toggleChapter(uriString: String) {
        val s = state.value as? State.Ready ?: return
        val newSelected = if (s.selectedChapterUris.contains(uriString)) {
            s.selectedChapterUris - uriString
        } else {
            s.selectedChapterUris + uriString
        }
        mutableState.update { s.copy(selectedChapterUris = newSelected) }
    }

    fun toggleSeries(series: OptimizableSeries, shouldSelect: Boolean) {
        val s = state.value as? State.Ready ?: return
        val chapterUris = series.chapters.map { it.uriString }
        val newSelected = if (shouldSelect) {
            s.selectedChapterUris + chapterUris
        } else {
            s.selectedChapterUris - chapterUris.toSet()
        }
        mutableState.update { s.copy(selectedChapterUris = newSelected) }
    }

    fun toggleSelectAll() {
        val s = state.value as? State.Ready ?: return
        val allVisibleUris = s.filteredSeries.flatMap { it.chapters }.map { it.uriString }
        val areAllVisibleSelected = allVisibleUris.isNotEmpty() &&
            allVisibleUris.all { s.selectedChapterUris.contains(it) }

        val newSelected = if (areAllVisibleSelected) {
            s.selectedChapterUris - allVisibleUris.toSet()
        } else {
            s.selectedChapterUris + allVisibleUris
        }
        mutableState.update { s.copy(selectedChapterUris = newSelected) }
    }

    fun applySelection(navigator: Navigator) {
        val s = state.value as? State.Ready ?: return
        DownloadOptimizerState.selectedChapterUris.value = s.selectedChapterUris
        navigator.pop()
    }

    private fun sortAndFilter(
        seriesList: List<OptimizableSeries>,
        query: String?,
        filter: FilterMode,
        sortBySize: Boolean,
    ): List<OptimizableSeries> {
        val filtered = seriesList.mapNotNull { series ->
            val chapters = series.chapters.filter { chapter ->
                when (filter) {
                    FilterMode.ALL -> true
                    FilterMode.READ -> chapter.isRead
                    FilterMode.UNREAD -> !chapter.isRead
                }
            }

            if (query != null) {
                val matchesTitle = series.title.contains(query, ignoreCase = true)
                val matchesChapter = chapters.any { it.name.contains(query, ignoreCase = true) }
                if (!matchesTitle && !matchesChapter) return@mapNotNull null
            }

            if (chapters.isEmpty()) return@mapNotNull null
            series.copy(chapters = chapters)
        }

        return if (sortBySize) {
            filtered.sortedByDescending { s -> s.chapters.sumOf { it.sizeBytes } }
        } else {
            filtered.sortedBy { it.title.lowercase() }
        }
    }
}
// KMK <--
