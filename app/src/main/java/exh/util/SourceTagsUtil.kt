package exh.util

import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.HITOMI_SOURCE_ID
import exh.NHENTAI_SOURCE_ID

class SourceTagsUtil {
    fun getWrappedTag(sourceId: Long, namespace: String? = null, tag: String? = null, fullTag: String? = null): String? {
        return if (sourceId == EXH_SOURCE_ID || sourceId == EH_SOURCE_ID || sourceId == NHENTAI_SOURCE_ID || sourceId == HITOMI_SOURCE_ID) {
            val parsed = if (fullTag != null) parseTag(fullTag) else if (namespace != null && tag != null) Pair(namespace, tag) else null
            if (parsed != null) {
                when (sourceId) {
                    HITOMI_SOURCE_ID -> wrapTagHitomi(parsed.first, parsed.second.substringBefore('|').trim())
                    NHENTAI_SOURCE_ID -> wrapTagNHentai(parsed.first, parsed.second.substringBefore('|').trim())
                    else -> wrapTag(parsed.first, parsed.second.substringBefore('|').trim())
                }
            } else null
        } else null
    }

    fun parseTag(tag: String) = tag.substringBefore(':').trim() to tag.substringAfter(':').trim()

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
}
