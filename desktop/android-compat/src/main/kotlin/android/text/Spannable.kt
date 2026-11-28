package android.text

/**
 * android-compat: `android.text.Spannable`. Desktop text carries no spans (see [Spanned]): [setSpan] and [removeSpan]
 * are no-ops; the text itself is kept.
 */
interface Spannable : Spanned {
    fun setSpan(what: Any?, start: Int, end: Int, flags: Int)

    fun removeSpan(what: Any?)
}
