package android.graphics

/**
 * android-compat: `android.graphics.ColorMatrix`, a 4 x 5 matrix applied to unpremultiplied R, G, B, A (0..255), the
 * last column added. Only what the catalogue uses (`tenkaiscan` inverts colours with one): the array, identity,
 * [set] and [reset].
 */
class ColorMatrix() {

    private val array = FloatArray(20)

    init {
        reset()
    }

    constructor(src: FloatArray) : this() {
        set(src)
    }

    constructor(src: ColorMatrix) : this(src.array)

    fun getArray(): FloatArray = array

    fun reset() {
        array.fill(0f)
        for (i in 0 until 4) array[i * 6] = 1f
    }

    fun set(src: FloatArray) {
        src.copyInto(array, 0, 0, 20)
    }

    fun set(src: ColorMatrix) = set(src.array)

    override fun equals(other: Any?): Boolean = other is ColorMatrix && array.contentEquals(other.array)

    override fun hashCode(): Int = array.contentHashCode()
}
