// AM (CONNECTIONS) -->
package eu.kanade.domain.connection.service

import eu.kanade.tachiyomi.data.connection.BaseConnection
import tachiyomi.core.common.preference.PreferenceStore

class ConnectionPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun connectionUsername(connection: BaseConnection) = preferenceStore.getString(
        connectionUsername(connection.id),
        "",
    )

    fun connectionPassword(connection: BaseConnection) = preferenceStore.getString(
        connectionPassword(connection.id),
        "",
    )

    fun setConnectionsCredentials(connection: BaseConnection, username: String, password: String) {
        connectionUsername(connection).set(username)
        connectionPassword(connection).set(password)
    }

    fun connectionsToken(connection: BaseConnection) = preferenceStore.getString(connectionsToken(connection.id), "")

    fun enableDiscordRPC() = preferenceStore.getBoolean("pref_enable_discord_rpc", false)

    fun discordRPCStatus() = preferenceStore.getInt("pref_discord_rpc_status", 1)

    fun discordRPCIncognito() = preferenceStore.getBoolean("pref_discord_rpc_incognito", false)

    fun discordRPCIncognitoCategories() = preferenceStore.getStringSet("discord_rpc_incognito_categories", emptySet())

    fun useChapterTitles() = preferenceStore.getBoolean("pref_discord_rpc_use_chapter_titles", false)

    companion object {

        fun connectionUsername(connectionId: Long) = "pref_connections_username_$connectionId"

        private fun connectionPassword(connectionId: Long) = "pref_connections_password_$connectionId"

        private fun connectionsToken(connectionId: Long) = "connection_token_$connectionId"
    }
}
// <-- AM (CONNECTIONS)
