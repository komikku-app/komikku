package eu.kanade.tachiyomi.source.online.all

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.md.MangaDexFabHeaderAdapter
import exh.md.handlers.ApiChapterParser
import exh.md.handlers.ApiMangaParser
import exh.md.handlers.FollowsHandler
import exh.md.handlers.MangaHandler
import exh.md.handlers.MangaPlusHandler
import exh.md.utils.FollowStatus
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.MangaDexDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import exh.widget.preference.MangadexLoginDialog
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        urlImportFetchSearchManga(context, query) {
            importIdToMdId(query) {
                super.fetchSearchManga(page, query, filters)
            }
        }

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        return if (lcFirstPathSegment == "title" || lcFirstPathSegment == "manga") {
            MdUtil.mapMdIdToMangaUrl(uri.pathSegments[1].toInt())
        } else {
            null
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return MangaHandler(client, headers, listOf(mdLang), preferences.mangaDexForceLatestCovers().get()).fetchMangaDetailsObservable(manga)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return MangaHandler(client, headers, listOf(mdLang), preferences.mangaDexForceLatestCovers().get()).fetchChapterListObservable(manga)
    }

    @ExperimentalSerializationApi
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

    override fun fetchFollows(): Observable<MangasPage> {
        return FollowsHandler(client, headers, Injekt.get()).fetchFollows()
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
            ).execute()
            response.body!!.string().isEmpty()
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

    override fun fetchAllFollows(forceHd: Boolean): Flow<List<Pair<SManga, MangaDexSearchMetadata>>> {
        return flow { emit(FollowsHandler(client, headers, Injekt.get()).fetchAllFollows(forceHd)) }
    }

    fun updateReadingProgress(track: Track): Flow<Boolean> {
        return flow { FollowsHandler(client, headers, Injekt.get()).updateReadingProgress(track) }
    }

    fun updateRating(track: Track): Flow<Boolean> {
        return flow { FollowsHandler(client, headers, Injekt.get()).updateRating(track) }
    }

    override fun fetchTrackingInfo(url: String): Flow<Track> {
        return flow {
            if (!isLogged()) {
                throw Exception("Not Logged in")
            }
            emit(FollowsHandler(client, headers, Injekt.get()).fetchTrackingInfo(url))
        }
    }

    override fun updateFollowStatus(mangaID: String, followStatus: FollowStatus): Flow<Boolean> {
        return flow { emit(FollowsHandler(client, headers, Injekt.get()).updateFollowStatus(mangaID, followStatus)) }
    }

    override fun getFilterHeader(controller: Controller): MangaDexFabHeaderAdapter {
        return MangaDexFabHeaderAdapter(controller, this)
    }

    override fun fetchRandomMangaUrl(): Flow<String> {
        return MangaHandler(client, headers, listOf(mdLang)).fetchRandomMangaId()
    }

    private fun importIdToMdId(query: String, fail: () -> Observable<MangasPage>): Observable<MangasPage> =
        when {
            query.toIntOrNull() != null -> {
                Observable.fromCallable {
                    // MdUtil.
                    val res = GalleryAdder().addGallery(context, MdUtil.baseUrl + MdUtil.mapMdIdToMangaUrl(query.toInt()), false, this)
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
    }
}
