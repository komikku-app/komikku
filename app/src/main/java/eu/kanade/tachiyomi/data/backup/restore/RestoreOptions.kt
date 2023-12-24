package eu.kanade.tachiyomi.data.backup.restore

data class RestoreOptions(
    val appSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    val library: Boolean = true,
    // SY -->
    val savedSearches: Boolean = true,
    // SY <--
) {
    fun toBooleanArray() = booleanArrayOf(
        appSettings,
        sourceSettings,
        library,
        // SY -->
        savedSearches,
        // SY <--
    )

    companion object {
        fun fromBooleanArray(booleanArray: BooleanArray) = RestoreOptions(
            appSettings = booleanArray[0],
            sourceSettings = booleanArray[1],
            library = booleanArray[2],
            // SY -->
            savedSearches = booleanArray[3],
            // SY <--
        )
    }
}
