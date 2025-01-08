package exh.source

object BlacklistedSources {
    val BLACKLISTED_EXT_SOURCES = EHENTAI_EXT_SOURCES.keys

    val BLACKLISTED_EXTENSIONS = arrayOf(
        "eu.kanade.tachiyomi.extension.all.ehentai",
    )

    var HIDDEN_SOURCES = setOf(
        MERGED_SOURCE_ID,
    )
}
