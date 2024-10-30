package eu.kanade.translation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

// DP
fun Density.toSp(dp: Dp): TextUnit = dp.toSp()
fun Density.toPx(dp: Dp): Float = dp.toPx()
fun Density.roundToPx(dp: Dp): Int = dp.roundToPx()

// TEXT UNIT
fun Density.toDp(sp: TextUnit): Dp = sp.toDp()
fun Density.toPx(sp: TextUnit): Float = sp.toPx()
fun Density.roundToPx(sp: TextUnit): Int = sp.roundToPx()

// FLOAT
fun Density.toDp(px: Float): Dp = px.toDp()
fun Density.toSp(px: Float): TextUnit = px.toSp()

// INT
fun Density.toDp(px: Int): Dp = px.toDp()
fun Density.toSp(px: Int): TextUnit = px.toSp()

// SIZE
fun Density.toIntSize(dpSize: DpSize): IntSize =
    IntSize(dpSize.width.roundToPx(), dpSize.height.roundToPx())

fun Density.toSize(dpSize: DpSize): Size =
    if (dpSize.isSpecified) {
        Size(dpSize.width.toPx(), dpSize.height.toPx())
    } else {
        Size.Unspecified
    }

fun Density.toDpSize(size: Size): DpSize =
    if (size.isSpecified) {
        DpSize(size.width.toDp(), size.height.toDp())
    } else {
        DpSize.Unspecified
    }

fun Density.toDpSize(intSize: IntSize): DpSize =
    DpSize(intSize.width.toDp(), intSize.height.toDp())

// OFFSET
fun Density.toIntOffset(dpOffset: DpOffset): IntOffset =
    IntOffset(dpOffset.x.roundToPx(), dpOffset.y.roundToPx())

fun Density.toOffset(dpOffset: DpOffset): Offset =
    if (dpOffset.isSpecified) {
        Offset(dpOffset.x.toPx(), dpOffset.y.toPx())
    } else {
        Offset.Unspecified
    }

fun Density.toDpOffset(offset: Offset): DpOffset =
    if (offset.isSpecified) {
        DpOffset(offset.x.toDp(), offset.y.toDp())
    } else {
        DpOffset.Unspecified
    }

fun Density.toDpOffset(intOffset: IntOffset): DpOffset =
    DpOffset(intOffset.x.toDp(), intOffset.y.toDp())
