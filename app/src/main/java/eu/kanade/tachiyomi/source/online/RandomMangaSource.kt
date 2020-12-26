package eu.kanade.tachiyomi.source.online

import tachiyomi.source.Source

interface RandomMangaSource : Source {
    suspend fun fetchRandomMangaUrl(): String
}
