package exh.assets.ehassets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import exh.assets.EhAssets

public val EhAssets.EhLogo: ImageVector
    get() {
        if (_ehLogo != null) {
            return _ehLogo!!
        }
        _ehLogo = Builder(
            name = "EhLogo",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 8.0f,
            viewportHeight = 7.0f,
        ).apply {
            group {
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(0.0f, 0.0f)
                    horizontalLineToRelative(3.0f)
                    verticalLineToRelative(1.0f)
                    horizontalLineToRelative(-3.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(0.0f, 1.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(1.0f)
                    verticalLineToRelative(-2.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(0.0f, 3.0f)
                    horizontalLineToRelative(2.25f)
                    verticalLineToRelative(1.0f)
                    horizontalLineToRelative(-2.25f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(0.0f, 4.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(1.0f)
                    verticalLineToRelative(-2.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(0.0f, 6.0f)
                    horizontalLineToRelative(3.0f)
                    verticalLineToRelative(1.0f)
                    horizontalLineToRelative(-3.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(3.0f, 3.0f)
                    horizontalLineToRelative(1.0f)
                    verticalLineToRelative(1.0f)
                    horizontalLineToRelative(-1.0f)
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(4.75f, 0.0f)
                    horizontalLineToRelative(1.0f)
                    verticalLineToRelative(7.0f)
                    horizontalLineToRelative(-1.0f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(5.75f, 3.0f)
                    horizontalLineToRelative(1.25f)
                    verticalLineToRelative(1.0f)
                    horizontalLineToRelative(-1.25f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF660611)),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(7.0f, 0.0f)
                    horizontalLineToRelative(1.0f)
                    verticalLineToRelative(7.0f)
                    horizontalLineToRelative(-1.0f)
                    close()
                }
            }
        }
            .build()
        return _ehLogo!!
    }

@Suppress("ObjectPropertyName", "ktlint:standard:backing-property-naming")
private var _ehLogo: ImageVector? = null
