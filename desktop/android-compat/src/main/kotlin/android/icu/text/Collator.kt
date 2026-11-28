package android.icu.text

import java.util.Locale

/**
 * android-compat: `android.icu.text.Collator` on the JDK's `java.text.Collator` (locale-sensitive string order, used by
 * extensions to sort titles or genres). ICU's strengths and decomposition modes are mapped onto the JDK's;
 * `QUATERNARY` orders like `TERTIARY`, as the JDK has no fourth level.
 */
open class Collator private constructor(private var delegate: java.text.Collator) :
    Comparator<Any?>,
    Cloneable {

    open fun compare(source: String?, target: String?): Int = delegate.compare(source.orEmpty(), target.orEmpty())

    override fun compare(source: Any?, target: Any?): Int = compare(source as String?, target as String?)

    open fun equals(source: String?, target: String?): Boolean = compare(source, target) == 0

    open fun getStrength(): Int = when (delegate.strength) {
        java.text.Collator.PRIMARY -> PRIMARY
        java.text.Collator.SECONDARY -> SECONDARY
        java.text.Collator.IDENTICAL -> IDENTICAL
        else -> TERTIARY
    }

    open fun setStrength(newStrength: Int) {
        delegate.strength = when (newStrength) {
            PRIMARY -> java.text.Collator.PRIMARY
            SECONDARY -> java.text.Collator.SECONDARY
            TERTIARY, QUATERNARY -> java.text.Collator.TERTIARY
            IDENTICAL -> java.text.Collator.IDENTICAL
            else -> throw IllegalArgumentException("Incorrect comparison level: $newStrength")
        }
    }

    open fun getDecomposition(): Int =
        if (delegate.decomposition == java.text.Collator.NO_DECOMPOSITION) NO_DECOMPOSITION else CANONICAL_DECOMPOSITION

    open fun setDecomposition(decomposition: Int) {
        delegate.decomposition = when (decomposition) {
            NO_DECOMPOSITION -> java.text.Collator.NO_DECOMPOSITION
            CANONICAL_DECOMPOSITION -> java.text.Collator.CANONICAL_DECOMPOSITION
            else -> throw IllegalArgumentException("Wrong decomposition mode: $decomposition")
        }
    }

    public override fun clone(): Any = Collator(delegate.clone() as java.text.Collator)

    override fun equals(other: Any?): Boolean = other is Collator && other.delegate == delegate

    override fun hashCode(): Int = delegate.hashCode()

    companion object {
        const val PRIMARY = 0
        const val SECONDARY = 1
        const val TERTIARY = 2
        const val QUATERNARY = 3
        const val IDENTICAL = 15
        const val NO_DECOMPOSITION = 16
        const val CANONICAL_DECOMPOSITION = 17

        @JvmStatic
        fun getInstance(): Collator = getInstance(Locale.getDefault())

        @JvmStatic
        fun getInstance(locale: Locale?): Collator = Collator(java.text.Collator.getInstance(locale ?: Locale.getDefault()))

        @JvmStatic
        fun getAvailableLocales(): Array<Locale> = java.text.Collator.getAvailableLocales()
    }
}
