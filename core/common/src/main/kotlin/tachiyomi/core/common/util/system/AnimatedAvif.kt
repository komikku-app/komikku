package tachiyomi.core.common.util.system

import okio.BufferedSource

/**
 * Detects AVIF image sequences (animated AVIF), whose `ftyp` box uses the `avis` brand.
 *
 * The bundled `tachiyomi.decoder` library reports both animated and still AVIF as a
 * non-animated AVIF, so the brand has to be checked separately.
 */
object AnimatedAvif {
    private const val FTYP = "ftyp"
    private const val ANIMATED_BRAND = "avis"
    private const val MAX_FTYP_SIZE = 1024L

    fun isAnimated(source: BufferedSource): Boolean {
        return try {
            val peek = source.peek()
            if (!peek.request(16)) return false

            val boxSize = peek.readInt().toLong() and 0xFFFFFFFFL
            if (boxSize < 16) return false
            if (peek.readUtf8(4) != FTYP) return false

            val size = minOf(boxSize, MAX_FTYP_SIZE)
            if (!peek.request(size - 8)) return false

            if (peek.readUtf8(4) == ANIMATED_BRAND) return true
            peek.skip(4) // minor_version

            var remaining = size - 16
            while (remaining >= 4) {
                if (peek.readUtf8(4) == ANIMATED_BRAND) return true
                remaining -= 4
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
