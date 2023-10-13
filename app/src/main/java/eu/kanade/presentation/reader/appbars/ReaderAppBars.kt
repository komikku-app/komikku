package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer

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
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Box(modifier) {
            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection
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
    viewer: Viewer?,

    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onSliderValueChange: (Int) -> Unit,

    readingMode: ReadingModeType,
    onClickReadingMode: () -> Unit,
    orientationMode: OrientationType,
    onClickOrientationMode: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    // SY -->
    navBarType: NavBarType,
    currentPageText: String,
    enabledButtons: Set<String>,
    isHttpSource: Boolean,
    dualPageSplitEnabled: Boolean,
    doublePages: Boolean,
    onClickChapterList: () -> Unit,
    onClickWebView: () -> Unit,
    onClickShare: () -> Unit,
    onClickPageLayout: () -> Unit,
    onClickShiftPage: () -> Unit,

) {
    val isRtl = viewer is R2LPagerViewer


    // SY -->
    BoxIgnoreLayoutDirection(
        Modifier.fillMaxWidth()
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
            modifier = Modifier.padding(bottom = 48.dp, top = 120.dp)
                .align(Alignment.TopStart)
        ) {
            ChapterNavigator(
                isRtl = isRtl,
                onNextChapter = onNextChapter,
                enabledNext = enabledNext,
                onPreviousChapter = onPreviousChapter,
                enabledPrevious = enabledPrevious,
                currentPage = currentPage,
                totalPages = totalPages,
                onSliderValueChange = onSliderValueChange,
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
            modifier = Modifier.padding(bottom = 48.dp, top = 120.dp)
                .align(Alignment.TopEnd)
        ) {
            ChapterNavigator(
                isRtl = isRtl,
                onNextChapter = onNextChapter,
                enabledNext = enabledNext,
                onPreviousChapter = onPreviousChapter,
                enabledPrevious = enabledPrevious,
                currentPage = currentPage,
                totalPages = totalPages,
                onSliderValueChange = onSliderValueChange,
                isVerticalSlider = true,
                currentPageText = currentPageText,
            )
        }
        // SY <--
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            onSliderValueChange = onSliderValueChange,
                            isVerticalSlider = false,
                            currentPageText = currentPageText,
                        )
                    }

                    BottomReaderBar(
                        // SY -->
                        enabledButtons = enabledButtons,
                        // SY <--
                        readingMode = readingMode,
                        onClickReadingMode = onClickReadingMode,
                        orientationMode = orientationMode,
                        onClickOrientationMode = onClickOrientationMode,
                        cropEnabled = cropEnabled,
                        onClickCropBorder = onClickCropBorder,
                        onClickSettings = onClickSettings,
                        // SY -->
                        isHttpSource = isHttpSource,
                        dualPageSplitEnabled = dualPageSplitEnabled,
                        doublePages = doublePages,
                        onClickChapterList = onClickChapterList,
                        onClickWebView = onClickWebView,
                        onClickShare = onClickShare,
                        onClickPageLayout = onClickPageLayout,
                        onClickShiftPage = onClickShiftPage
                    )
                }
            }
        }
    }
}
