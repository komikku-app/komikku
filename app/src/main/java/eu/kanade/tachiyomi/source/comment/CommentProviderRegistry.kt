package eu.kanade.tachiyomi.source.comment

/**
 * Routes a source to its [CommentProvider] by package name and/or source id.
 * Providers are registered in construction order; first match wins.
 */
class CommentProviderRegistry(
    private val providers: List<CommentProvider> = emptyList(),
) {

    fun get(sourceId: Long, packageName: String?): CommentProvider? =
        providers.find { it.supports(sourceId, packageName) }

    fun exists(sourceId: Long, packageName: String?): Boolean =
        get(sourceId, packageName) != null
}
