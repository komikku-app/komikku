package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import kotlinx.collections.immutable.ImmutableSet
import tachiyomi.presentation.core.components.material.padding

private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

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

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
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

    // SY -->
    BoxIgnoreLayoutDirection(
        Modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(
            visible = visible && navBarType == NavBarType.VerticalLeft,
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeIn(animationSpec = readerBarsFadeAnimationSpec),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeOut(animationSpec = readerBarsFadeAnimationSpec),
            modifier = Modifier
                .padding(top = 164.dp, bottom = 70.dp)
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
                // SY -->
                isVerticalSlider = true,
                currentPageText = currentPageText,
                // SY <--
            )
        }

        AnimatedVisibility(
            visible = visible && navBarType == NavBarType.VerticalRight,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeIn(animationSpec = readerBarsFadeAnimationSpec),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeOut(animationSpec = readerBarsFadeAnimationSpec),
            modifier = Modifier
                .padding(top = 164.dp, bottom = 70.dp)
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
                // SY -->
                isVerticalSlider = true,
                currentPageText = currentPageText,
                // SY <--
            )
        }
        // SY <--
        Column(modifier = Modifier.fillMaxHeight()) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                    fadeIn(animationSpec = readerBarsFadeAnimationSpec),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                    fadeOut(animationSpec = readerBarsFadeAnimationSpec),
            ) {
                // SY -->
                Column {
                    // SY <--
                    ReaderTopBar(
                        modifier = Modifier
                            .background(backgroundColor)
                            .clickable(onClick = onClickTopAppBar),
                        mangaTitle = mangaTitle,
                        chapterTitle = chapterTitle,
                        navigateUp = navigateUp,
                        bookmarked = bookmarked,
                        onToggleBookmarked = onToggleBookmarked,
                        // SY -->
                        onOpenInWebView = null, // onOpenInWebView,
                        onOpenInBrowser = null, // onOpenInBrowser,
                        onShare = null, // onShare,
                        // SY <--
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
                }
                // SY <--
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                    fadeIn(animationSpec = readerBarsFadeAnimationSpec),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                    fadeOut(animationSpec = readerBarsFadeAnimationSpec),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    // SY -->
                    if (navBarType == NavBarType.Bottom) {
                        // SY <--
                        ChapterNavigator(
                            isRtl = isRtl,
                            onNextChapter = onNextChapter,
                            enabledNext = enabledNext,
                            onPreviousChapter = onPreviousChapter,
                            enabledPrevious = enabledPrevious,
                            currentPage = currentPage,
                            totalPages = totalPages,
                            onPageIndexChange = onPageIndexChange,
                            // SY -->
                            isVerticalSlider = false,
                            currentPageText = currentPageText,
                            // SY <--
                        )
                    }
                    ReaderBottomBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .padding(horizontal = MaterialTheme.padding.small)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        readingMode = readingMode,
                        onClickReadingMode = onClickReadingMode,
                        orientation = orientation,
                        onClickOrientation = onClickOrientation,
                        cropEnabled = cropEnabled,
                        onClickCropBorder = onClickCropBorder,
                        onClickSettings = onClickSettings,
                        // SY -->
                        enabledButtons = enabledButtons,
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
