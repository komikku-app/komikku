// AM (CONNECTIONS) -->
package eu.kanade.domain.connection.service

import eu.kanade.tachiyomi.data.connections.ConnectionsService
import tachiyomi.core.common.preference.PreferenceStore

class ConnectionPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun connectionUsername(connection: ConnectionsService) = preferenceStore.getString(
        connectionUsername(connection.id),
        "",
    )

    fun connectionPassword(connection: ConnectionsService) = preferenceStore.getString(
        connectionPassword(connection.id),
        "",
    )

    fun setConnectionsCredentials(connection: ConnectionsService, username: String, password: String) {
        connectionUsername(connection).set(username)
        connectionPassword(connection).set(password)
    }

    fun connectionsToken(connection: ConnectionsService) = preferenceStore.getString(connectionsToken(connection.id), "")

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
