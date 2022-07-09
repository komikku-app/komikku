package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.plusAssign

class CreateSortTag(
    private val preferences: PreferencesHelper,
    private val getSortTag: GetSortTag,
) {

    fun await(tag: String): Result {
        // Do not allow duplicate categories.
        // Do not allow duplicate categories.
        if (tagExists(tag.trim())) {
            return Result.TagExists
        }

        val size = preferences.sortTagsForLibrary().get().size

        preferences.sortTagsForLibrary() += encodeTag(size, tag)

        return Result.Success
    }

    sealed class Result {
        object TagExists : Result()
        object Success : Result()
    }

    /**
     * Returns true if a tag with the given name already exists.
     */
    private fun tagExists(name: String): Boolean {
        return getSortTag.await().any { it.equals(name) }
    }

    companion object {
        fun encodeTag(index: Int, tag: String) = "$index|${tag.trim()}"
    }
}
