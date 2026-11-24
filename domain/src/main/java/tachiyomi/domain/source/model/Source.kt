package tachiyomi.domain.source.model

data class Source(
    val id: Long,
    val lang: String,
    val name: String,
    val supportsLatest: Boolean,
    val isStub: Boolean,
    val pin: Pins = Pins.unpinned,
    val isUsedLast: Boolean = false,
    // SY -->
    val category: String? = null,
    val isExcludedFromDataSaver: Boolean = false,
    val categories: Set<String> = emptySet(),
    // SY <--
) {

    val visualName: String
        get() = when {
            lang.isEmpty() -> name
            else -> "$name (${lang.uppercase()})"
        }

    val key: () -> String = {
        when {
            isUsedLast -> "$id-lastused"
            // SY -->
            // KMK -->
            // With the group in it: "$id-$category" was "$id-lastused" for a category named "lastused", a duplicate
            // list key next to the last used copy (a crash in the sources tab)
            category != null -> "$id-category-$category"
            // KMK <--
            // SY <--
            else -> "$id"
        }
    }
}
