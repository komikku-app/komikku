package exh.util

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import exh.metadata.metadata.base.RaisedTag
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.PURURIN_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds
import tachiyomi.presentation.core.icons.FlagEmoji.Companion.getEmojiLangFlag
import java.util.Locale

object SourceTagsUtil {
    fun getWrappedTag(
        sourceId: Long?,
        namespace: String? = null,
        tag: String? = null,
        fullTag: String? = null,
    ): String? {
        return if (
            sourceId == EXH_SOURCE_ID ||
            sourceId == EH_SOURCE_ID ||
            sourceId in nHentaiSourceIds ||
            sourceId in mangaDexSourceIds ||
            sourceId == PURURIN_SOURCE_ID ||
            sourceId == TSUMINO_SOURCE_ID
        ) {
            val parsed = when {
                fullTag != null -> parseTag(fullTag)
                namespace != null && tag != null -> RaisedTag(namespace, tag, TAG_TYPE_DEFAULT)
                else -> null
            }
            if (parsed?.namespace != null) {
                when (sourceId) {
                    in nHentaiSourceIds -> wrapTagNHentai(parsed.namespace!!, parsed.name.substringBefore('|').trim())
                    in mangaDexSourceIds -> parsed.name
                    PURURIN_SOURCE_ID -> parsed.name.substringBefore('|').trim()
                    TSUMINO_SOURCE_ID -> wrapTagTsumino(parsed.namespace!!, parsed.name.substringBefore('|').trim())
                    else -> wrapTag(parsed.namespace!!, parsed.name.substringBefore('|').trim())
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun wrapTag(namespace: String, tag: String) = if (tag.contains(spaceRegex)) {
        "$namespace:\"$tag$\""
    } else {
        "$namespace:$tag$"
    }

    private fun wrapTagNHentai(namespace: String, tag: String) = if (tag.contains(spaceRegex)) {
        if (namespace == "tag") {
            """"$tag""""
        } else {
            """$namespace:"$tag""""
        }
    } else {
        "$namespace:$tag"
    }

    private fun wrapTagTsumino(namespace: String, tag: String) = if (tag.contains(spaceRegex)) {
        if (namespace == "tags") {
            "\"${tag.replace(spaceRegex, "_")}\""
        } else {
            "\"$namespace: ${tag.replace(spaceRegex, "_")}\""
        }
    } else {
        if (namespace == "tags") {
            tag
        } else {
            "$namespace:$tag"
        }
    }

    fun parseTag(tag: String) = RaisedTag(
        if (tag.startsWith("-")) {
            tag.substringAfter("-")
        } else {
            tag
        }.substringBefore(':', missingDelimiterValue = "").trimOrNull(),
        tag.substringAfter(':', missingDelimiterValue = tag).trim(),
        if (tag.startsWith("-")) TAG_TYPE_EXCLUDE else TAG_TYPE_DEFAULT,
    )

    private const val TAG_TYPE_EXCLUDE = 69 // why not

    enum class GenreColor(val color: Int) {
        DOUJINSHI_COLOR("#ff614d"),
        MANGA_COLOR("#ff9800"),
        ARTIST_CG_COLOR("#fbc02d"),
        GAME_CG_COLOR("#4caf50"),
        WESTERN_COLOR("#8bc34a"),
        NON_H_COLOR("#2c9bf8"),
        IMAGE_SET_COLOR("#3c4fb3"),
        COSPLAY_COLOR("#921aa6"),
        ASIAN_PORN_COLOR("#a685df"),
        MISC_COLOR("#f36594"),
        ;

        constructor(color: String) : this(Color.parseColor(color))
    }

    @ColorInt fun genreTextColor(genre: GenreColor): Int {
        return when (genre) {
            GenreColor.DOUJINSHI_COLOR -> Color.parseColor("#000000")
            GenreColor.MANGA_COLOR -> Color.parseColor("#000000")
            GenreColor.ARTIST_CG_COLOR -> Color.parseColor("#000000")
            GenreColor.GAME_CG_COLOR -> Color.parseColor("#000000")
            GenreColor.WESTERN_COLOR -> Color.parseColor("#000000")
            GenreColor.NON_H_COLOR -> Color.parseColor("#000000")
            GenreColor.IMAGE_SET_COLOR -> Color.parseColor("#FFFFFF")
            GenreColor.COSPLAY_COLOR -> Color.parseColor("#FFFFFF")
            GenreColor.ASIAN_PORN_COLOR -> Color.parseColor("#000000")
            GenreColor.MISC_COLOR -> Color.parseColor("#000000")
        }
    }

    fun getLocaleSourceUtil(language: String?) = when (language) {
        "english", "eng" -> Locale("en")
        "japanese" -> Locale("ja")
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

    private val spaceRegex = "\\s".toRegex()
}

@Preview
@Composable
private fun LanguageFlagPreview() {
    val locales = listOf(
        Locale("en"),
        Locale("ja"),
        Locale("zh"),
        Locale("es"),
        Locale("ko"),
        Locale("ru"),
        Locale("fr"),
        Locale("pt"),
        Locale("th"),
        Locale("de"),
        Locale("it"),
        Locale("vi"),
        Locale("pl"),
        Locale("hu"),
        Locale("nl"),
    )
    Column {
        FlowRow {
            locales.forEach {
                Text(text = getEmojiLangFlag(it.toLanguageTag()))
            }
        }
    }
}
