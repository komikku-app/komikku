package app.komikku.desktop.source

import android.content.Context
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.Lanraragi
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.Pururin
import eu.kanade.tachiyomi.source.online.english.EightMuses
import exh.log.xLogD
import exh.log.xLogE
import exh.source.DelegatedHttpSource
import exh.source.EnhancedHttpSource

/**
 * KMK: the desktop's SY delegated (enhanced) sources, Android's `AndroidSourceManager.toInternalSource` /
 * `DELEGATED_SOURCES` for the delegates borrowed into `:app-shared` (`desktopCopy`, unchanged Android code): an
 * installed extension source whose class is one of [delegates] is wrapped in an [EnhancedHttpSource] over the
 * borrowed delegate (metadata, namespace tags, URL import, page previews where the delegate has them), with the same
 * id, name and language, so library entries, backups and the "use the enhanced source" setting
 * (`DelegateSourcePreferences.delegateSources()`) behave as on Android.
 *
 * Not on desktop (yet): the MangaDex delegate (it needs the MDList tracker with its login activity, which is a stub on
 * desktop, and the follows / similar screens), and SY's built-in E-Hentai / ExHentai and merged sources.
 */
object DesktopDelegatedSources {

    class Delegate(
        val sourceName: String,
        /** The extension source's class; with [factory] only the package (subclasses in it match), like Android. */
        val originalSourceQualifiedClassName: String,
        val factory: Boolean = false,
        val create: (HttpSource, Context) -> DelegatedHttpSource,
    )

    /** Android's `AndroidSourceManager.DELEGATED_SOURCES` without MangaDex (same class names and order). */
    val delegates: List<Delegate> = listOf(
        Delegate("Pururin", "eu.kanade.tachiyomi.extension.all.pururin.Pururin") { source, context -> Pururin(source, context) },
        Delegate("8Muses", "eu.kanade.tachiyomi.extension.en.eightmuses.EightMuses") { source, context -> EightMuses(source, context) },
        Delegate("NHentai", "eu.kanade.tachiyomi.extension.all.nhentai.NHentai") { source, context -> NHentai(source, context) },
        Delegate("LANraragi", "eu.kanade.tachiyomi.extension.all.lanraragi.LANraragi") { source, context -> Lanraragi(source, context) },
    )

    /** The delegate for [source]'s class, if any. */
    fun delegateFor(source: Source): Delegate? {
        val name = source::class.qualifiedName ?: return null
        return delegates.firstOrNull {
            name == it.originalSourceQualifiedClassName || (it.factory && name.startsWith(it.originalSourceQualifiedClassName))
        }
    }

    /** [source] wrapped over its delegate when it has one (Android's `toInternalSource`), otherwise [source] itself. */
    fun wrap(source: Source, context: Context): Source {
        if (source !is HttpSource) return source
        val delegate = delegateFor(source) ?: return source
        xLogD("Delegating source: %s -> %s!", source::class.qualifiedName, delegate.sourceName)
        return try {
            EnhancedHttpSource(source, delegate.create(source, context))
        } catch (e: Exception) {
            // Not guarded on Android; here a delegate that cannot start keeps the plain extension source usable
            xLogE("Could not delegate ${source::class.qualifiedName}", e)
            source
        }
    }
}
