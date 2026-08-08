@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.FollowsSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.RandomMangaSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import exh.md.dto.MangaDto
import exh.md.dto.StatisticsMangaDto
import exh.md.handlers.ApiMangaParser
import exh.md.handlers.AzukiHandler
import exh.md.handlers.BilibiliHandler
import exh.md.handlers.ComikeyHandler
import exh.md.handlers.FollowsHandler
import exh.md.handlers.MangaHandler
import exh.md.handlers.MangaHotHandler
import exh.md.handlers.MangaPlusHandler
import exh.md.handlers.NamicomiHandler
import exh.md.handlers.PageHandler
import exh.md.network.MangaDexLoginHelper
import exh.md.service.MangaDexAuthService
import exh.md.service.MangaDexService
import exh.md.utils.FollowStatus
import exh.md.utils.MdApi
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.source.DelegatedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Response
import rx.Observable
import tachiyomi.core.common.util.lang.runAsObservable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible

@Suppress("OverridingDeprecatedMember")
class MangaDex(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<MangaDexSearchMetadata, Triple<MangaDto, List<String>, StatisticsMangaDto>>,
    UrlImportableSource,
    FollowsSource,
    LoginSource,
    RandomMangaSource,
    NamespaceSource {
    override val lang: String = delegate.lang

    private val mdLang by lazy {
        MdLang.fromExt(lang) ?: MdLang.ENGLISH
    }

    override val matchingHosts: List<String> = listOf("mangadex.org", "www.mangadex.org")

    val trackPreferences: TrackPreferences by injectLazy()
    val mdList: MdList by lazy { Injekt.get<TrackerManager>().mdList }

    private val sourcePreferences: SharedPreferences by lazy {
        context.getSharedPreferences("source_$id", 0x0000)
    }

    private val loginHelper = MangaDexLoginHelper(network.client, headers, trackPreferences, mdList, mdList.interceptor)

    override val baseHttpClient: OkHttpClient = delegate.client.newBuilder()
        .addInterceptor(mdList.interceptor)
        .build()

    private fun dataSaver() = sourcePreferences.getBoolean(getDataSaverPreferenceKey(mdLang.lang), false)
    private fun usePort443Only() = sourcePreferences.getBoolean(getStandardHttpsPreferenceKey(mdLang.lang), false)
    private fun blockedGroups() = sourcePreferences.getString(getBlockedGroupsPrefKey(mdLang.lang), "").orEmpty()
    private fun blockedUploaders() = sourcePreferences.getString(getBlockedUploaderPrefKey(mdLang.lang), "").orEmpty()
    private fun coverQuality() = sourcePreferences.getString(getCoverQualityPrefKey(mdLang.lang), "").orEmpty()
    private fun tryUsingFirstVolumeCover() = sourcePreferences.getBoolean(getTryUsingFirstVolumeCoverKey(mdLang.lang), false)
    private fun altTitlesInDesc() = sourcePreferences.getBoolean(getAltTitlesInDescKey(mdLang.lang), false)
    private fun finalChapterInDesc() = sourcePreferences.getBoolean(getFinalChapterInDescPrefKey(mdLang.lang), false)
    private fun preferExtensionLangTitle() = sourcePreferences.getBoolean(getPreferExtensionLangTitlePrefKey(mdLang.extLang), true)

    private val mangadexService by lazy {
        MangaDexService(client, headers)
    }
    private val mangadexAuthService by lazy {
        MangaDexAuthService(baseHttpClient, headers)
    }
    private val apiMangaParser by lazy {
        ApiMangaParser(mdLang.lang)
    }
    private val followsHandler by lazy {
        FollowsHandler(mdLang.lang, mangadexAuthService)
    }
    private val mangaHandler by lazy {
        MangaHandler(mdLang.lang, mangadexService, apiMangaParser)
    }
    private val mangaPlusHandler by lazy {
        MangaPlusHandler(network.client)
    }
    private val comikeyHandler by lazy {
        ComikeyHandler(network.client, network.defaultUserAgentProvider())
    }
    private val bilibiliHandler by lazy {
        BilibiliHandler(network.client)
    }
    private val azukiHandler by lazy {
        AzukiHandler(network.client, network.defaultUserAgentProvider())
    }
    private val mangaHotHandler by lazy {
        MangaHotHandler(network.client, network.defaultUserAgentProvider())
    }
    private val namicomiHandler by lazy {
        NamicomiHandler(network.client, network.defaultUserAgentProvider())
    }
    private val pageHandler by lazy {
        PageHandler(
            headers,
            mangadexService,
            mangaPlusHandler,
            comikeyHandler,
            bilibiliHandler,
            azukiHandler,
            mangaHotHandler,
            namicomiHandler,
        )
    }

    /**
     * Signature:
     *
     * ```kotlin
     * @Suppress(names = ["unused"])
     * public final suspend fun komikkuGetSearchManga(
     *     page: Int,
     *     query: String,
     *     filters: FilterList
     * ): MangasPage
     * ```
     */
    private val komikkuGetSearchManga by lazy {
        delegate::class.memberFunctions.find { it.name == "komikkuGetSearchManga" }
    }

    /**
     * Signature:
     *
     * ```kotlin
     * @Suppress(names = ["unused"])
     * public final suspend fun komikkuGetLatestUpdates(
     *     page: Int
     * ): MangasPage
     * ```
     */
    private val komikkuGetLatestUpdates by lazy {
        delegate::class.memberFunctions.find { it.name == "komikkuGetLatestUpdates" }
    }

    private val tokenTracker: HashMap<String, Long>? by lazy {
        val helperProperty = delegate::class.declaredMemberProperties.find { it.name == "helper" }
            ?: delegate::class.superclasses.asSequence().flatMap { it.declaredMemberProperties }.find { it.name == "helper" }
            ?: return@lazy null
        helperProperty.isAccessible = true
        val helper = helperProperty.call(delegate) ?: return@lazy null

        val tokenTrackerProperty = helper::class.declaredMemberProperties.find { it.name == "tokenTracker" } ?: return@lazy null
        tokenTrackerProperty.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        tokenTrackerProperty.call(helper) as? HashMap<String, Long>
    }

    // UrlImportableSource methods
    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.lowercase() ?: return null

        return if (lcFirstPathSegment == "title" || lcFirstPathSegment == "manga") {
            MdUtil.buildMangaUrl(uri.pathSegments[1])
        } else {
            null
        }
    }

    override fun mapUrlToChapterUrl(uri: Uri): String? {
        if (!uri.pathSegments.firstOrNull().equals("chapter", true)) return null
        val id = uri.pathSegments.getOrNull(1) ?: return null
        return MdApi.chapter + '/' + id
    }

    override suspend fun mapChapterUrlToMangaUrl(uri: Uri): String? {
        val id = uri.pathSegments.getOrNull(1) ?: return null
        return mangaHandler.getMangaFromChapterId(id)?.let { MdUtil.buildMangaUrl(it) }
    }

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getSearchManga"))
    @Suppress("DEPRECATION")
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return komikkuGetSearchManga?.let {
            runAsObservable {
                val result = it.callSuspend(delegate, page, query, filters)
                result as? MangasPage
                    ?: throw Exception("komikkuGetSearchManga returned $result instead of a MangasPage instance")
            }
        } ?: delegate.fetchSearchManga(page, query, filters)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return komikkuGetSearchManga?.let {
            val result = it.callSuspend(delegate, page, query, filters)
            result as? MangasPage
                ?: throw Exception("komikkuGetSearchManga returned $result instead of a MangasPage instance")
        } ?: delegate.getSearchManga(page, query, filters)
    }

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    @Suppress("DEPRECATION")
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        komikkuGetLatestUpdates?.let {
            return runAsObservable {
                val result = it.callSuspend(delegate, page)
                result as? MangasPage
                    ?: throw Exception("komikkuGetLatestUpdates returned $result instead of a MangasPage instance")
            }
        }
        val request = delegate.latestUpdatesRequest(page)
        val url = request.url.newBuilder()
            .removeAllQueryParameters("includeFutureUpdates")
            .build()
        return client.newCall(request.newBuilder().url(url).build())
            .asObservableSuccess()
            .map { response ->
                delegate.latestUpdatesParse(response)
            }
    }

    @Suppress("DEPRECATION")
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        komikkuGetLatestUpdates?.let {
            val result = it.callSuspend(delegate, page)
            return result as? MangasPage
                ?: throw Exception("komikkuGetLatestUpdates returned $result instead of a MangasPage instance")
        }
        val request = delegate.latestUpdatesRequest(page)
        val url = request.url.newBuilder()
            .removeAllQueryParameters("includeFutureUpdates")
            .build()

        val response = client.newCall(request.newBuilder().url(url).build()).awaitSuccess()
        return delegate.latestUpdatesParse(response)
    }

    // KMK -->
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val asyncManga = if (fetchDetails) getMangaDetails(manga) else null
        val asyncChapters = if (fetchChapters) getChapterList(manga) else null
        return SMangaUpdate(asyncManga ?: manga, asyncChapters ?: chapters)
    }
    // KMK <--

    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return mangaHandler.fetchMangaDetailsObservable(
            manga,
            id,
            coverQuality(),
            tryUsingFirstVolumeCover(),
            altTitlesInDesc(),
            finalChapterInDesc(),
            preferExtensionLangTitle(),
        )
    }

    internal suspend fun getMangaDetails(manga: SManga): SManga {
        return mangaHandler.getMangaDetails(
            manga,
            id,
            coverQuality(),
            tryUsingFirstVolumeCover(),
            altTitlesInDesc(),
            finalChapterInDesc(),
            preferExtensionLangTitle(),
        )
    }

    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return mangaHandler.fetchChapterListObservable(manga, blockedGroups(), blockedUploaders())
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        return mangaHandler.getChapterList(manga, blockedGroups(), blockedUploaders())
    }

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return runAsObservable { pageHandler.fetchPageList(chapter, usePort443Only(), dataSaver(), tokenTracker) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        return pageHandler.fetchPageList(chapter, usePort443Only(), dataSaver(), tokenTracker)
    }

    override suspend fun getImage(page: Page): Response {
        val call = pageHandler.getImageCall(page)
        return call?.awaitSuccess() ?: super.getImage(page)
    }

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getImageUrl"))
    override fun fetchImageUrl(page: Page): Observable<String> {
        return pageHandler.fetchImageUrl(page) {
            @Suppress("DEPRECATION")
            super.fetchImageUrl(it)
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        return pageHandler.getImageUrl(page) {
            super.getImageUrl(page)
        }
    }

    // MetadataSource methods
    override val metaClass: KClass<MangaDexSearchMetadata> = MangaDexSearchMetadata::class

    override fun newMetaInstance() = MangaDexSearchMetadata()

    override suspend fun parseIntoMetadata(
        metadata: MangaDexSearchMetadata,
        input: Triple<MangaDto, List<String>, StatisticsMangaDto>,
    ) {
        apiMangaParser.parseIntoMetadata(
            metadata,
            input.first,
            input.second,
            input.third,
            null,
            coverQuality(),
            altTitlesInDesc(),
            finalChapterInDesc(),
            preferExtensionLangTitle(),
        )
    }

    // LoginSource methods
    override val requiresLogin: Boolean = false

    override val twoFactorAuth = LoginSource.AuthSupport.NOT_SUPPORTED

    override fun isLogged(): Boolean {
        return mdList.isLoggedIn
    }

    override fun getUsername(): String {
        return mdList.getUsername()
    }

    override fun getPassword(): String {
        return mdList.getPassword()
    }

    override suspend fun login(authCode: String): Boolean {
        return loginHelper.login(authCode)
    }

    override suspend fun logout(): Boolean {
        return loginHelper.logout()
    }

    // FollowsSource methods
    override suspend fun fetchFollows(page: Int): MangasPage {
        return followsHandler.fetchFollows(page)
    }

    override suspend fun fetchAllFollows(): List<Pair<SManga, MangaDexSearchMetadata>> {
        return followsHandler.fetchAllFollows()
    }

    suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean {
        return followsHandler.updateFollowStatus(mangaID, followStatus)
    }

    suspend fun fetchTrackingInfo(url: String): Track {
        return followsHandler.fetchTrackingInfo(url)
    }

    // Tracker methods
    /*suspend fun updateReadingProgress(track: Track): Boolean {
        return followsHandler.updateReadingProgress(track)
    }*/

    suspend fun updateRating(track: Track): Boolean {
        return followsHandler.updateRating(track)
    }

    // RandomMangaSource method
    override suspend fun fetchRandomMangaUrl(): String {
        return mangaHandler.fetchRandomMangaId()
    }

    suspend fun getMangaMetadata(track: Track): SManga {
        return mangaHandler.getMangaMetadata(
            track,
            id,
            coverQuality(),
            tryUsingFirstVolumeCover(),
            altTitlesInDesc(),
            finalChapterInDesc(),
            preferExtensionLangTitle(),
        )
    }

    companion object {
        private const val dataSaverPref = "dataSaverV5"
        fun getDataSaverPreferenceKey(dexLang: String): String {
            return "${dataSaverPref}_$dexLang"
        }

        private const val standardHttpsPortPref = "usePort443"
        fun getStandardHttpsPreferenceKey(dexLang: String): String {
            return "${standardHttpsPortPref}_$dexLang"
        }

        private const val blockedGroupsPref = "blockedGroups"
        fun getBlockedGroupsPrefKey(dexLang: String): String {
            return "${blockedGroupsPref}_$dexLang"
        }

        private const val blockedUploaderPref = "blockedUploader"
        fun getBlockedUploaderPrefKey(dexLang: String): String {
            return "${blockedUploaderPref}_$dexLang"
        }

        private const val coverQualityPref = "thumbnailQuality"
        fun getCoverQualityPrefKey(dexLang: String): String {
            return "${coverQualityPref}_$dexLang"
        }

        private const val tryUsingFirstVolumeCoverPref = "tryUsingFirstVolumeCover"
        fun getTryUsingFirstVolumeCoverKey(dexLang: String): String {
            return "${tryUsingFirstVolumeCoverPref}_$dexLang"
        }

        private const val altTitlesInDescPref = "altTitlesInDesc"
        fun getAltTitlesInDescKey(dexLang: String): String {
            return "${altTitlesInDescPref}_$dexLang"
        }

        private const val finalChapterInDescPref = "finalChapterInDesc"
        fun getFinalChapterInDescPrefKey(dexLang: String): String {
            return "${finalChapterInDescPref}_$dexLang"
        }

        private const val preferExtensionLangTitlePref = "preferExtensionLangTitle"
        fun getPreferExtensionLangTitlePrefKey(dexLang: String): String {
            return "${preferExtensionLangTitlePref}_$dexLang"
        }
    }
}
