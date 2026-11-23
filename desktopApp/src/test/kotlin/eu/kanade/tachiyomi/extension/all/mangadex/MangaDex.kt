package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.online.HttpSource

/** P4 test double of the MangaDex extension (English): only the class name the SY delegate is chosen by. */
class MangaDex : HttpSource() {
    override val name = "MangaDex"
    override val lang = "en"
    override val baseUrl = "https://mangadex.org"
    override val supportsLatest = true
}
