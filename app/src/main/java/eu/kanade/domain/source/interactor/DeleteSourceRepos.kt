package eu.kanade.domain.source.interactor

import eu.kanade.tachiyomi.data.preference.PreferencesHelper

class DeleteSourceRepos(private val preferences: PreferencesHelper) {

    fun await(repos: List<String>) {
        preferences.extensionRepos().set(
            preferences.extensionRepos().get().filterNot { it in repos }.toSet(),
        )
    }
}
