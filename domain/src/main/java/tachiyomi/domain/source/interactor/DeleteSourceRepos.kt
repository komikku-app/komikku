package tachiyomi.domain.source.interactor

import tachiyomi.domain.UnsortedPreferences

class DeleteSourceRepos(private val preferences: UnsortedPreferences) {

    fun await(repos: List<String>) {
        preferences.extensionRepos().set(
            preferences.extensionRepos().get().filterNot { it in repos }.toSet(),
        )
    }
}
