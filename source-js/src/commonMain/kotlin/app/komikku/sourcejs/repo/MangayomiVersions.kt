package app.komikku.sourcejs.repo

/**
 * Mangayomi's `compareVersions` (contract 1.3): a leading `v` / `V` and a `+build` suffix are ignored, the
 * dot-separated parts before a `-pre-release` part compare as integers (missing or non-numeric parts count as 0); when
 * they are equal, a version without pre-release is greater than one with, and pre-release parts compare numerically
 * when both are numbers, a number sorts before text, text compares lexically, then the longer list wins.
 */
object MangayomiVersions {

    /** Negative when [a] is older than [b], zero when they are the same version, positive when [a] is newer. */
    fun compare(a: String, b: String): Int {
        val left = Parsed.of(a)
        val right = Parsed.of(b)
        val size = maxOf(left.core.size, right.core.size)
        for (i in 0 until size) {
            val diff = (left.core.getOrNull(i) ?: 0L).compareTo(right.core.getOrNull(i) ?: 0L)
            if (diff != 0) return diff
        }
        return when {
            left.pre == null && right.pre == null -> 0
            left.pre == null -> 1
            right.pre == null -> -1
            else -> comparePre(left.pre, right.pre)
        }
    }

    /** Whether [version] is not above [maximum] (blank versions are not above anything). */
    fun isAtMost(version: String, maximum: String): Boolean = version.isBlank() || compare(version, maximum) <= 0

    private fun comparePre(a: List<String>, b: List<String>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]
            val y = b[i]
            val xn = x.toLongOrNull()
            val yn = y.toLongOrNull()
            val diff = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1
                yn != null -> 1
                else -> x.compareTo(y)
            }
            if (diff != 0) return diff
        }
        return a.size.compareTo(b.size)
    }

    private class Parsed(val core: List<Long>, val pre: List<String>?) {
        companion object {
            fun of(version: String): Parsed {
                val text = version.trim().removePrefix("v").removePrefix("V").substringBefore('+')
                val dash = text.indexOf('-')
                val core = if (dash >= 0) text.substring(0, dash) else text
                val pre = if (dash >= 0) text.substring(dash + 1).split('.') else null
                return Parsed(core.split('.').map { it.trim().toLongOrNull() ?: 0L }, pre)
            }
        }
    }
}
