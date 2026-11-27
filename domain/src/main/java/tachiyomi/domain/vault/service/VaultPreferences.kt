package tachiyomi.domain.vault.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Persisted handoff state for entering the private vault.
 *
 * This is a one-shot flag, not the active profile: it is written before the app restarts into
 * the vault and cleared as soon as the new process starts. Any other process start (killed app,
 * crash, reboot, normal relaunch) must come back to the standard library, so the vault session
 * never survives process death.
 */
class VaultPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun pendingEnter() = preferenceStore.getBoolean(Preference.appStateKey(KEY_PENDING_ENTER), false)

    companion object {
        const val KEY_PENDING_ENTER = "vault_pending_enter"
    }
}
