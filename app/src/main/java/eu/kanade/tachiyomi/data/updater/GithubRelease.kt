package eu.kanade.tachiyomi.data.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains information about the latest release from GitHub.
 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val version: String,
    @SerialName("body") val info: String,
    @SerialName("html_url") val releaseLink: String,
    @SerialName("assets") private val assets: List<Assets>,
) {

    /**
     * Get download link of latest release from the assets.
     * @return download link of latest release.
     */
    fun getDownloadLink(): String =
        assets[0].downloadLink

    /**
     * Assets class containing download url.
     */
    @Serializable
    data class Assets(@SerialName("browser_download_url") val downloadLink: String)
}
