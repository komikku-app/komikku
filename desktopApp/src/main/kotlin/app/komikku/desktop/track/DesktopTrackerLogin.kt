package app.komikku.desktop.track

import app.komikku.shared.track.OAuthRedirect
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
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import tachiyomi.domain.source.service.SourceManager

/**
 * KMK: how each tracker logs in on desktop (Android: `SettingsTrackingScreen` + `TrackLoginActivity`).
 *
 * The OAuth trackers' registered redirect URIs are app-scheme URIs (`komikku://<tracker>-auth`), which a
 * desktop app cannot receive (no OS scheme registration, and a loopback listener would need different
 * redirect URIs registered with each service, which is out of scope). So the login opens the authorize
 * page in the system browser and the user pastes the URL the browser was redirected to (or just the code /
 * token) into a dialog; [OAuthRedirect] extracts the same parameter Android's `TrackLoginActivity` reads.
 */
sealed interface TrackerLoginMethod {
    /** Username + password (Kitsu, MangaUpdates, and in-test trackers). */
    data object Credentials : TrackerLoginMethod

    /** Browser authorization + pasted redirect: [parameter] of the redirect to `komikku://[host]`. */
    data class OAuthPaste(val authUrl: () -> String, val host: String, val parameter: String) : TrackerLoginMethod

    /** Komga / Kavita / Suwayomi: logged in by the matching installed source (Android: `loginNoop`). */
    data object Enhanced : TrackerLoginMethod

    /** Not used by the borrowed trackers any more (MDList logs in through [OAuthPaste] since P4); kept for tracker types without a desktop login. */
    data object Unsupported : TrackerLoginMethod
}

object DesktopTrackerLogin {

    fun method(tracker: Tracker): TrackerLoginMethod = when (tracker) {
        is MyAnimeList -> TrackerLoginMethod.OAuthPaste({ MyAnimeListApi.authUrl().toString() }, "myanimelist-auth", "code")
        is Anilist -> TrackerLoginMethod.OAuthPaste({ AnilistApi.authUrl().toString() }, "anilist-auth", "access_token")
        is Shikimori -> TrackerLoginMethod.OAuthPaste({ ShikimoriApi.authUrl().toString() }, "shikimori-auth", "code")
        is Bangumi -> TrackerLoginMethod.OAuthPaste({ BangumiApi.authUrl().toString() }, "bangumi-auth", "code")
        // Android: Settings → MangaDex → login (MangaDexLoginActivity), through the enabled MangaDex source (P4)
        is MdList -> TrackerLoginMethod.OAuthPaste({ MdConstants.Login.authUrl(MdUtil.getPkceChallengeCode()) }, "mangadex-auth", "code")
        is EnhancedTracker -> TrackerLoginMethod.Enhanced
        else -> TrackerLoginMethod.Credentials
    }

    /**
     * The credential to log in with from [pasted] for [method], or an error message key: `null` when the
     * redirect belongs to another tracker or carries no value.
     */
    fun credentialFromRedirect(method: TrackerLoginMethod.OAuthPaste, pasted: String): String? {
        val redirect = OAuthRedirect.parse(pasted)
        if (redirect.host != null && redirect.host != method.host) return null
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

    /** Android: an enhanced tracker can be enabled when a source it accepts is installed. */
    fun enhancedAvailable(tracker: Tracker, sourceManager: SourceManager): Boolean {
        val accepted = (tracker as? EnhancedTracker)?.getAcceptedSources() ?: return false
        return sourceManager.getAll().any { it::class.qualifiedName in accepted }
    }
}
