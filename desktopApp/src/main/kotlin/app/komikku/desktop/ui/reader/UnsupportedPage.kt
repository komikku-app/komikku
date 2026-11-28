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

    /**
     * Another class or member of Android's that desktop does not have, e.g. `android.graphics.Matrix` or
     * `android.graphics.Canvas.drawBitmap(Bitmap, Matrix, Paint)`.
     */
    data class Missing(override val needs: String) : UnsupportedPage

    companion object {
        /** Paint / Canvas members that draw or measure text. */
        private val TEXT_MEMBERS = setOf(
            "getFontSpacing",
            "getFontMetrics",
            "getFontMetricsInt",
            "hasGlyph",
            "setLetterSpacing",
            "setTypeface",
            "getTypeface",
        )
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
            // A member a compat class does not have (NoSuchMethodError, NoSuchFieldError, AbstractMethodError, …)
            is LinkageError -> e.message?.let(::missingMember)
            else -> null
        }

        private val ANDROID_PACKAGES = listOf("android.", "androidx.", "dalvik.")
        private const val ANDROID_CLASS = """(?:android|androidx|dalvik)\.[\w.$]+"""

        /** NoSuchMethodError: `'void android.graphics.Canvas.drawPath(android.graphics.Path, android.graphics.Paint)'`. */
        private val METHOD = Regex("""($ANDROID_CLASS)\.([\w$<>]+)\(([^)]*)\)""")

        /** NoSuchFieldError: `Class android.graphics.Paint does not have member field 'int X'`. */
        private val FIELD = Regex("""Class ($ANDROID_CLASS) does not have member field '\S+ ([\w$]+)'""")

        private val ANY_CLASS = Regex("""(?:android|androidx|dalvik)[./][\w./$]+""")

        /** The Android member a linkage error's [message] names, parameter types without their package. */
        private fun missingMember(message: String): UnsupportedPage? {
            METHOD.find(message)?.destructured?.let { (className, member, params) ->
                val types = params.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val needs = "$className.$member(${types.joinToString(", ") { it.substringAfterLast('.') }})"
                val text = isTextClass(className) || (className in TEXT_CLASSES && isTextMember(member)) ||
                    types.any(::isTextClass)
                return if (text) TextDrawing(needs) else Missing(needs)
            }
            FIELD.find(message)?.destructured?.let { (className, field) ->
                val needs = "$className.$field"
                return if (isTextClass(className)) TextDrawing(needs) else Missing(needs)
            }
            return ANY_CLASS.find(message)?.value?.replace('/', '.')?.trimEnd('.')
                ?.let { name -> if (isTextClass(name)) TextDrawing(name) else Missing(name) }
        }

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
