package mihon.feature.upcoming

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.isTabletUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.launch
import mihon.feature.upcoming.components.UpcomingItem
import mihon.feature.upcoming.components.calendar.Calendar
import tachiyomi.core.common.Constants
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun UpcomingScreenContent(
    state: UpcomingScreenModel.State,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickUpcoming: (manga: Manga) -> Unit,
    // KMK -->
    showUpdatingMangas: () -> Unit,
    hideUpdatingMangas: () -> Unit,
    isPredictReleaseDate: Boolean,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // KMK -->
    val headerIndexes = if (state.isShowingUpdatingMangas) state.updatingHeaderIndexes else state.headerIndexes
    val items = if (state.isShowingUpdatingMangas) state.updatingItems else state.items
    val events = if (state.isShowingUpdatingMangas) state.updatingEvents else state.events
    val isLoading = if (state.isShowingUpdatingMangas) state.isLoadingUpdating else state.isLoadingUpcoming
    // KMK <--

    val onClickDay: (LocalDate, Int) -> Unit = { date, offset ->
        headerIndexes[date]?.let {
            scope.launch {
                listState.animateScrollToItem(it + offset)
            }
        }
    }
    Scaffold(
        topBar = {
            UpcomingToolbar(
                // KMK -->
                state.isShowingUpdatingMangas,
                showUpdatingMangas = showUpdatingMangas,
                hideUpdatingMangas = hideUpdatingMangas,
                isPredictReleaseDate = isPredictReleaseDate,
                // KMK <--
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        // KMK -->
        if (isLoading) {
            LoadingScreen(modifier = Modifier.padding(paddingValues))
            return@Scaffold
        }
        // KMK <--
        if (isTabletUi()) {
            UpcomingScreenLargeImpl(
                listState = listState,
                items = items,
                events = events,
                paddingValues = paddingValues,
                selectedYearMonth = state.selectedYearMonth,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = { onClickDay(it, 0) },
                onClickUpcoming = onClickUpcoming,
                // KMK -->
                state.isShowingUpdatingMangas,
                // KMK <--
            )
        } else {
            UpcomingScreenSmallImpl(
                listState = listState,
                items = items,
                events = events,
                paddingValues = paddingValues,
                selectedYearMonth = state.selectedYearMonth,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = { onClickDay(it, 1) },
                onClickUpcoming = onClickUpcoming,
                // KMK -->
                state.isShowingUpdatingMangas,
                // KMK <--
            )
        }
    }
}

@Composable
private fun UpcomingToolbar(
    // KMK -->
    isShowingUpdatingMangas: Boolean,
    showUpdatingMangas: () -> Unit,
    hideUpdatingMangas: () -> Unit,
    isPredictReleaseDate: Boolean,
    // KMK <--
) {
    val navigator = LocalNavigator.currentOrThrow
    val uriHandler = LocalUriHandler.current

    AppBar(
        title =
        if (isShowingUpdatingMangas) {
            stringResource(KMR.strings.label_to_be_updated)
        } else {
            stringResource(MR.strings.label_upcoming)
        },
        navigateUp = navigator::pop,
        actions = {
            // KMK -->
            if (isPredictReleaseDate) {
                IconButton(
                    onClick = {
                        if (isShowingUpdatingMangas) {
                            hideUpdatingMangas()
                        } else {
                            showUpdatingMangas()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NewReleases,
                        contentDescription = stringResource(MR.strings.pref_library_update_smart_update),
                        tint = if (isShowingUpdatingMangas) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
            // KMK <--
            IconButton(onClick = { uriHandler.openUri(Constants.URL_HELP_UPCOMING) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(MR.strings.upcoming_guide),
                )
            }
        },
    )
}

@Composable
private fun DateHeading(
    date: LocalDate,
    mangaCount: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = relativeDateText(date),
            modifier = Modifier
                .padding(MaterialTheme.padding.small)
                .padding(start = MaterialTheme.padding.small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text("$mangaCount")
        }
    }
}

@Composable
private fun UpcomingScreenSmallImpl(
    listState: LazyListState,
    items: ImmutableList<UpcomingUIModel>,
    events: ImmutableMap<LocalDate, Int>,
    paddingValues: PaddingValues,
    selectedYearMonth: YearMonth,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickDay: (LocalDate) -> Unit,
    onClickUpcoming: (manga: Manga) -> Unit,
    // KMK -->
    isShowingUpdatingMangas: Boolean,
    // KMK <--
) {
    FastScrollLazyColumn(
        contentPadding = paddingValues,
        state = listState,
    ) {
        item(key = "upcoming-calendar") {
            Calendar(
                selectedYearMonth = selectedYearMonth,
                events = events,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = onClickDay,
            )
        }
        items(
            items = items,
            key = { (if (isShowingUpdatingMangas) "updating" else "upcoming") + it.hashCode() },
            contentType = {
                when (it) {
                    is UpcomingUIModel.Header -> "header"
                    is UpcomingUIModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is UpcomingUIModel.Item -> {
                    UpcomingItem(
                        upcoming = item.manga,
                        onClick = { onClickUpcoming(item.manga) },
                    )
                }

                is UpcomingUIModel.Header -> {
                    DateHeading(
                        date = item.date,
                        mangaCount = item.mangaCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingScreenLargeImpl(
    listState: LazyListState,
    items: ImmutableList<UpcomingUIModel>,
    events: ImmutableMap<LocalDate, Int>,
    paddingValues: PaddingValues,
    selectedYearMonth: YearMonth,
    setSelectedYearMonth: (YearMonth) -> Unit,
    onClickDay: (LocalDate) -> Unit,
    onClickUpcoming: (manga: Manga) -> Unit,
    // KMK -->
    isShowingUpdatingMangas: Boolean,
    // KMK <--
) {
    TwoPanelBox(
        modifier = Modifier.padding(paddingValues),
        startContent = {
            Calendar(
                selectedYearMonth = selectedYearMonth,
                events = events,
                setSelectedYearMonth = setSelectedYearMonth,
                onClickDay = onClickDay,
            )
        },
        endContent = {
            FastScrollLazyColumn(state = listState) {
                items(
                    items = items,
                    key = { (if (isShowingUpdatingMangas) "updating" else "upcoming") + it.hashCode() },
                    contentType = {
                        when (it) {
                            is UpcomingUIModel.Header -> "header"
                            is UpcomingUIModel.Item -> "item"
                        }
                    },
                ) { item ->
                    when (item) {
                        is UpcomingUIModel.Item -> {
                            UpcomingItem(
                                upcoming = item.manga,
                                onClick = { onClickUpcoming(item.manga) },
                            )
                        }

                        is UpcomingUIModel.Header -> {
                            DateHeading(
                                date = item.date,
                                mangaCount = item.mangaCount,
                            )
                        }
                    }
                }
            }
        },
    )
}
