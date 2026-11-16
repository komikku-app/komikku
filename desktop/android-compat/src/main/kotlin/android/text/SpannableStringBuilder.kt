package android.text

/**
 * android-compat: `android.text.SpannableStringBuilder` as plain mutable text; spans are not
 * supported on desktop.
 */
open class SpannableStringBuilder(text: CharSequence = "") : Editable, Spanned {

    private val builder = StringBuilder(text)

    override val length: Int get() = builder.length

    override fun get(index: Int): Char = builder[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        SpannableStringBuilder(builder.substring(startIndex, endIndex))

    override fun replace(st: Int, en: Int, text: CharSequence): SpannableStringBuilder =
        apply { builder.replace(st, en, text.toString()) }

    override fun insert(where: Int, text: CharSequence): SpannableStringBuilder = apply { builder.insert(where, text) }

    override fun delete(st: Int, en: Int): SpannableStringBuilder = apply { builder.delete(st, en) }

    override fun clear() {
        builder.setLength(0)
    }

    override fun append(text: CharSequence?): SpannableStringBuilder = apply { builder.append(text) }

    override fun append(text: CharSequence?, start: Int, end: Int): SpannableStringBuilder =
        apply { builder.append(text, start, end) }

    override fun append(text: Char): SpannableStringBuilder = apply { builder.append(text) }

    override fun toString(): String = builder.toString()

    override fun equals(other: Any?): Boolean =
        other is SpannableStringBuilder && other.toString() == toString()

    override fun hashCode(): Int = toString().hashCode()
}
