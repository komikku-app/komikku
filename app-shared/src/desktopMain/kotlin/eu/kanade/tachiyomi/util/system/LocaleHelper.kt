package eu.kanade.tachiyomi.util.system

import android.content.Context
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.util.Locale

/**
 * KMK: desktop counterpart of the Android `LocaleHelper` (app-only: it uses `LocaleListCompat` and the Android
 * `SourcesScreenModel`) for the borrowed `GetLanguagesWithSources` and the desktop source screens. Same names, order
 * and special keys; the system language is the JVM default locale. The app's display language is not switched here
 * (desktop UI language = `StringDesc.localeType`, PROGRESS.md 1.5).
 */
object LocaleHelper {

    /** Same keys as Android's `SourcesScreenModel.LAST_USED_KEY` / `PINNED_KEY`. */
    const val LAST_USED_KEY = "last_used"
    const val PINNED_KEY = "pinned"

    /**
     * Sorts by display name, except keeps the "all" (displayed as "Multi") locale at the top.
     */
    val comparator = { a: String, b: String ->
        if (a == "all") {
            -1
        } else if (b == "all") {
            1
        } else {
            getLocalizedDisplayName(a).compareTo(getLocalizedDisplayName(b))
        }
    }

    /**
     * Returns display name of a string language code.
     */
    fun getSourceDisplayName(lang: String?, context: Context): String {
        if (lang != null && lang.contains("custom|")) {
            return lang.split("|")[1]
        }
        return when (lang) {
            LAST_USED_KEY -> context.stringResource(MR.strings.last_used_source)
            PINNED_KEY -> context.stringResource(MR.strings.pinned_sources)
            "other" -> context.stringResource(MR.strings.other_source)
            "all" -> context.stringResource(MR.strings.multi_lang)
            else -> getLocalizedDisplayName(lang)
        }
    }

    fun getDisplayName(lang: String): String {
        val normalizedLang = when (lang) {
            "zh-CN" -> "zh-Hans"
            "zh-TW" -> "zh-Hant"
            else -> lang
        }

        return Locale.forLanguageTag(normalizedLang).displayName
    }

    fun getShortDisplayName(lang: String?): String {
        return when (lang) {
            null -> ""
            "es-419" -> "es-la"
            "zh-CN" -> "zh-hans"
            "zh-TW" -> "zh-hant"
            else -> lang
        }
    }

    /**
     * Returns display name of a string language code.
     *
     * @param lang empty for system language
     */
    fun getLocalizedDisplayName(lang: String?): String {
        if (lang == null) {
            return ""
        }

        val locale = when (lang) {
            "" -> Locale.getDefault()
            "zh-CN" -> Locale.forLanguageTag("zh-Hans")
            "zh-TW" -> Locale.forLanguageTag("zh-Hant")
            else -> Locale.forLanguageTag(lang)
        }
        return locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
    }

    /**
     * Return the default languages enabled for the sources.
     */
    fun getDefaultEnabledLanguages(): Set<String> {
        return setOf("all", "en", Locale.getDefault().language)
    }
}
