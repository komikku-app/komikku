package android.icu.text

import app.komikku.desktop.compat.unsupported

/**
 * android-compat: `android.icu.text.RuleBasedCollator` (what [Collator.getInstance] returns) on the JDK's
 * `java.text.RuleBasedCollator`, plus ICU's numeric collation ([setNumericCollation]): runs of decimal digits compare
 * by their value, leading zeros ignored ("Chapter 2" before "Chapter 10", "Vol. 02" equal to "Vol. 2"), the text
 * between them by the locale's rules. ICU's case level is not available ([setCaseLevel]).
 */
class RuleBasedCollator internal constructor(delegate: java.text.RuleBasedCollator) : Collator(delegate) {

    private var numeric = false

    fun getNumericCollation(): Boolean = numeric

    fun setNumericCollation(flag: Boolean) {
        checkNotFrozen()
        numeric = flag
    }

    fun isCaseLevel(): Boolean = false

    fun setCaseLevel(flag: Boolean) {
        checkNotFrozen()
        if (flag) unsupported("android.icu.text.RuleBasedCollator", "setCaseLevel(true)")
    }

    override fun compare(source: String?, target: String?): Int {
        if (!numeric) return super.compare(source, target)
        // One whole-string comparison, like ICU: all primary differences first, so a later letter outranks an earlier
        // case or accent difference, and a number lines up with whatever stands at its place in the other string
        return super.compare(sortable(source.orEmpty()), sortable(target.orEmpty()))
    }

    override fun equals(other: Any?): Boolean = super.equals(other) && (other as RuleBasedCollator).numeric == numeric

    override fun hashCode(): Int = super.hashCode() * 31 + numeric.hashCode()

    /**
     * [value] with every run of decimal digits (any script's) replaced by ASCII digits that sort by the number's value:
     * leading zeros dropped ("0" for zeros), after a length prefix (one '9' per 9 digits, then the rest as a digit), so
     * a longer number sorts after a shorter one digit by digit.
     */
    private fun sortable(value: String): String {
        val out = StringBuilder(value.length + 4)
        var start = 0
        while (start < value.length) {
            if (!Character.isDigit(value[start])) {
                out.append(value[start++])
                continue
            }
            var end = start
            while (end < value.length && Character.isDigit(value[end])) end++
            val digits = value.substring(start, end).map { Character.forDigit(Character.digit(it, 10), 10) }
                .joinToString("").trimStart('0').ifEmpty { "0" }
            repeat((digits.length - 1) / 9) { out.append('9') }
            out.append(Character.forDigit((digits.length - 1) % 9, 10)).append(digits)
            start = end
        }
        return out.toString()
    }
}
