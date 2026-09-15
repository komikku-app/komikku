package tachiyomi.domain.manga.model

import tachiyomi.domain.taste.model.MangaRating

data class PersonalTasteProfile(
    val genreWeights: Map<String, Int>,
) {
    val preferredGenres: List<String> = genreWeights
        .filterValues { it > 0 }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

    fun score(candidateGenres: Iterable<String>): Int {
        return candidateGenres
            .map { it.normalizedGenre() }
            .distinct()
            .sumOf { genreWeights[it] ?: 0 }
    }
}

fun buildPersonalTasteProfile(
    manga: Iterable<Manga>,
    ratings: Map<Long, MangaRating>,
): PersonalTasteProfile? {
    val weights = buildMap {
        manga.forEach { entry ->
            val ratingWeight = ratings[entry.id]?.value ?: return@forEach

            entry.ogGenre.orEmpty()
                .map { it.normalizedGenre() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { genre -> put(genre, (get(genre) ?: 0) + ratingWeight) }
        }
    }.filterValues { it != 0 }

    return weights.takeIf { values -> values.any { it.value > 0 } }?.let(::PersonalTasteProfile)
}

private fun String.normalizedGenre(): String = trim().lowercase()
