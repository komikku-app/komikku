package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper

class ReorderSortTag(
    private val preferences: PreferencesHelper,
    private val getSortTag: GetSortTag,
) {

    fun await(tag: String, newPosition: Int): Result {
        val tags = getSortTag.await()
        val currentIndex = tags.indexOfFirst { it == tag }

        if (currentIndex == -1) {
            return Result.InternalError
        }

        if (currentIndex == newPosition) {
            return Result.Unchanged
        }

        val reorderedTags = tags.toMutableList()
        val reorderedTag = reorderedTags.removeAt(currentIndex)
        reorderedTags.add(newPosition, reorderedTag)

        preferences.sortTagsForLibrary().set(
            reorderedTags.mapIndexed { index, s ->
                CreateSortTag.encodeTag(index, s)
            }.toSet(),
        )

        return Result.Success
    }

    sealed class Result {
        object Success : Result()
        object Unchanged : Result()
        object InternalError : Result()
    }
}
