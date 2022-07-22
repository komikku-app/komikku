package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toChapterInfo
import eu.kanade.tachiyomi.source.online.BrowseSourceFilterHeader
import eu.kanade.tachiyomi.source.online.FollowsSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.RandomMangaSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.util.lang.runAsObservable
import exh.md.MangaDexFabHeaderAdapter
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
import exh.md.handlers.PageHandler
import exh.md.handlers.SimilarHandler
import exh.md.network.MangaDexLoginHelper
import exh.md.network.TokenAuthenticator
import exh.md.service.MangaDexAuthService
import exh.md.service.MangaDexService
import exh.md.service.SimilarService
import exh.md.utils.FollowStatus
import exh.md.utils.MdApi
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.MangaDexDescription
import okhttp3.OkHttpClient
import okhttp3.Response
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.reflect.KClass

@Suppress("OverridingDeprecatedMember")
class MangaDex(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<MangaDexSearchMetadata, Triple<MangaDto, List<String>, StatisticsMangaDto>>,
    UrlImportableSource,
    FollowsSource,
    LoginSource,
    BrowseSourceFilterHeader,
    RandomMangaSource,
    NamespaceSource {
    override val lang: String = delegate.lang

    private val mdLang by lazy {
        MdLang.fromExt(lang) ?: MdLang.ENGLISH
    }

    override val matchingHosts: List<String> = listOf("mangadex.org", "www.mangadex.org")

    val preferences = Injekt.get<PreferencesHelper>()
    val mdList: MdList = Injekt.get<TrackManager>().mdList

    private val sourcePreferences: SharedPreferences by lazy {
        context.getSharedPreferences("source_$id", 0x0000)
    }

    private val mangadexAuthServiceLazy = lazy { MangaDexAuthService(baseHttpClient, headers, preferences, mdList) }

    private val loginHelper = MangaDexLoginHelper(mangadexAuthServiceLazy, preferences, mdList)

    override val baseHttpClient: OkHttpClient = delegate.client.newBuilder()
        .authenticator(
            TokenAuthenticator(loginHelper),
        )
        .build()

    private fun dataSaver() = sourcePreferences.getBoolean(getDataSaverPreferenceKey(mdLang.lang), false)
    private fun usePort443Only() = sourcePreferences.getBoolean(getStandardHttpsPreferenceKey(mdLang.lang), false)
    private fun blockedGroups() = sourcePreferences.getString(getBlockedGroupsPrefKey(mdLang.lang), "").orEmpty()
    private fun blockedUploaders() = sourcePreferences.getString(getBlockedUploaderPrefKey(mdLang.lang), "").orEmpty()

    private val mangadexService by lazy {
        MangaDexService(client)
    }
    private val mangadexAuthService by mangadexAuthServiceLazy
    private val similarService by lazy {
        SimilarService(client)
    }
    private val apiMangaParser by lazy {
        ApiMangaParser(mdLang.lang)
    }
    private val followsHandler by lazy {
        FollowsHandler(mdLang.lang, mangadexAuthService)
    }
    private val mangaHandler by lazy {
        MangaHandler(mdLang.lang, mangadexService, apiMangaParser, followsHandler)
    }
    private val similarHandler by lazy {
        SimilarHandler(mdLang.lang, mangadexService, similarService)
    }
    private val mangaPlusHandler by lazy {
        MangaPlusHandler(network.client)
    }
    private val comikeyHandler by lazy {
        ComikeyHandler(network.cloudflareClient, network.defaultUserAgent)
    }
    private val bilibiliHandler by lazy {
        BilibiliHandler(network.cloudflareClient)
    }
    private val azukHandler by lazy {
        AzukiHandler(network.client, network.defaultUserAgent)
    }
    private val mangaHotHandler by lazy {
        MangaHotHandler(network.client, network.defaultUserAgent)
    }
    private val pageHandler by lazy {
        PageHandler(
            headers,
            mangadexService,
            mangaPlusHandler,
            comikeyHandler,
            bilibiliHandler,
            azukHandler,
            mangaHotHandler,
            preferences,
            mdList,
        )
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

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return mangaHandler.fetchMangaDetailsObservable(manga, id)
    }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        return mangaHandler.getMangaDetails(manga, id)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return mangaHandler.fetchChapterListObservable(manga, blockedGroups(), blockedUploaders())
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return mangaHandler.getChapterList(manga, blockedGroups(), blockedUploaders())
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return runAsObservable { pageHandler.fetchPageList(chapter.toChapterInfo(), isLogged(), usePort443Only(), dataSaver(), delegate) }
    }

    override fun fetchImage(page: Page): Observable<Response> {
        return pageHandler.fetchImage(page) {
            super.fetchImage(it)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return pageHandler.fetchImageUrl(page) {
            super.fetchImageUrl(it)
        }
    }

    // MetadataSource methods
    override val metaClass: KClass<MangaDexSearchMetadata> = MangaDexSearchMetadata::class

    @Composable
    override fun DescriptionComposable(state: MangaScreenState.Success, openMetadataViewer: () -> Unit, search: (String) -> Unit) {
        MangaDexDescription(state, openMetadataViewer)
    }

    override suspend fun parseIntoMetadata(metadata: MangaDexSearchMetadata, input: Triple<MangaDto, List<String>, StatisticsMangaDto>) {
        apiMangaParser.parseIntoMetadata(metadata, input.first, input.second, input.third)
    }

    // LoginSource methods
    override val requiresLogin: Boolean = false

    override val twoFactorAuth = LoginSource.AuthSupport.NOT_SUPPORTED

    override fun isLogged(): Boolean {
        return mdList.isLogged
    }

    override fun getUsername(): String {
        return mdList.getUsername()
    }

    override fun getPassword(): String {
        return mdList.getPassword()
    }

    override suspend fun login(
        username: String,
        password: String,
        twoFactorCode: String?,
    ): Boolean {
        val result = loginHelper.login(username, password)
        return if (result) {
            mdList.saveCredentials(username, password)
            true
        } else false
    }

    override suspend fun logout(): Boolean {
        loginHelper.logout()
        mdList.logout()
        return true
    }

    // FollowsSource methods
    override suspend fun fetchFollows(page: Int): MangasPage {
        return followsHandler.fetchFollows(page)
    }

    override suspend fun fetchAllFollows(): List<Pair<SManga, MangaDexSearchMetadata>> {
        return followsHandler.fetchAllFollows()
    }

    override suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean {
        return followsHandler.updateFollowStatus(mangaID, followStatus)
    }

    override suspend fun fetchTrackingInfo(url: String): Track {
        return followsHandler.fetchTrackingInfo(url)
    }

    // Tracker methods
    /*suspend fun updateReadingProgress(track: Track): Boolean {
        return followsHandler.updateReadingProgress(track)
    }*/

    suspend fun updateRating(track: Track): Boolean {
        return followsHandler.updateRating(track)
    }

    suspend fun getTrackingAndMangaInfo(track: Track): Pair<Track, MangaDexSearchMetadata?> {
        return mangaHandler.getTrackingInfo(track)
    }

    // BrowseSourceFilterHeader method
    override fun getFilterHeader(controller: BaseController<*>, onClick: () -> Unit): MangaDexFabHeaderAdapter {
        return MangaDexFabHeaderAdapter(controller, this, onClick)
    }

    // RandomMangaSource method
    override suspend fun fetchRandomMangaUrl(): String {
        return mangaHandler.fetchRandomMangaId()
    }

    suspend fun getMangaSimilar(manga: MangaInfo): MetadataMangasPage {
        return similarHandler.getSimilar(manga)
    }

    suspend fun getMangaRelated(manga: MangaInfo): MetadataMangasPage {
        return similarHandler.getRelated(manga)
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
    }
}
