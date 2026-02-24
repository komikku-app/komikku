package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class GetAllDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    /**
     * Returns all library manga grouped by normalized title or extracted alternate names,
     * filtering to only groups with 2+ entries (duplicates).
     */
    suspend operator fun invoke(): Map<String, List<Manga>> {
        val libraryManga = mangaRepository.getFavorites()

        // Data class to hold normalized properties for faster comparison
        data class NormalizedManga(
            val manga: Manga,
            val title: String = manga.title.normalizeForDuplicateCheck(),
            val altTitles: List<String> = getAltTitles(manga.description)
        )

        val normalizedList = libraryManga.map { NormalizedManga(it) }
        val disjointSet = IntArray(normalizedList.size) { it }

        fun find(i: Int): Int {
            if (disjointSet[i] == i) return i
            disjointSet[i] = find(disjointSet[i])
            return disjointSet[i]
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                disjointSet[rootI] = rootJ
            }
        }

        // Use a map to link titles to manga indices for faster lookups
        val titleMap = mutableMapOf<String, MutableList<Int>>()
        normalizedList.forEachIndexed { index, normalizedManga ->
            val allTitles = listOf(normalizedManga.title) + normalizedManga.altTitles
            allTitles.forEach { title ->
                if (title.isNotBlank()) {
                    titleMap.getOrPut(title) { mutableListOf() }.add(index)
                }
            }
        }

        // Union manga that share a title
        titleMap.values.forEach { indices ->
            if (indices.size > 1) {
                for (i in 1 until indices.size) {
                    union(indices[0], indices[i])
                }
            }
        }

        // Group by root of disjoint set
        return normalizedList.withIndex()
            .groupBy { find(it.index) }
            .filter { it.value.size > 1 }
            // Map the root index to the title of the first item in the cluster for the UI
            .mapKeys { entry -> entry.value.first().value.manga.title }
            .mapValues { entry -> entry.value.map { it.value.manga } }
    }

    private companion object {
        /**
         * Extracts alternate titles from the manga description if available.
         * Common patterns: "Alternative Titles: ...", "Alt names: ...", "Alternative: ..."
         */
        private fun getAltTitles(description: String?): List<String> {
            if (description.isNullOrBlank()) return emptyList()

            val lines = description.lines()
            val altTitles = mutableListOf<String>()

            for (line in lines) {
                val lowerLine = line.lowercase()
                if (lowerLine.startsWith("alternative:") ||
                    lowerLine.startsWith("alternative titles:") ||
                    lowerLine.startsWith("alt name(s):") ||
                    lowerLine.startsWith("alt names:") ||
                    lowerLine.startsWith("other names:")
                ) {
                    // Extract the part after the colon
                    val content = line.substringAfter(":").trim()
                    // Usually separated by comma, semicolon, or slash
                    val titles = content.split(Regex("[,;/|]+"))
                    altTitles.addAll(titles.map { it.normalizeForDuplicateCheck() }.filter { it.isNotBlank() })
                }
            }
            return altTitles
        }
    }
}

/**
 * Normalizes a string for duplicate comparison.
 * Strips non-alphanumeric characters, collapses whitespace, and lowercases.
 */
fun String.normalizeForDuplicateCheck(): String =
    this.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
