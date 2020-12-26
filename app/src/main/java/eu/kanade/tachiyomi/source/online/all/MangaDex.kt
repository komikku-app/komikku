package eu.kanade.tachiyomi.source.online.all

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.bluelinelabs.conductor.Controller
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
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.asObservable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.reflect.KClass

class MangaDex(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<MangaDexSearchMetadata, Response>,
    UrlImportableSource,
    FollowsSource,
    LoginSource,
    BrowseSourceFilterHeader,
    RandomMangaSource {
    override val lang: String = delegate.lang

    override val headers: Headers
        get() = super.headers.newBuilder().apply {
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
        val id = uri.pathSegments.getOrNull(1) ?: return null
        return MdUtil.apiChapter + id
    }

    override suspend fun mapChapterUrlToMangaUrl(uri: Uri): String? {
        val id = uri.pathSegments.getOrNull(2) ?: return null
        val mangaId = MangaHandler(client, headers, listOf(mdLang)).getMangaIdFromChapterId(id)
        return MdUtil.mapMdIdToMangaUrl(mangaId)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return MangaHandler(client, headers, listOf(mdLang), preferences.mangaDexForceLatestCovers().get()).fetchMangaDetailsObservable(manga)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return MangaHandler(client, headers, listOf(mdLang), preferences.mangaDexForceLatestCovers().get()).fetchChapterListObservable(manga)
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
        val chpUrl = chapter.url.substringBefore(MdUtil.apiChapterSuffix)
        return GET(MdUtil.baseUrl + chpUrl + MdUtil.apiChapterSuffix, headers, CacheControl.FORCE_NETWORK)
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
        ApiMangaParser(listOf(mdLang)).parseIntoMetadata(metadata, input, preferences.mangaDexForceLatestCovers().get())
    }

    override suspend fun fetchFollows(): MangasPage {
        return FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).fetchFollows()
    }

    override val needsLogin: Boolean = true

    override fun getLoginDialog(source: Source, activity: Activity): DialogController {
        return MangadexLoginDialog(source as MangaDex)
    }

    override fun isLogged(): Boolean {
        val httpUrl = MdUtil.baseUrl.toHttpUrlOrNull()!!
        return trackManager.mdList.isLogged && network.cookieManager.get(httpUrl).any { it.name == REMEMBER_ME }
    }

    override suspend fun login(
        username: String,
        password: String,
        twoFactorCode: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val formBody = FormBody.Builder()
                .add("login_username", username)
                .add("login_password", password)
                .add("no_js", "1")
                .add("remember_me", "1")

            twoFactorCode.let {
                formBody.add("two_factor", it)
            }

            val response = client.newCall(
                POST(
                    "${MdUtil.baseUrl}/ajax/actions.ajax.php?function=login",
                    headers,
                    formBody.build()
                )
            ).await()

            response.body!!.string().let {
                if (it.isEmpty()) {
                    true
                } else {
                    val error = HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    throw Exception(error)
                }
            }
        }
    }

    override suspend fun logout(): Boolean {
        return withContext(Dispatchers.IO) {
            // https://mangadex.org/ajax/actions.ajax.php?function=logout
            val httpUrl = MdUtil.baseUrl.toHttpUrlOrNull()!!
            val listOfDexCookies = network.cookieManager.get(httpUrl)
            val cookie = listOfDexCookies.find { it.name == REMEMBER_ME }
            val token = cookie?.value
            if (token.isNullOrEmpty()) {
                return@withContext true
            }
            val result = client.newCall(
                POST("${MdUtil.baseUrl}/ajax/actions.ajax.php?function=logout", headers).newBuilder().addHeader(REMEMBER_ME, token).build()
            ).execute()
            val resultStr = result.body!!.string()
            if (resultStr.contains("success", true)) {
                network.cookieManager.remove(httpUrl)
                trackManager.mdList.logout()
                return@withContext true
            }

            false
        }
    }

    override suspend fun fetchAllFollows(forceHd: Boolean): List<Pair<SManga, MangaDexSearchMetadata>> {
        return withContext(Dispatchers.IO) { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).fetchAllFollows(forceHd) }
    }

    suspend fun updateReadingProgress(track: Track): Boolean {
        return withContext(Dispatchers.IO) { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).updateReadingProgress(track) }
    }

    suspend fun updateRating(track: Track): Boolean {
        return withContext(Dispatchers.IO) { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).updateRating(track) }
    }

    override suspend fun fetchTrackingInfo(url: String): Track {
        return withContext(Dispatchers.IO) {
            if (!isLogged()) {
                throw Exception("Not Logged in")
            }
            FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).fetchTrackingInfo(url)
        }
    }

    override suspend fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Boolean {
        return withContext(Dispatchers.IO) { FollowsHandler(client, headers, Injekt.get(), useLowQualityThumbnail()).updateFollowStatus(mangaID, followStatus) }
    }

    override fun getFilterHeader(controller: Controller): MangaDexFabHeaderAdapter {
        return MangaDexFabHeaderAdapter(controller, this)
    }

    override suspend fun fetchRandomMangaUrl(): String {
        return MangaHandler(client, headers, listOf(mdLang)).fetchRandomMangaId()
    }

    fun fetchMangaSimilar(manga: Manga): Observable<MangasPage> {
        return SimilarHandler(preferences, useLowQualityThumbnail()).fetchSimilar(manga)
    }

    private fun importIdToMdId(query: String, fail: () -> Observable<MangasPage>): Observable<MangasPage> =
        when {
            query.toIntOrNull() != null -> {
                flow {
                    emit(GalleryAdder().addGallery(context, MdUtil.baseUrl + MdUtil.mapMdIdToMangaUrl(query.toInt()), false, this@MangaDex))
                }
                    .asObservable()
                    .map { res ->
                        MangasPage(
                            (
                                if (res is GalleryAddEvent.Success) {
                                    listOf(res.manga)
                                } else {
                                    emptyList()
                                }
                                ),
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
