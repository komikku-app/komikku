package tachiyomi.data.release

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tachiyomi.domain.release.model.Release

/**
 * Contains information about the latest release from GitHub.
 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val version: String,
    @SerialName("body") val info: String,
    @SerialName("html_url") val releaseLink: String,
    @SerialName("assets") val assets: List<GitHubAssets>,
    // KMK -->
    @SerialName("prerelease") val preRelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false,
    // KMK <--
)

/**
 * Assets class containing download url.
 */
@Serializable
data class GitHubAssets(@SerialName("browser_download_url") val downloadLink: String)

/**
 * Regular expression that matches a mention to a valid GitHub username, like it's
 * done in GitHub Flavored Markdown. It follows these constraints:
 *
 * - Alphanumeric with single hyphens (no consecutive hyphens)
 * - Cannot begin or end with a hyphen
 * - Max length of 39 characters
 *
 * Convert '(@cuong-tran)' to '([@cuong-tran](https://github.com/cuong-tran))'
 *
 * Reference: https://stackoverflow.com/a/30281147
 */
val gitHubUsernameMentionRegex =
    """\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38}(?<=[a-z0-9]))""".toRegex(
        RegexOption.IGNORE_CASE,
    )

// KMK -->
/**
 * Convert from: https://github.com/komikku-app/komikku/compare/23d862d17...48fb4a2e6
 * to: [komikku-app/komikku@23d862d17...48fb4a2e6](https://github.com/komikku-app/komikku/compare/23d862d17...48fb4a2e6)
 */
val gitHubCommitsCompareRegex =
    """(\[[^]]+]\()?https://github.com/(?<owner>[^/]+)/(?<repo>[^/]+)/compare/(?<from>[0-9a-f.rv]+)\.\.\.(?<to>[0-9a-f.rv]+)\)?"""
        .toRegex(RegexOption.IGNORE_CASE)
// KMK <--

val releaseMapper: (GithubRelease) -> Release = {
    Release(
        it.version,
        it.info
            .replace(gitHubUsernameMentionRegex) { mention ->
                "[${mention.value}](https://github.com/${mention.value.substring(1)})"
            }
            // KMK -->
            .replace(gitHubCommitsCompareRegex) { matchResult ->
                val owner = matchResult.groups["owner"]!!.value
                val repo = matchResult.groups["repo"]!!.value
                val from = matchResult.groups["from"]!!.value
                val to = matchResult.groups["to"]!!.value

                "[$owner/$repo@$from...$to](https://github.com/$owner/$repo/compare/$from...$to)"
            },
        // KMK <--
        it.releaseLink,
        it.assets.map(GitHubAssets::downloadLink),
        // KMK -->
        preRelease = it.preRelease,
        draft = it.draft,
        // KMK <--
    )
}
