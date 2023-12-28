package eu.kanade.domain.manga.interactor

import tachiyomi.domain.library.service.LibraryPreferences

class ReorderSortTag(
    private val preferences: LibraryPreferences,
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
        data object Success : Result()
        data object Unchanged : Result()
        data object InternalError : Result()
    }
}
