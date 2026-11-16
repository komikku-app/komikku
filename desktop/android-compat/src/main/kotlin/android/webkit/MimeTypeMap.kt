package android.webkit

/**
 * android-compat: `android.webkit.MimeTypeMap` with a built-in table of the types Komikku and
 * sources deal with (images, archives, documents, web and media files). Lookups are case-sensitive
 * like on Android (callers lowercase extensions themselves).
 */
class MimeTypeMap private constructor() {

    fun hasMimeType(mimeType: String?): Boolean = mimeType != null && mimeType in extensionByMime

    fun getMimeTypeFromExtension(extension: String?): String? =
        extension?.takeIf { it.isNotEmpty() }?.let(mimeByExtension::get)

    fun hasExtension(extension: String?): Boolean = extension != null && extension in mimeByExtension

    fun getExtensionFromMimeType(mimeType: String?): String? =
        mimeType?.takeIf { it.isNotEmpty() }?.let(extensionByMime::get)

    companion object {
        private val defaultInstance = MimeTypeMap()

        // The first extension listed for a type is the one `getExtensionFromMimeType` returns.
        private val TYPES = listOf(
            "image/jpeg" to listOf("jpg", "jpeg", "jpe"),
            "image/png" to listOf("png"),
            "image/gif" to listOf("gif"),
            "image/webp" to listOf("webp"),
            "image/avif" to listOf("avif"),
            "image/heif" to listOf("heif"),
            "image/heic" to listOf("heic"),
            "image/jxl" to listOf("jxl"),
            "image/bmp" to listOf("bmp"),
            "image/svg+xml" to listOf("svg", "svgz"),
            "image/x-icon" to listOf("ico"),
            "image/tiff" to listOf("tiff", "tif"),
            "application/zip" to listOf("zip"),
            "application/vnd.comicbook+zip" to listOf("cbz"),
            "application/vnd.rar" to listOf("rar"),
            "application/x-rar-compressed" to listOf("rar"),
            "application/vnd.comicbook-rar" to listOf("cbr"),
            "application/x-7z-compressed" to listOf("7z"),
            "application/x-cb7" to listOf("cb7"),
            "application/x-tar" to listOf("tar"),
            "application/x-cbt" to listOf("cbt"),
            "application/gzip" to listOf("gz"),
            "application/epub+zip" to listOf("epub"),
            "application/pdf" to listOf("pdf"),
            "application/json" to listOf("json"),
            "application/javascript" to listOf("js", "mjs"),
            "application/xml" to listOf("xml"),
            "application/vnd.android.package-archive" to listOf("apk"),
            "application/java-archive" to listOf("jar"),
            "application/octet-stream" to listOf("bin"),
            "text/plain" to listOf("txt", "text", "log"),
            "text/html" to listOf("html", "htm"),
            "text/css" to listOf("css"),
            "text/csv" to listOf("csv"),
            "audio/mpeg" to listOf("mp3"),
            "audio/ogg" to listOf("ogg", "oga"),
            "video/mp4" to listOf("mp4", "m4v"),
            "video/webm" to listOf("webm"),
            "video/x-matroska" to listOf("mkv"),
            "font/ttf" to listOf("ttf"),
            "font/otf" to listOf("otf"),
            "font/woff" to listOf("woff"),
            "font/woff2" to listOf("woff2"),
        )

        private val mimeByExtension: Map<String, String> = buildMap {
            TYPES.forEach { (mime, extensions) -> extensions.forEach { putIfAbsent(it, mime) } }
        }

        private val extensionByMime: Map<String, String> = TYPES.associate { (mime, extensions) -> mime to extensions.first() }

        private val VALID_FILE_NAME = Regex("[a-zA-Z_0-9.\\-()%]+")

        @JvmStatic
        fun getSingleton(): MimeTypeMap = defaultInstance

        /**
         * Extension of the last path segment of [url] (query and fragment removed), or "" if the file
         * name contains characters outside `[a-zA-Z_0-9.-()%]` or has no extension.
         */
        @JvmStatic
        fun getFileExtensionFromUrl(url: String?): String {
            if (url.isNullOrEmpty()) return ""
            val fileName = url
                .substringBefore('#')
                .substringBefore('?')
                .substringAfterLast('/')
            if (fileName.isEmpty() || !VALID_FILE_NAME.matches(fileName)) return ""
            val dot = fileName.lastIndexOf('.')
            return if (dot >= 0) fileName.substring(dot + 1) else ""
        }
    }
}
