package eu.kanade.tachiyomi.data.database.models

class LibraryManga : MangaImpl() {

    var unread: Int = 0

    var category: Int = 0

    // SY -->
    var read: Int = 0
    // SY <--
}
