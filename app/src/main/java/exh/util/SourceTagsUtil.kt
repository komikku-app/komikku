package exh.util

import eu.kanade.tachiyomi.data.database.models.Manga
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.HITOMI_SOURCE_ID
import exh.NHENTAI_SOURCE_ID
import exh.PURURIN_SOURCE_ID
import exh.TSUMINO_SOURCE_ID
import exh.metadata.metadata.base.RaisedTag
import java.util.Locale

class SourceTagsUtil {
    fun getWrappedTag(sourceId: Long, namespace: String? = null, tag: String? = null, fullTag: String? = null): String? {
        return if (sourceId == EXH_SOURCE_ID || sourceId == EH_SOURCE_ID || sourceId == NHENTAI_SOURCE_ID || sourceId == HITOMI_SOURCE_ID) {
            val parsed = if (fullTag != null) parseTag(fullTag) else if (namespace != null && tag != null) RaisedTag(namespace, tag, TAG_TYPE_DEFAULT) else null
            if (parsed?.namespace != null) {
                when (sourceId) {
                    HITOMI_SOURCE_ID -> wrapTagHitomi(parsed.namespace, parsed.name.substringBefore('|').trim())
                    NHENTAI_SOURCE_ID -> wrapTagNHentai(parsed.namespace, parsed.name.substringBefore('|').trim())
                    PURURIN_SOURCE_ID -> parsed.name.substringBefore('|').trim()
                    TSUMINO_SOURCE_ID -> parsed.name.substringBefore('|').trim()
                    else -> wrapTag(parsed.namespace, parsed.name.substringBefore('|').trim())
                }
            } else null
        } else null
    }

    private fun wrapTag(namespace: String, tag: String) = if (tag.contains(' ')) {
        "$namespace:\"$tag$\""
    } else {
        "$namespace:$tag$"
    }

    private fun wrapTagHitomi(namespace: String, tag: String) = if (tag.contains(' ')) {
        "$namespace:$tag".replace("\\s".toRegex(), "_")
    } else {
        "$namespace:$tag"
    }

    private fun wrapTagNHentai(namespace: String, tag: String) = if (tag.contains(' ')) {
        if (namespace == "tag") {
            "\"$tag\""
        } else {
            "$namespace:\"$tag\""
        }
    } else {
        "$namespace:$tag"
    }
    companion object {
        fun Manga.getRaisedTags(genres: List<String>? = null): List<RaisedTag>? = (genres ?: this.getGenres())?.map { parseTag(it) }

        fun parseTag(tag: String) = RaisedTag(
            (
                if (tag.startsWith("-")) {
                    tag.substringAfter("-")
                } else {
                    tag
                }
                ).substringBefore(':', missingDelimiterValue = "").trimOrNull(),
            tag.substringAfter(':', missingDelimiterValue = tag).trim(),
            if (tag.startsWith("-")) TAG_TYPE_EXCLUDE else TAG_TYPE_DEFAULT
        )

        const val TAG_TYPE_EXCLUDE = 69 // why not

        const val DOUJINSHI_COLOR = "#f44336"
        const val MANGA_COLOR = "#ff9800"
        const val ARTIST_CG_COLOR = "#fbc02d"
        const val GAME_CG_COLOR = "#4caf50"
        const val WESTERN_COLOR = "#8bc34a"
        const val NON_H_COLOR = "#2196f3"
        const val IMAGE_SET_COLOR = "#3f51b5"
        const val COSPLAY_COLOR = "#9c27b0"
        const val ASIAN_PORN_COLOR = "#9575cd"
        const val MISC_COLOR = "#f06292"

        fun getLocaleSourceUtil(language: String?) = when (language) {
            "english", "eng" -> Locale("en")
            "chinese" -> Locale("zh")
            "spanish" -> Locale("es")
            "korean" -> Locale("ko")
            "russian" -> Locale("ru")
            "french" -> Locale("fr")
            "portuguese" -> Locale("pt")
            "thai" -> Locale("th")
            "german" -> Locale("de")
            "italian" -> Locale("it")
            "vietnamese" -> Locale("vi")
            "polish" -> Locale("pl")
            "hungarian" -> Locale("hu")
            "dutch" -> Locale("nl")
            else -> null
        }

        private const val TAG_TYPE_DEFAULT = 1
    }
}
