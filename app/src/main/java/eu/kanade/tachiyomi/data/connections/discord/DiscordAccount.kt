package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordAccount(
    val id: String,
    val username: String,
    @SerialName("avatar")
    val avatarId: String = "",
    val token: String = "",
    val isActive: Boolean = false,
) {
    val avatarUrl: String?
        get() = "https://cdn.discordapp.com/avatars/$id/$avatarId.png?size=512"
            .takeIf { avatarId.isNotBlank() }
}
