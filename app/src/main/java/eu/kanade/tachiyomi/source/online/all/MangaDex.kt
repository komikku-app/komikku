package eu.kanade.tachiyomi.source.online.all

import android.net.Uri
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import exh.source.DelegatedHttpSource

class MangaDex(delegate: HttpSource) :
    DelegatedHttpSource(delegate),
    ConfigurableSource,
    UrlImportableSource {

    override val matchingHosts: List<String> = listOf("mangadex.org")

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        return if (lcFirstPathSegment == "title" || lcFirstPathSegment == "manga") {
            "/manga/${uri.pathSegments[1]}"
        } else {
            null
        }
    }

    override val lang: String get() = delegate.lang

    override fun setupPreferenceScreen(screen: PreferenceScreen) = (delegate as ConfigurableSource).setupPreferenceScreen(screen)
}
