// AM (CONNECTIONS) -->
package eu.kanade.tachiyomi.data.connections

import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.kanade.domain.connection.service.ConnectionPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

abstract class ConnectionsService(val id: Long) {

    val connectionPreferences: ConnectionPreferences by injectLazy()
    private val networkService: NetworkHelper by injectLazy()

    open val client: OkHttpClient
        get() = networkService.client

    // Name of the connection service to display
    @StringRes
    abstract fun nameRes(): Int

    @DrawableRes
    abstract fun getLogo(): Int

    @ColorInt
    abstract fun getLogoColor(): Int

    @CallSuper
    open fun logout() {
        connectionPreferences.setConnectionsCredentials(this, "", "")
        connectionPreferences.connectionsToken(this).set("")
    }

    abstract suspend fun login(username: String, password: String)

    fun getUsername() = connectionPreferences.connectionUsername(this).get()

    fun getPassword() = connectionPreferences.connectionPassword(this).get()

    fun saveCredentials(username: String, password: String) {
        connectionPreferences.setConnectionsCredentials(this, username, password)
    }

    open val isLogged: Boolean
        get() = getUsername().isNotEmpty() &&
            getPassword().isNotEmpty()
}
// <-- AM (CONNECTIONS)
