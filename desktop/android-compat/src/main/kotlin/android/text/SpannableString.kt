package android.text

/**
 * android-compat: `android.text.SpannableString`, immutable text whose spans are dropped ([setSpan] and [removeSpan]
 * are no-ops, see [Spannable]).
 */
open class SpannableString(source: CharSequence) :
    Spannable,
    CharSequence {

    private val text = source.toString()

    override val length: Int get() = text.length

    override fun get(index: Int): Char = text[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = SpannableString(text.substring(startIndex, endIndex))

    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) = Unit

    override fun removeSpan(what: Any?) = Unit

    override fun toString(): String = text

    override fun equals(other: Any?): Boolean = other is SpannableString && other.text == text

    override fun hashCode(): Int = text.hashCode()

    companion object {
        @JvmStatic
        fun valueOf(source: CharSequence): SpannableString = source as? SpannableString ?: SpannableString(source)
    }
}
