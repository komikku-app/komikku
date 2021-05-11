package exh.util

import android.graphics.Color
import eu.kanade.tachiyomi.data.database.models.Manga
import exh.metadata.metadata.base.RaisedTag
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.PURURIN_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.source.hitomiSourceIds
import exh.source.nHentaiSourceIds
import java.util.Locale

object SourceTagsUtil {
    fun getWrappedTag(sourceId: Long, namespace: String? = null, tag: String? = null, fullTag: String? = null): String? {
        return if (sourceId == EXH_SOURCE_ID || sourceId == EH_SOURCE_ID || sourceId in nHentaiSourceIds || sourceId in hitomiSourceIds) {
            val parsed = when {
                fullTag != null -> parseTag(fullTag)
                namespace != null && tag != null -> RaisedTag(namespace, tag, TAG_TYPE_DEFAULT)
                else -> null
            }
            if (parsed?.namespace != null) {
                when (sourceId) {
                    in hitomiSourceIds -> wrapTagHitomi(parsed.namespace, parsed.name.substringBefore('|').trim())
                    in nHentaiSourceIds -> wrapTagNHentai(parsed.namespace, parsed.name.substringBefore('|').trim())
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

    fun parseTag(tag: String) = RaisedTag(
        if (tag.startsWith("-")) {
            tag.substringAfter("-")
        } else {
            tag
        }.substringBefore(':', missingDelimiterValue = "").trimOrNull(),
        tag.substringAfter(':', missingDelimiterValue = tag).trim(),
        if (tag.startsWith("-")) TAG_TYPE_EXCLUDE else TAG_TYPE_DEFAULT
    )

    const val TAG_TYPE_EXCLUDE = 69 // why not

    enum class GenreColor(val color: Int) {
        DOUJINSHI_COLOR("#f44336"),
        MANGA_COLOR("#ff9800"),
        ARTIST_CG_COLOR("#fbc02d"),
        GAME_CG_COLOR("#4caf50"),
        WESTERN_COLOR("#8bc34a"),
        NON_H_COLOR("#2196f3"),
        IMAGE_SET_COLOR("#3f51b5"),
        COSPLAY_COLOR("#9c27b0"),
        ASIAN_PORN_COLOR("#9575cd"),
        MISC_COLOR("#f06292");

        constructor(color: String) : this(Color.parseColor(color))
    }

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

fun Manga.getRaisedTags(genres: List<String>? = getGenres()): List<RaisedTag>? = genres?.map {
    SourceTagsUtil.parseTag(it)
}
