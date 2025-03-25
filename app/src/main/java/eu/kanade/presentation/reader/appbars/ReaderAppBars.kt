package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import kotlinx.collections.immutable.ImmutableSet
import tachiyomi.presentation.core.components.material.padding

private val animationSpec = tween<IntOffset>(200)

// SY -->
enum class NavBarType {
    VerticalRight,
    VerticalLeft,
    Bottom,
}

@Composable
fun BoxIgnoreLayoutDirection(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        Box(modifier) {
            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
            ) {
                content()
            }
        }
    }
}
// SY <--

@Composable
fun ReaderAppBars(
    visible: Boolean,
    fullscreen: Boolean,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    // bookmarked: Boolean,
    // onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,

    viewer: Viewer?,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    // SY -->
    isExhToolsVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    isAutoScroll: Boolean,
    isAutoScrollEnabled: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,
    onClickAutoScrollHelp: () -> Unit,
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    onClickBoostPage: () -> Unit,
    onClickBoostPageHelp: () -> Unit,
    navBarType: NavBarType,
    currentPageText: String,
    enabledButtons: ImmutableSet<String>,
    currentReadingMode: ReadingMode,
    dualPageSplitEnabled: Boolean,
    doublePages: Boolean,
    onClickChapterList: () -> Unit,
    onClickPageLayout: () -> Unit,
    onClickShiftPage: () -> Unit,
    // SY <--
) {
    val isRtl = viewer is R2LPagerViewer
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

    val modifierWithInsetsPadding = if (fullscreen) {
        Modifier.systemBarsPadding()
    } else {
        Modifier
    }

    // SY -->
    BoxIgnoreLayoutDirection(
        Modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(
            visible = visible && navBarType == NavBarType.VerticalLeft,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = animationSpec,
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = animationSpec,
            ),
            modifier = modifierWithInsetsPadding
                .padding(bottom = 64.dp, top = 112.dp)
                .align(Alignment.TopStart),
        ) {
            ChapterNavigator(
                isRtl = isRtl,
                onNextChapter = onNextChapter,
                enabledNext = enabledNext,
                onPreviousChapter = onPreviousChapter,
                enabledPrevious = enabledPrevious,
                currentPage = currentPage,
                totalPages = totalPages,
                onPageIndexChange = onPageIndexChange,
                isVerticalSlider = true,
                currentPageText = currentPageText,
            )
        }

        AnimatedVisibility(
            visible = visible && navBarType == NavBarType.VerticalRight,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = animationSpec,
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = animationSpec,
            ),
            modifier = modifierWithInsetsPadding
                .padding(bottom = 64.dp, top = 112.dp)
                .align(Alignment.TopEnd),
        ) {
            ChapterNavigator(
                isRtl = isRtl,
                onNextChapter = onNextChapter,
                enabledNext = enabledNext,
                onPreviousChapter = onPreviousChapter,
                enabledPrevious = enabledPrevious,
                currentPage = currentPage,
                totalPages = totalPages,
                onPageIndexChange = onPageIndexChange,
                isVerticalSlider = true,
                currentPageText = currentPageText,
            )
        }
        // SY <--
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = animationSpec,
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = animationSpec,
                ),
            ) {
                // SY -->
                Column(modifierWithInsetsPadding) {
                    // SY <--
                    AppBar(
                        modifier = /*SY --> */ Modifier /*SY <-- */
                            .clickable(onClick = onClickTopAppBar),
                        backgroundColor = backgroundColor,
                        title = mangaTitle,
                        subtitle = chapterTitle,
                        navigateUp = navigateUp,
                        /* SY -->
                        actions = {
                            AppBarActions(
                                actions = persistentListOf<AppBar.AppBarAction>().builder()
                                    .apply {
                                        add(
                                            AppBar.Action(
                                                title = stringResource(
                                                    if (bookmarked) {
                                                        MR.strings.action_remove_bookmark
                                                    } else {
                                                        MR.strings.action_bookmark
                                                    },
                                                ),
                                                icon = if (bookmarked) {
                                                    Icons.Outlined.Bookmark
                                                } else {
                                                    Icons.Outlined.BookmarkBorder
                                                },
                                                onClick = onToggleBookmarked,
                                            ),
                                        )
                                        onOpenInWebView?.let {
                                            add(
                                                AppBar.OverflowAction(
                                                    title = stringResource(MR.strings.action_open_in_web_view),
                                                    onClick = it,
                                                ),
                                            )
                                        }
                                        onOpenInBrowser?.let {
                                            add(
                                                AppBar.OverflowAction(
                                                    title = stringResource(MR.strings.action_open_in_browser),
                                                    onClick = it,
                                                ),
                                            )
                                        }
                                        onShare?.let {
                                            add(
                                                AppBar.OverflowAction(
                                                    title = stringResource(MR.strings.action_share),
                                                    onClick = it,
                                                ),
                                            )
                                        }
                                    }
                                    .build(),
                            )
                        },
                        SY <-- */
                    )
                    // SY -->
                    ExhUtils(
                        isVisible = isExhToolsVisible,
                        onSetExhUtilsVisibility = onSetExhUtilsVisibility,
                        backgroundColor = backgroundColor,
                        isAutoScroll = isAutoScroll,
                        isAutoScrollEnabled = isAutoScrollEnabled,
                        onToggleAutoscroll = onToggleAutoscroll,
                        autoScrollFrequency = autoScrollFrequency,
                        onSetAutoScrollFrequency = onSetAutoScrollFrequency,
                        onClickAutoScrollHelp = onClickAutoScrollHelp,
                        onClickRetryAll = onClickRetryAll,
                        onClickRetryAllHelp = onClickRetryAllHelp,
                        onClickBoostPage = onClickBoostPage,
                        onClickBoostPageHelp = onClickBoostPageHelp,
                    )
                    // SY <--
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = animationSpec,
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = animationSpec,
                ),
            ) {
                Column(
                    modifier = modifierWithInsetsPadding,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    if (navBarType == NavBarType.Bottom) {
                        ChapterNavigator(
                            isRtl = isRtl,
                            onNextChapter = onNextChapter,
                            enabledNext = enabledNext,
                            onPreviousChapter = onPreviousChapter,
                            enabledPrevious = enabledPrevious,
                            currentPage = currentPage,
                            totalPages = totalPages,
                            onPageIndexChange = onPageIndexChange,
                            isVerticalSlider = false,
                            currentPageText = currentPageText,
                        )
                    }
                    BottomReaderBar(
                        // SY -->
                        enabledButtons = enabledButtons,
                        // SY <--
                        backgroundColor = backgroundColor,
                        readingMode = readingMode,
                        onClickReadingMode = onClickReadingMode,
                        orientation = orientation,
                        onClickOrientation = onClickOrientation,
                        cropEnabled = cropEnabled,
                        onClickCropBorder = onClickCropBorder,
                        onClickSettings = onClickSettings,
                        // SY -->
                        currentReadingMode = currentReadingMode,
                        dualPageSplitEnabled = dualPageSplitEnabled,
                        doublePages = doublePages,
                        onClickChapterList = onClickChapterList,
                        onClickWebView = onOpenInWebView,
                        onClickBrowser = onOpenInBrowser,
                        onClickShare = onShare,
                        onClickPageLayout = onClickPageLayout,
                        onClickShiftPage = onClickShiftPage,
                        // SY <--
                    )
                }
            }
        }
    }
}
