package app.komikku.desktop.track

import app.komikku.shared.track.OAuthRedirect
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import exh.log.xLogE
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * KMK: how each tracker logs in on desktop (Android: `SettingsTrackingScreen` + `TrackLoginActivity`).
 *
 * The OAuth trackers' registered redirect URIs are app-scheme URIs (`komikku://<tracker>-auth`, MDList
 * `tachiyomisy://mangadex-auth`; a loopback listener would need other redirect URIs registered with each service). The
 * login opens the authorize page in the system browser and waits for the redirect: the packaged app registers both
 * schemes with the OS (macOS: the bundle's `CFBundleURLTypes`; Windows / Linux: their installers), which hands the
 * address to the running app ([OAuthRedirectInbox]). Pasting the redirected URL (or just the code / token) into the
 * dialog stays as the fallback, e.g. when the app runs from Gradle without a registration. [OAuthRedirect] extracts the
 * same parameter Android's `TrackLoginActivity` reads.
 */
sealed interface TrackerLoginMethod {
    /** Username + password (Kitsu, MangaUpdates, and in-test trackers). */
    data object Credentials : TrackerLoginMethod

    /**
     * Browser authorization + the redirect to `[scheme]://[host]`, received ([OAuthRedirectInbox]) or pasted; the
     * credential is its [parameter]. Each [authUrl] call starts a new login (MyAnimeList and MDList store a new PKCE
     * verifier), so call it once per login.
     */
    data class OAuthPaste(
        val authUrl: () -> String,
        val host: String,
        val parameter: String,
        val scheme: String = "komikku",
    ) : TrackerLoginMethod

    /** Komga / Kavita / Suwayomi: logged in by the matching installed source (Android: `loginNoop`). */
    data object Enhanced : TrackerLoginMethod

    /** Not used by the borrowed trackers any more (MDList logs in through [OAuthPaste] since P4); kept for tracker types without a desktop login. */
    data object Unsupported : TrackerLoginMethod
}

object DesktopTrackerLogin {

    fun method(tracker: Tracker): TrackerLoginMethod = when (tracker) {
        // Each authorization URL stores a new PKCE verifier for the code exchange in the tracker's login
        is MyAnimeList -> TrackerLoginMethod.OAuthPaste(
            { MyAnimeListApi.authUrl(Injekt.get<TrackPreferences>(), tracker).toString() },
            "myanimelist-auth",
            "code",
        )
        is Anilist -> TrackerLoginMethod.OAuthPaste({ AnilistApi.authUrl().toString() }, "anilist-auth", "access_token")
        is Shikimori -> TrackerLoginMethod.OAuthPaste({ ShikimoriApi.authUrl().toString() }, "shikimori-auth", "code")
        is Bangumi -> TrackerLoginMethod.OAuthPaste({ BangumiApi.authUrl().toString() }, "bangumi-auth", "code")
        // Android: Settings → MangaDex → login (MangaDexLoginActivity), through the enabled MangaDex source (P4)
        is MdList -> TrackerLoginMethod.OAuthPaste(
            { MdConstants.Login.authUrl(MdUtil.newLoginCodeVerifier(Injekt.get<TrackPreferences>(), tracker)) },
            "mangadex-auth",
            "code",
            MdConstants.Login.redirectUri.substringBefore("://"),
        )
        is EnhancedTracker -> TrackerLoginMethod.Enhanced
        else -> TrackerLoginMethod.Credentials
    }

    /**
     * The credential to log in with from [pasted] for [method], or an error message key: `null` when the
     * redirect belongs to another tracker or carries no value.
     */
    fun credentialFromRedirect(method: TrackerLoginMethod.OAuthPaste, pasted: String): String? {
        val redirect = OAuthRedirect.parse(pasted)
        if (redirect.host != null && !redirect.host.equals(method.host, ignoreCase = true)) return null
        return redirect.value(method.parameter)
    }

    /** OAuth trackers' `login(username, password)` takes the code / token as password (Android does the same). */
    suspend fun loginWithRedirect(tracker: Tracker, method: TrackerLoginMethod.OAuthPaste, pasted: String): Boolean {
        val credential = credentialFromRedirect(method, pasted) ?: return false
        if (tracker is MdList) {
            // Android's MangaDexLoginActivity: the code goes to the enabled MangaDex (delegated) source, which stores
            // the MDList token; without one there is nothing to log in with
            val mangaDex = MdUtil.getEnabledMangaDex() ?: return false
            return mangaDex.login(credential) && tracker.isLoggedIn
        }
        tracker.login("", credential)
        return tracker.isLoggedIn
    }

    /**
     * Whether [tracker] has what it logs in through: an enhanced tracker a source it accepts ([enhancedAvailable]), MDList
     * an enabled MangaDex source (installed extension, language and source enabled, delegated sources on), which its
     * login and sync go through. Other trackers need nothing.
     */
    fun sourceAvailable(tracker: Tracker, sourceManager: SourceManager, sourcePreferences: SourcePreferences): Boolean = when (tracker) {
        is MdList -> MdUtil.getEnabledMangaDex(sourcePreferences, sourceManager) != null
        is EnhancedTracker -> enhancedAvailable(tracker, sourceManager)
        else -> true
    }

    /**
     * Logs out of [tracker]. MDList like Android's MangaDex settings (`MangaDexLoginHelper.logout` through the enabled
     * MangaDex source: revokes the session at MangaDex), and in any case also locally: the tracker's
     * `MangaDexAuthInterceptor` keeps the token in memory, and a later request or token refresh would send or write it
     * back after only the preference was cleared (no MangaDex source, MangaDex unreachable).
     */
    suspend fun logout(tracker: Tracker, sourceManager: SourceManager, sourcePreferences: SourcePreferences) {
        if (tracker !is MdList) {
            tracker.logout()
            return
        }
        try {
            MdUtil.getEnabledMangaDex(sourcePreferences, sourceManager)?.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xLogE("MDList: could not revoke the MangaDex session", e)
        }
        tracker.interceptor.setAuth(null)
        tracker.logout()
    }

    /** Android: an enhanced tracker can be enabled when a source it accepts is installed. */
    fun enhancedAvailable(tracker: Tracker, sourceManager: SourceManager): Boolean {
        val accepted = (tracker as? EnhancedTracker)?.getAcceptedSources() ?: return false
        return sourceManager.getAll().any { it::class.qualifiedName in accepted }
    }
}
