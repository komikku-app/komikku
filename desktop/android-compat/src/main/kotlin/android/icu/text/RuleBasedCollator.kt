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
        val a = runs(source.orEmpty())
        val b = runs(target.orEmpty())
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]
            val y = b[i]
            val order = if (x.isDigits && y.isDigits) {
                compareValuesBy(x, y, { it.text.length }, { it.text })
            } else {
                super.compare(x.text, y.text)
            }
            if (order != 0) return order
        }
        return a.size.compareTo(b.size)
    }

    override fun equals(other: Any?): Boolean = super.equals(other) && (other as RuleBasedCollator).numeric == numeric

    override fun hashCode(): Int = super.hashCode() * 31 + numeric.hashCode()

    /** A run of decimal digits ([text] as ASCII digits without leading zeros) or of other characters. */
    private class Run(val text: String, val isDigits: Boolean)

    private fun runs(value: String): List<Run> {
        val runs = ArrayList<Run>()
        var start = 0
        while (start < value.length) {
            val isDigits = Character.isDigit(value[start])
            var end = start
            while (end < value.length && Character.isDigit(value[end]) == isDigits) end++
            val run = value.substring(start, end)
            runs += if (isDigits) {
                Run(run.map { Character.forDigit(Character.digit(it, 10), 10) }.joinToString("").trimStart('0'), true)
            } else {
                Run(run, false)
            }
            start = end
        }
        return runs
    }
}
