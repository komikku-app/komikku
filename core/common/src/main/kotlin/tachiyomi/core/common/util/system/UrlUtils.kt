package tachiyomi.core.common.util.system

/**
 * Utility functions for URL handling and validation
 */
object UrlUtils {

    /**
     * Detects if a URL string represents an online resource (not local storage)
     *
     * @param url The URL string to check
     * @return true if the URL is online, false if it's local storage or invalid
     */
    fun isOnlineUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val trimmedUrl = url.trim()

        // Check for common online URL schemes
        return when {
            // Standard HTTP/HTTPS URLs
            trimmedUrl.startsWith("http://", ignoreCase = true) -> true
            trimmedUrl.startsWith("https://", ignoreCase = true) -> true

            // FTP URLs (less common but still online)
            trimmedUrl.startsWith("ftp://", ignoreCase = true) -> true
            trimmedUrl.startsWith("ftps://", ignoreCase = true) -> true

            // If none of the above patterns match, assume it's not a valid local URL
            else -> false
        }
    }

    /**
     * Detects if a URL string represents a local storage resource
     *
     * @param url The URL string to check
     * @return true if the URL is local storage, false if it's online or invalid
     */
    fun isLocalUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val trimmedUrl = url.trim()

        // Check for local storage URL schemes
        return when {
            // Local file schemes
            trimmedUrl.startsWith("file://", ignoreCase = true) -> true
            trimmedUrl.startsWith("content://", ignoreCase = true) -> true
            trimmedUrl.startsWith("android_asset://", ignoreCase = true) -> true

            // Intent URLs (Android specific)
            trimmedUrl.startsWith("intent://", ignoreCase = true) -> true

            // Local paths (absolute or relative)
            trimmedUrl.startsWith("/") -> true
            trimmedUrl.startsWith("./") -> true
            trimmedUrl.startsWith("../") -> true

            // If none of the above patterns match, assume it's not a valid embedded URL
            else -> false
        }
    }

    /**
     * Detects if a URL string represents an embedded resource (such as blob or data URLs)
     *
     * @param url The URL string to check
     * @return true if the URL is an embedded resource, false otherwise
     */
    fun isEmbeddedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val trimmedUrl = url.trim()

        // Check for embedded URL schemes
        return when {
            // Blob URLs (typically local/temporary)
            trimmedUrl.startsWith("blob:", ignoreCase = true) -> true

            // Data URLs (embedded data)
            trimmedUrl.startsWith("data:", ignoreCase = true) -> true

            // If none of the above patterns match, assume it's not a valid online URL
            else -> false
        }
    }

    /**
     * Gets the scheme (protocol) from a URL string
     *
     * @param url The URL string
     * @return The scheme part of the URL, or null if not found
     */
    fun getUrlScheme(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // Find the first colon, which separates the scheme from the rest
        val colonIndex = url.indexOf(':')
        if (colonIndex <= 0) return null

        // Optionally, check if the scheme is followed by '//' or not
        return url.substring(0, colonIndex)
        val schemeIndex = url.indexOf("://")
        return if (schemeIndex > 0) {
            url.substring(0, schemeIndex).lowercase()
        } else {
            null
        }
    }

    /**
     * Checks if a URL is a valid web URL (HTTP or HTTPS)
     *
     * @param url The URL string to check
     * @return true if it's a valid web URL, false otherwise
     */
    fun isWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val trimmedUrl = url.trim()
        return trimmedUrl.startsWith("http://", ignoreCase = true) ||
            trimmedUrl.startsWith("https://", ignoreCase = true)
    }
}
