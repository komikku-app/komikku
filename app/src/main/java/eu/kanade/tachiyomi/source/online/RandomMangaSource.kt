package eu.kanade.tachiyomi.source.online

import kotlinx.coroutines.flow.Flow

interface RandomMangaSource {
    fun fetchRandomMangaUrl(): Flow<String>
}
