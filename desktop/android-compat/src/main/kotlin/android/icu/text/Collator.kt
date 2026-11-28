package android.icu.text

import java.util.Locale

/**
 * android-compat: `android.icu.text.Collator` on the JDK's `java.text.Collator` (locale-sensitive string order, used by
 * extensions to sort titles or genres). ICU's strengths and decomposition modes are mapped onto the JDK's;
 * `QUATERNARY` orders like `TERTIARY`, as the JDK has no fourth level. [getInstance] returns a [RuleBasedCollator], as
 * on Android. A [frozen][freeze] collator refuses changes and can be shared between threads.
 */
open class Collator internal constructor(internal var delegate: java.text.Collator) :
    Comparator<Any?>,
    Cloneable {

    private var frozen = false

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
        checkNotFrozen()
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
        checkNotFrozen()
        delegate.decomposition = when (decomposition) {
            NO_DECOMPOSITION -> java.text.Collator.NO_DECOMPOSITION
            CANONICAL_DECOMPOSITION -> java.text.Collator.CANONICAL_DECOMPOSITION
            else -> throw IllegalArgumentException("Wrong decomposition mode: $decomposition")
        }
    }

    /** Makes this collator read-only (ICU's `Freezable`); returns it. */
    open fun freeze(): Collator = apply { frozen = true }

    open fun isFrozen(): Boolean = frozen

    /** A copy that is not frozen. */
    open fun cloneAsThawed(): Collator = (clone() as Collator).also { it.frozen = false }

    /** A copy with its own settings (frozen if this one is). */
    public override fun clone(): Any = (super.clone() as Collator).also { it.delegate = delegate.clone() as java.text.Collator }

    protected fun checkNotFrozen() {
        if (frozen) throw UnsupportedOperationException("Attempt to modify frozen object")
    }

    override fun equals(other: Any?): Boolean = other is Collator && other.javaClass == javaClass && other.delegate == delegate

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
        fun getInstance(locale: Locale?): Collator =
            RuleBasedCollator(java.text.Collator.getInstance(locale ?: Locale.getDefault()) as java.text.RuleBasedCollator)

        @JvmStatic
        fun getAvailableLocales(): Array<Locale> = java.text.Collator.getAvailableLocales()
    }
}
