package android.graphics

/** android-compat: `android.graphics.Rect`, an integer rectangle (right and bottom exclusive). */
class Rect() {

    @JvmField
    var left = 0

    @JvmField
    var top = 0

    @JvmField
    var right = 0

    @JvmField
    var bottom = 0

    constructor(left: Int, top: Int, right: Int, bottom: Int) : this() {
        set(left, top, right, bottom)
    }

    constructor(r: Rect?) : this() {
        if (r != null) set(r)
    }

    fun set(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun set(src: Rect) = set(src.left, src.top, src.right, src.bottom)

    fun setEmpty() = set(0, 0, 0, 0)

    fun isEmpty(): Boolean = left >= right || top >= bottom

    fun width(): Int = right - left

    fun height(): Int = bottom - top

    fun centerX(): Int = (left + right) shr 1

    fun centerY(): Int = (top + bottom) shr 1

    fun exactCenterX(): Float = (left + right) * 0.5f

    fun exactCenterY(): Float = (top + bottom) * 0.5f

    fun offset(dx: Int, dy: Int) = set(left + dx, top + dy, right + dx, bottom + dy)

    fun contains(x: Int, y: Int): Boolean = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom

    override fun equals(other: Any?): Boolean =
        other is Rect && left == other.left && top == other.top && right == other.right && bottom == other.bottom

    override fun hashCode(): Int = ((left * 31 + top) * 31 + right) * 31 + bottom

    override fun toString(): String = "Rect($left, $top - $right, $bottom)"

    fun toShortString(): String = "[$left,$top][$right,$bottom]"
}
