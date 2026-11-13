package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.util.system.toast // KMK --> // KMK <--
import kotlinx.coroutines.launch

class TrackLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val data = when {
            !uri.encodedQuery.isNullOrBlank() -> uri.encodedQuery
            !uri.encodedFragment.isNullOrBlank() -> uri.encodedFragment
            else -> null
        }
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.associate {
                val parts = it.split("=", limit = 2).map<String, String>(Uri::decode)
                parts[0] to parts.getOrNull(1)
            }
            .orEmpty()

        lifecycleScope.launch {
            try {
                when (uri.host) {
                    // KMK -->
                    "mangabaka-auth" -> handleMangaBaka(data["code"], data["state"])
                    // KMK <--
                    "anilist-auth" -> handleAniList(data["access_token"])
                    "bangumi-auth" -> handleBangumi(data["code"])
                    "myanimelist-auth" -> handleMyAnimeList(data["code"])
                    "shikimori-auth" -> handleShikimori(data["code"])
                }
            } finally {
                returnToSettings()
            }
        }
    }

    // KMK -->
    private suspend fun handleMangaBaka(code: String?, state: String?) {
        // Ignore malformed or unsolicited callbacks instead of signing the user out.
        if (code.isNullOrBlank() || state.isNullOrBlank()) return
        try {
            trackerManager.mangaBaka.loginWithOAuth(code, state)
        } catch (e: Exception) {
            toast(e.message)
        }
    }
    // KMK <--

    private suspend fun handleAniList(accessToken: String?) {
        if (accessToken != null) {
            trackerManager.aniList.login(accessToken)
        } else {
            trackerManager.aniList.logout()
        }
    }

    private suspend fun handleBangumi(code: String?) {
        if (code != null) {
            trackerManager.bangumi.login(code)
        } else {
            trackerManager.bangumi.logout()
        }
    }

    private suspend fun handleMyAnimeList(code: String?) {
        if (code != null) {
            trackerManager.myAnimeList.login(code)
        } else {
            trackerManager.myAnimeList.logout()
        }
    }

    private suspend fun handleShikimori(code: String?) {
        if (code != null) {
            trackerManager.shikimori.login(code)
        } else {
            trackerManager.shikimori.logout()
        }
    }
}
