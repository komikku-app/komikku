package app.komikku.desktop.source

import android.content.Context
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.Lanraragi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.Pururin
import eu.kanade.tachiyomi.source.online.english.EightMuses
import exh.log.xLogD
import exh.log.xLogE
import exh.source.DelegatedHttpSource
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.EnhancedHttpSource
import exh.source.LIBRARY_UPDATE_EXCLUDED_SOURCES
import exh.source.PURURIN_SOURCE_ID
import exh.source.lanraragiSourceIds
import exh.source.mangaDexSourceIds
import exh.source.metadataDelegatedSourceIds
import exh.source.nHentaiSourceIds
import kotlinx.coroutines.CancellationException

/**
 * KMK: the desktop's SY delegated (enhanced) sources, Android's `AndroidSourceManager.toInternalSource` /
 * `DELEGATED_SOURCES` for the delegates borrowed into `:app-shared` (`desktopCopy`, unchanged Android code): an
 * installed extension source whose class is one of [delegates] is wrapped in an [EnhancedHttpSource] over the
 * borrowed delegate (metadata, namespace tags, URL import, page previews where the delegate has them), with the same
 * id, name and language, so library entries, backups and the "use the enhanced source" setting
 * (`DelegateSourcePreferences.delegateSources()`) behave as on Android.
 *
 * MangaDex works without an account; its MDList login (Android's `MangaDexLoginActivity`) is the desktop's Settings →
 * Tracking OAuth paste (`DesktopTrackerLogin`). Not on desktop yet: MangaDex's settings, follows and similar screens,
 * SY's built-in E-Hentai / ExHentai and merged sources.
 */
object DesktopDelegatedSources {

    class Delegate(
        val sourceName: String,
        /** The extension source's class; with [factory] only the package (subclasses in it match), like Android. */
        val originalSourceQualifiedClassName: String,
        val factory: Boolean = false,
        val create: (HttpSource, Context) -> DelegatedHttpSource,
    )

    /** Android's `AndroidSourceManager.DELEGATED_SOURCES` (same class names and order). */
    val delegates: List<Delegate> = listOf(
        Delegate("Pururin", "eu.kanade.tachiyomi.extension.all.pururin.Pururin") { source, context -> Pururin(source, context) },
        Delegate("MangaDex", "eu.kanade.tachiyomi.extension.all.mangadex.MangaDex") { source, context -> MangaDex(source, context) },
        Delegate("8Muses", "eu.kanade.tachiyomi.extension.en.eightmuses.EightMuses") { source, context -> EightMuses(source, context) },
        Delegate("NHentai", "eu.kanade.tachiyomi.extension.all.nhentai.NHentai") { source, context -> NHentai(source, context) },
        Delegate("LANraragi", "eu.kanade.tachiyomi.extension.all.lanraragi.LANraragi") { source, context -> Lanraragi(source, context) },
    )

    /** The delegate for [source]'s class, if any. */
    fun delegateFor(source: Source): Delegate? {
        val name = source::class.qualifiedName ?: return null
        return delegates.firstOrNull { it.matches(name) }
    }

    private fun Delegate.matches(name: String) =
        name == originalSourceQualifiedClassName || (factory && name.startsWith(originalSourceQualifiedClassName))

    /**
     * [source] wrapped over its delegate when it has one (Android's `toInternalSource`), otherwise [source] itself.
     * [delegates] is for tests.
     */
    fun wrap(source: Source, context: Context, delegates: List<Delegate> = this.delegates): Source {
        if (source !is HttpSource) return source
        return try {
            val name = source::class.qualifiedName ?: return source
            val delegate = delegates.firstOrNull { it.matches(name) } ?: return source
            xLogD("Delegating source: %s -> %s!", name, delegate.sourceName)
            EnhancedHttpSource(source, delegate.create(source, context))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Not guarded on Android; here a delegate that cannot start keeps the plain extension source usable. Errors
            // too: borrowed Android code can fail with NoClassDefFoundError / ExceptionInInitializerError on desktop
            xLogE("Could not delegate ${source::class.qualifiedName}", e)
            source
        }
    }

    /**
     * Android's `handleSourceLibrary` (run when `AndroidSourceManager.currentDelegatedSources` changes): publishes the
     * ids of the delegated sources in [sources] to the shared SY lookups (`exh.source`: metadata sources for the library
     * search by tags, NHentai / MangaDex / LANraragi ids, the sources library updates skip). Recomputed from the current
     * sources (Android's map only grows during a run).
     */
    fun publishSourceIds(sources: Collection<Source>) {
        val delegated = sources.filterIsInstance<EnhancedHttpSource>()
        fun idsOf(predicate: (HttpSource) -> Boolean) = delegated.filter { predicate(it.enhancedSource) }.map { it.originalSource.id }.sorted()
        metadataDelegatedSourceIds = idsOf { it is Pururin || it is EightMuses || it is NHentai || it is Lanraragi }
        nHentaiSourceIds = idsOf { it is NHentai }
        mangaDexSourceIds = idsOf { it is MangaDex }
        lanraragiSourceIds = idsOf { it is Lanraragi }
        LIBRARY_UPDATE_EXCLUDED_SOURCES = listOf(EH_SOURCE_ID, EXH_SOURCE_ID, PURURIN_SOURCE_ID) + nHentaiSourceIds
    }
}
