package app.komikku.desktop.ui.reader

import app.komikku.desktop.compat.UnsupportedOnDesktop

/**
 * Why a page cannot be shown on desktop however often it is retried: its extension's code needs something the
 * Android compat layer does not have. Read from the error the page failed with ([of]).
 */
sealed interface UnsupportedPage {

    /** What is missing, e.g. `android.text.StaticLayout` or `android.graphics.Paint.measureText`. */
    val needs: String

    /**
     * The extension draws the page from text (Keiyoushi's text-interceptor library, captions, speech-bubble overlays):
     * text layout and fonts (`StaticLayout`, `TextPaint`, `Typeface`, `Paint`'s text members) are not on desktop yet.
     */
    data class TextDrawing(override val needs: String) : UnsupportedPage

    /** Another class or member of Android's that desktop does not have. */
    data class Missing(override val needs: String) : UnsupportedPage

    companion object {
        /** Paint / Canvas members that draw or measure text. */
        private val TEXT_MEMBERS = setOf("getFontSpacing", "hasGlyph", "setLetterSpacing", "setTypeface", "getTypeface")
        private val TEXT_CLASSES = setOf("android.graphics.Paint", "android.graphics.Canvas")

        /**
         * The unsupported part in [error], its causes or the errors suppressed into them (OkHttp hands an interceptor's
         * failure to an async call as `IOException("canceled due to …")` with the failure suppressed), text drawing
         * first; null for any other failure (network, decoding), which a retry may fix.
         */
        fun of(error: Throwable): UnsupportedPage? {
            val found = mutableListOf<UnsupportedPage>()
            val seen = HashSet<Throwable>()
            val queue = ArrayDeque(listOf(error))
            while (queue.isNotEmpty()) {
                val e = queue.removeFirst()
                if (!seen.add(e)) continue
                classify(e)?.let(found::add)
                e.cause?.let(queue::add)
                queue.addAll(e.suppressed)
            }
            return found.firstOrNull { it is TextDrawing } ?: found.firstOrNull()
        }

        private fun classify(e: Throwable): UnsupportedPage? = when (e) {
            is UnsupportedOnDesktop -> {
                val member = e.member.substringBefore('(')
                if (isTextClass(e.className) || (e.className in TEXT_CLASSES && isTextMember(member))) {
                    TextDrawing(e.needs)
                } else {
                    Missing(e.needs)
                }
            }
            // A class the compat layer does not have: "android/text/StaticLayout", or "Could not initialize class …"
            is NoClassDefFoundError, is ClassNotFoundException ->
                e.message
                    ?.substringAfter("Could not initialize class ")
                    ?.replace('/', '.')
                    ?.takeIf { name -> ANDROID_PACKAGES.any { name.startsWith(it) } }
                    ?.let { name -> if (isTextClass(name)) TextDrawing(name) else Missing(name) }
            else -> null
        }

        private val ANDROID_PACKAGES = listOf("android.", "androidx.", "dalvik.")

        private fun isTextClass(name: String): Boolean =
            name.startsWith("android.text.") ||
                name.startsWith("android.graphics.fonts.") ||
                name == "android.graphics.Typeface" ||
                name.startsWith("android.graphics.Typeface$") ||
                name == "android.graphics.Paint\$Align" ||
                name.startsWith("android.graphics.Paint\$FontMetrics")

        private fun isTextMember(member: String): Boolean = "Text" in member || member in TEXT_MEMBERS
    }
}
