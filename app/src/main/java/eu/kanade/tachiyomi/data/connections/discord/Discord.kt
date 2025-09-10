// AM (DISCORD) -->

// Taken from Animiru. Thank you Quickdev for permission!

package eu.kanade.tachiyomi.data.connections.discord

import android.graphics.Color
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsService
import exh.log.xLogE
import kotlinx.serialization.json.Json
import tachiyomi.i18n.kmk.KMR
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Discord(id: Long) : ConnectionsService(id) {

    override fun nameStrRes() = KMR.strings.connections_discord

    override fun getLogo() = R.drawable.ic_discord_24dp

    @Suppress("MagicNumber")
    override fun getLogoColor() = Color.rgb(88, 101, 242)

    override fun logout() {
        super.logout()
        connectionsPreferences.connectionsToken(this).delete()
    }

    override suspend fun login(username: String, password: String) {
        // Not Needed
    }

    override val isLogged: Boolean
        get() = getToken().isNotBlank()

    override fun getToken(): String {
        return connectionsPreferences.connectionsToken(this).get()
    }

    private val json = Injekt.get<Json>()

    fun getAccounts(): List<DiscordAccount> {
        val accountsJson = connectionsPreferences.discordAccounts().get()
        return try {
            if (accountsJson.isNotBlank()) {
                json.decodeFromString<List<DiscordAccount>>(accountsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Discord accounts")
            emptyList()
        }
    }

    fun addAccount(account: DiscordAccount) {
        val accounts = getAccounts().toMutableList()

        if (account.isActive) {
            accounts.replaceAll { it.copy(isActive = false) }
            connectionsPreferences.connectionsToken(this).set(account.token)
        }

        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }

        saveAccounts(accounts)
    }

    fun removeAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.id == accountId }
        saveAccounts(accounts)
    }

    fun setActiveAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.replaceAll { it.copy(isActive = it.id == accountId) }
        saveAccounts(accounts)
        // Update active token (should restart RPC later)
        accounts.find { it.id == accountId }?.let { account ->
            connectionsPreferences.connectionsToken(this).set(account.token)
        }
    }

    fun restartRichPresence(context: android.content.Context) {
        // Direct restart via service intent
        DiscordRPCService.restart(context)
    }

    private fun saveAccounts(accounts: List<DiscordAccount>) {
        try {
            val accountsJson = json.encodeToString(accounts)
            connectionsPreferences.discordAccounts().set(accountsJson)
        } catch (e: Exception) {
            xLogE("Failed to save Discord accounts", e)
        }
    }
}
// <-- AM (DISCORD)
