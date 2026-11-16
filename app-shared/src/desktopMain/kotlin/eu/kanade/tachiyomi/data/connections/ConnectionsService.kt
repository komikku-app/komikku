package eu.kanade.tachiyomi.data.connections

import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * KMK: desktop counterpart of the Android connections service type (Discord rich presence on Android) for
 * the borrowed settings model / `ConnectionPreferenceWidget`. No connection exists on desktop yet, so
 * nothing subclasses it; only the members those files use are declared.
 */
abstract class ConnectionsService(val id: Long) {
    abstract fun nameStrRes(): StringResource

    abstract fun getLogo(): Int

    abstract fun getLogoColor(): Int

    open val isLogged: Boolean get() = false

    open val isLoggedInFlow: Flow<Boolean> get() = flowOf(false)
}
