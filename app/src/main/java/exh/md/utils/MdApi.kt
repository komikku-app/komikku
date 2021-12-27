package exh.md.utils

object MdApi {
    const val baseUrl = "https://api.mangadex.org"
    const val login = "$baseUrl/auth/login"
    const val checkToken = "$baseUrl/auth/check"
    const val refreshToken = "$baseUrl/auth/refresh"
    const val logout = "$baseUrl/auth/logout"
    const val manga = "$baseUrl/manga"
    const val chapter = "$baseUrl/chapter"
    const val group = "$baseUrl/group"
    const val author = "$baseUrl/author"
    const val rating = "$baseUrl/rating"
    const val statistics = "$baseUrl/statistics/manga"
    const val chapterImageServer = "$baseUrl/at-home/server"
    const val userFollows = "$baseUrl/user/follows/manga"
    const val readingStatusForAllManga = "$baseUrl/manga/status"
    const val atHomeServer = "$baseUrl/at-home/server"

    const val legacyMapping = "$baseUrl/legacy/mapping"
}
