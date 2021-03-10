package eu.kanade.tachiyomi.source.online.all

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.BrowseSourceFilterHeader
import eu.kanade.tachiyomi.source.online.FollowsSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.RandomMangaSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.md.MangaDexFabHeaderAdapter
import exh.md.handlers.ApiChapterParser
import exh.md.handlers.ApiMangaParser
import exh.md.handlers.FollowsHandler
import exh.md.handlers.MangaHandler
import exh.md.handlers.MangaPlusHandler
import exh.md.handlers.SimilarHandler
import exh.md.utils.FollowStatus
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.MangaDexDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import exh.widget.preference.MangadexLoginDialog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.EOFException
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.reflect.KClass

@Suppress("OverridingDeprecatedMember")
class MangaDex(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<MangaDexSearchMetadata, Response>,
    UrlImportableSource,
    FollowsSource,
    LoginSource,
    BrowseSourceFilterHeader,
    RandomMangaSource {
    override val lang: String = delegate.lang

    override val headers: Headers = super.headers.newBuilder().apply {
        add("X-Requested-With", "XMLHttpRequest")
        add("Referer", MdUtil.baseUrl)
    }.build()

    private val mdLang by lazy {
        MdLang.values().find { it.lang == lang }?.dexLang ?: lang
    }

    override val matchingHosts: List<String> = listOf("mangadex.org", "www.mangadex.org")

    val preferences: PreferencesHelper by injectLazy()
    val trackManager: TrackManager by injectLazy()

    private val sourcePreferences: SharedPreferences by lazy {
        context.getSharedPreferences("source_$id", 0x0000)
    }

    private fun useLowQualityThumbnail() = sourcePreferences.getInt(SHOW_THUMBNAIL_PREF, 0) == LOW_QUALITY

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            importIdToMdId(query) {
                super.fetchSearchManga(page, query, filters)
            }
        }

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        return if (lcFirstPathSegment == "title" || lcFirstPathSegment == "manga") {
            MdUtil.mapMdIdToMangaUrl(uri.pathSegments[1].toInt())
        } else {
            null
        }
    }

    override fun mapUrlToChapterUrl(uri: Uri): String? {
        if (!uri.pathSegments.firstOrNull().equals("chapter", true)) return null
        val id = uri.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return MdUtil.oldApiChapter + id
    }

    override suspend fun mapChapterUrlToMangaUrl(uri: Uri): String? {
        val id = uri.pathSegments.getOrNull(2) ?: return null
        val mangaId = MangaHandler(client, headers, mdLang).getMangaIdFromChapterId(id)
        return MdUtil.mapMdIdToMangaUrl(mangaId)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return MangaHandler(client, headers, mdLang, preferences.mangaDexForceLatestCovers().get()).fetchMangaDetailsObservable(manga)
    }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        return MangaHandler(client, headers, mdLang, preferences.mangaDexForceLatestCovers().get()).getMangaDetails(manga, id)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return MangaHandler(client, headers, mdLang, preferences.mangaDexForceLatestCovers().get()).fetchChapterListObservable(manga)
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        return MangaHandler(client, headers, mdLang, preferences.mangaDexForceLatestCovers().get()).getChapterList(manga)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return if (chapter.scanlator == "MangaPlus") {
            client.newCall(mangaPlusPageListRequest(chapter))
                .asObservableSuccess()
                .map { response ->
                    val chapterId = ApiChapterParser().externalParse(response)
                    MangaPlusHandler(client).fetchPageList(chapterId)
                }
        } else super.fetchPageList(chapter)
    }

    private fun mangaPlusPageListRequest(chapter: SChapter): Request {
        val urlChapterId = MdUtil.getChapterId(chapter.url)
        return GET(MdUtil.apiUrl + MdUtil.newApiChapter + urlChapterId + MdUtil.apiChapterSuffix, headers, CacheControl.FORCE_NETWORK)
    }

    override fun fetchImage(page: Page): Observable<Response> {
        return if (page.imageUrl!!.contains("mangaplus", true)) {
            MangaPlusHandler(network.client).client.newCall(GET(page.imageUrl!!, headers))
                .asObservableSuccess()
        } else super.fetchImage(page)
    }

    override val metaClass: KClass<MangaDexSearchMetadata> = MangaDexSearchMetadata::class

    override fun getDescriptionAdapter(controller: MangaController): MangaDexDescriptionAdapter {
        return MangaDexDescriptionAdapter(controller)
    }

    override fun parseIntoMetadata(metadata: MangaDexSearchMetadata, input: Response) {
        ApiMangaParser(mdLang).parseIntoMetadata(metadata, input, emptyList())
    }

    override suspend fun fetchFollows(): MangasPage {
        return FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).fetchFollows()
    }

    override val needsLogin: Boolean = true

    override fun getLoginDialog(source: Source, activity: Activity): DialogController {
        return MangadexLoginDialog(source as MangaDex)
    }

    override fun isLogged(): Boolean {
        val httpUrl = MdUtil.baseUrl.toHttpUrl()
        return trackManager.mdList.isLogged && network.cookieManager.get(httpUrl).any { it.name == REMEMBER_ME }
    }

    override suspend fun login(
        username: String,
        password: String,
        twoFactorCode: String
    ): Boolean {
        return withIOContext {
            val formBody = FormBody.Builder().apply {
                add("login_username", username)
                add("login_password", password)
                add("no_js", "1")
                add("remember_me", "1")
                add("two_factor", twoFactorCode)
            }

            client.newCall(
                POST(
                    "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=login",
                    headers,
                    formBody.build()
                )
            ).await()

            val response = client.newCall(GET(MdUtil.apiUrl + MdUtil.isLoggedInApi, headers)).await()

            withIOContext { response.body?.string() }.let { jsonData ->
                if (jsonData != null) {
                    MdUtil.jsonParser.decodeFromString<JsonObject>(jsonData)["code"]?.let { it as? JsonPrimitive }?.int == 200
                } else {
                    throw Exception("Json data was null")
                }
            }
        }
    }

    override suspend fun logout(): Boolean {
        return withIOContext {
            // https://mangadex.org/ajax/actions.ajax.php?function=logout
            val httpUrl = MdUtil.baseUrl.toHttpUrl()
            val listOfDexCookies = network.cookieManager.get(httpUrl)
            val cookie = listOfDexCookies.find { it.name == REMEMBER_ME }
            val token = cookie?.value
            if (token.isNullOrEmpty()) {
                return@withIOContext true
            }
            val result = client.newCall(
                POST("${MdUtil.baseUrl}/ajax/actions.ajax.php?function=logout", headers).newBuilder().addHeader(REMEMBER_ME, token).build()
            ).await()
            try {
                val resultStr = withIOContext { result.body?.string() }
                if (resultStr?.contains("success", true) == true) {
                    network.cookieManager.remove(httpUrl)
                    trackManager.mdList.logout()
                    return@withIOContext true
                }
            } catch (e: EOFException) {
                network.cookieManager.remove(httpUrl)
                trackManager.mdList.logout()
                return@withIOContext true
            }

            false
        }
    }

    override suspend fun fetchAllFollows(forceHd: Boolean): List<Pair<SManga, MangaDexSearchMetadata>> {
        return withIOContext { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).fetchAllFollows(forceHd) }
    }

    suspend fun updateReadingProgress(track: Track): Boolean {
        return withIOContext { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).updateReadingProgress(track) }
    }

    suspend fun updateRating(track: Track): Boolean {
        return withIOContext { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).updateRating(track) }
    }

    override suspend fun fetchTrackingInfo(url: String): Track {
        return withIOContext {
            if (!isLogged()) {
                throw Exception("Not Logged in")
            }
            FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).fetchTrackingInfo(url)
        }
    }

    suspend fun getTrackingAndMangaInfo(track: Track): Pair<Track, MangaDexSearchMetadata> {
        return MangaHandler(client, headers, lang).getTrackingInfo(track, useLowQualityThumbnail())
    }

    override suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean {
        return withIOContext { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).updateFollowStatus(mangaID, followStatus) }
    }

    override fun getFilterHeader(controller: BaseController<*>): MangaDexFabHeaderAdapter {
        return MangaDexFabHeaderAdapter(controller, this)
    }

    override suspend fun fetchRandomMangaUrl(): String {
        return withIOContext { MangaHandler(client, headers, mdLang).fetchRandomMangaId() }
    }

    fun fetchMangaSimilar(manga: Manga): Observable<MangasPage> {
        return SimilarHandler(preferences, useLowQualityThumbnail()).fetchSimilar(manga)
    }

    private fun importIdToMdId(query: String, fail: () -> Observable<MangasPage>): Observable<MangasPage> =
        when {
            query.toIntOrNull() != null -> {
                runAsObservable({
                    GalleryAdder().addGallery(context, MdUtil.baseUrl + MdUtil.mapMdIdToMangaUrl(query.toInt()), false, this@MangaDex)
                })
                    .map { res ->
                        MangasPage(
                            if (res is GalleryAddEvent.Success) {
                                listOf(res.manga)
                            } else {
                                emptyList()
                            },
                            false
                        )
                    }
            }
            else -> fail()
        }

    companion object {
        private const val REMEMBER_ME = "mangadex_rememberme_token"
        private const val SHOW_THUMBNAIL_PREF = "showThumbnailDefault"
        private const val LOW_QUALITY = 1
    }
}
