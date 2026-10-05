@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package tachiyomi.presentation.core.components.m3e

import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * The determinate circular indicator of Material 3 Expressive.
 *
 * Use it where the progress value is information the user wants, such as a page that loads or a
 * download that advances. For a wait with no known duration use
 * [WavyIndeterminateCircularProgressIndicator].
 */
@Composable
fun WavyCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
) = CircularWavyProgressIndicator(
    progress = progress,
    modifier = modifier,
    color = color,
    trackColor = trackColor,
    stroke = stroke,
    trackStroke = trackStroke,
    gapSize = gapSize,
)

/** The indeterminate circular indicator, for a wait with no known duration. */
@Composable
fun WavyIndeterminateCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
) = CircularWavyProgressIndicator(
    modifier = modifier,
    color = color,
    trackColor = trackColor,
    stroke = stroke,
    trackStroke = trackStroke,
    gapSize = gapSize,
)

/** The determinate linear indicator of Material 3 Expressive. */
@Composable
fun WavyLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
) = LinearWavyProgressIndicator(
    progress = progress,
    modifier = modifier,
    color = color,
    trackColor = trackColor,
    stroke = stroke,
    trackStroke = trackStroke,
    gapSize = gapSize,
)

/** The indeterminate linear indicator, for a wait with no known duration. */
@Composable
fun WavyIndeterminateLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
) = LinearWavyProgressIndicator(
    modifier = modifier,
    color = color,
    trackColor = trackColor,
    stroke = stroke,
    trackStroke = trackStroke,
    gapSize = gapSize,
)
