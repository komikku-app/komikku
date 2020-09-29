package eu.kanade.tachiyomi.source

import android.content.Context
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.PervEden
import eu.kanade.tachiyomi.source.online.english.EightMuses
import eu.kanade.tachiyomi.source.online.english.HBrowse
import eu.kanade.tachiyomi.source.online.english.HentaiCafe
import eu.kanade.tachiyomi.source.online.english.Pururin
import eu.kanade.tachiyomi.source.online.english.Tsumino
import exh.EH_SOURCE_ID
import exh.EIGHTMUSES_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.HBROWSE_SOURCE_ID
import exh.HENTAI_CAFE_SOURCE_ID
import exh.PERV_EDEN_EN_SOURCE_ID
import exh.PERV_EDEN_IT_SOURCE_ID
import exh.PURURIN_SOURCE_ID
import exh.TSUMINO_SOURCE_ID
import exh.source.BlacklistedSources
import exh.source.DelegatedHttpSource
import exh.source.EnhancedHttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.reflect.KClass

open class SourceManager(private val context: Context) {

    private val sourcesMap = mutableMapOf<Long, Source>()

    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    // SY -->
    private val prefs: PreferencesHelper by injectLazy()

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    // SY <--

    init {
        createInternalSources().forEach { registerSource(it) }

        // SY -->
        // Create internal sources
        createEHSources().forEach { registerSource(it) }

        // Watch the preference and manage Exhentai
        prefs.enableExhentai().asFlow()
            .drop(1)
            .onEach {
                if (it) {
                    registerSource(EHentai(EXH_SOURCE_ID, true, context))
                } else {
                    sourcesMap.remove(EXH_SOURCE_ID)
                }
            }.launchIn(scope)

        registerSource(MergedSource())
        // SY <--
    }

    open fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            StubSource(sourceKey)
        }
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getVisibleOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>().filter {
        it.id !in BlacklistedSources.HIDDEN_SOURCES
    }

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    // SY -->
    fun getVisibleCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>().filter {
        it.id !in BlacklistedSources.HIDDEN_SOURCES
    }

    fun getDelegatedCatalogueSources() = sourcesMap.values.filterIsInstance<EnhancedHttpSource>().mapNotNull { enhancedHttpSource ->
        enhancedHttpSource.enhancedSource as? DelegatedHttpSource
    }
    // SY <--

    internal fun registerSource(source: Source, overwrite: Boolean = false) {
        // EXH -->
        val sourceQName = source::class.qualifiedName
        val factories = DELEGATED_SOURCES.entries.filter { it.value.factory }.map { it.value.originalSourceQualifiedClassName }
        val delegate = if (sourceQName != null) {
            val matched = factories.find { sourceQName.startsWith(it) }
            if (matched != null) {
                DELEGATED_SOURCES[matched]
            } else DELEGATED_SOURCES[sourceQName]
        } else null
        val newSource = if (source is HttpSource && delegate != null) {
            XLog.d("[EXH] Delegating source: %s -> %s!", sourceQName, delegate.newSourceClass.qualifiedName)
            val enhancedSource = EnhancedHttpSource(
                source,
                delegate.newSourceClass.constructors.find { it.parameters.size == 2 }!!.call(source, context)
            )
            val map = listOf(DelegatedSource(enhancedSource.originalSource.name, enhancedSource.originalSource.id, enhancedSource.originalSource::class.qualifiedName ?: delegate.originalSourceQualifiedClassName, (enhancedSource.enhancedSource as DelegatedHttpSource)::class, delegate.factory)).associateBy { it.sourceId }
            currentDelegatedSources.plusAssign(map)
            enhancedSource
        } else source

        if (source.id in BlacklistedSources.BLACKLISTED_EXT_SOURCES) {
            XLog.d("[EXH] Removing blacklisted source: (id: %s, name: %s, lang: %s)!", source.id, source.name, (source as? CatalogueSource)?.lang)
            return
        }
        // EXH <--

        if (overwrite || !sourcesMap.containsKey(source.id)) {
            sourcesMap[source.id] = newSource
        }
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
    }

    private fun createInternalSources(): List<Source> = listOf(
        LocalSource(context)
    )

    // SY -->
    private fun createEHSources(): List<Source> {
        val exSrcs = mutableListOf<HttpSource>(
            EHentai(EH_SOURCE_ID, false, context)
        )
        if (prefs.enableExhentai().get()) {
            exSrcs += EHentai(EXH_SOURCE_ID, true, context)
        }
        return exSrcs
    }
    // SY <--

    private inner class StubSource(override val id: Long) : Source {

        override val name: String
            get() = id.toString()

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            return name
        }

        private fun getSourceNotInstalledException(): Exception {
            return Exception(context.getString(R.string.source_not_installed, id.toString()))
        }
    }

    // SY -->
    companion object {
        private const val fillInSourceId = Long.MAX_VALUE
        val DELEGATED_SOURCES = listOf(
            DelegatedSource(
                "Hentai Cafe",
                HENTAI_CAFE_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.all.foolslide.HentaiCafe",
                HentaiCafe::class
            ),
            DelegatedSource(
                "Pururin",
                PURURIN_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.pururin.Pururin",
                Pururin::class
            ),
            DelegatedSource(
                "Tsumino",
                TSUMINO_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.tsumino.Tsumino",
                Tsumino::class
            ),
            DelegatedSource(
                "MangaDex",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.mangadex",
                MangaDex::class,
                true
            ),
            DelegatedSource(
                "HBrowse",
                HBROWSE_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.hbrowse.HBrowse",
                HBrowse::class
            ),
            DelegatedSource(
                "8Muses",
                EIGHTMUSES_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.all.eromuse.EroMuse",
                EightMuses::class
            ),
            DelegatedSource(
                "Hitomi",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.hitomi.Hitomi",
                Hitomi::class,
                true
            ),
            DelegatedSource(
                "PervEden English",
                PERV_EDEN_EN_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.perveden.Perveden",
                PervEden::class
            ),
            DelegatedSource(
                "PervEden Italian",
                PERV_EDEN_IT_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.it.perveden.Perveden",
                PervEden::class
            ),
            DelegatedSource(
                "NHentai",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.nhentai.NHentai",
                NHentai::class,
                true
            )
        ).associateBy { it.originalSourceQualifiedClassName }

        var currentDelegatedSources = mutableMapOf<Long, DelegatedSource>()

        data class DelegatedSource(
            val sourceName: String,
            val sourceId: Long,
            val originalSourceQualifiedClassName: String,
            val newSourceClass: KClass<out DelegatedHttpSource>,
            val factory: Boolean = false
        )
    }
    // SY <--
}
