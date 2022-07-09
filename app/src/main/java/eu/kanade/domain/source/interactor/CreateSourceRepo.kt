package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.plusAssign

class CreateSourceRepo(private val preferences: PreferencesHelper) {

    fun await(name: String): Result {
        // Do not allow duplicate repos.
        if (repoExists(name)) {
            return Result.RepoExists
        }

        // Do not allow invalid formats
        if (!name.matches(repoRegex)) {
            return Result.InvalidName
        }

        preferences.extensionRepos() += name

        return Result.Success
    }

    sealed class Result {
        object RepoExists : Result()
        object InvalidName : Result()
        object Success : Result()
    }

    /**
     * Returns true if a repo with the given name already exists.
     */
    private fun repoExists(name: String): Boolean {
        return preferences.extensionRepos().get().any { it.equals(name, true) }
    }

    companion object {
        val repoRegex = """^[a-zA-Z0-9-_.]*?\/[a-zA-Z0-9-_.]*?$""".toRegex()
    }
}
