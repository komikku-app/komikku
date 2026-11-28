package android.graphics

import org.jetbrains.skia.ColorFilter as SkColorFilter
import org.jetbrains.skia.ColorMatrix as SkColorMatrix

/**
 * android-compat: `android.graphics.ColorMatrixColorFilter` with Skia's matrix filter. Android's last column is in
 * 0..255, Skia's in 0..1, so it is divided by 255; the matrix is copied, as on Android.
 */
class ColorMatrixColorFilter(matrix: FloatArray) : ColorFilter() {

    constructor(matrix: ColorMatrix) : this(matrix.getArray())

    private val skia: SkColorFilter = SkColorFilter.makeMatrix(
        SkColorMatrix(FloatArray(20) { i -> if (i % 5 == 4) matrix[i] / 255f else matrix[i] }),
    )

    override fun toSkia(): SkColorFilter = skia
}
