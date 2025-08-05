// AM (CONNECTIONS) -->
package eu.kanade.tachiyomi.data.connection

import eu.kanade.tachiyomi.data.connection.discord.Discord

class ConnectionManager {

    companion object {
        const val DISCORD = 201L
    }

    val discord = Discord(DISCORD)

    val services = listOf(discord)

    fun getService(id: Long) = services.find { it.id == id }
}
// <-- AM (CONNECTIONS)
