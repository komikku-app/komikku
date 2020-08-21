package eu.kanade.tachiyomi.source.online.all

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.md.handlers.serializers.ErrorResult
import exh.md.handlers.serializers.Language
import exh.md.handlers.serializers.MangaPlusResponse
import exh.md.handlers.serializers.MangaPlusSerializer
import exh.md.handlers.serializers.Popup
import exh.md.handlers.serializers.Title
import exh.source.DelegatedHttpSource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@ExperimentalSerializationApi
class MangaPlus(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    ConfigurableSource {
    override val lang = delegate.lang

    private val internalLang: String
        get() = when (lang) {
            "es" -> "esp"
            else -> "eng"
        }

    private val langCode: Language
        get() = when (lang) {
            "es" -> Language.SPANISH
            else -> Language.ENGLISH
        }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val imageResolution: String
        get() = preferences.getString("${RESOLUTION_PREF_KEY}_$lang", RESOLUTION_PREF_DEFAULT_VALUE)!!

    private val splitImages: String
        get() = if (preferences.getBoolean("${SPLIT_PREF_KEY}_$lang", SPLIT_PREF_DEFAULT_VALUE)) "yes" else "no"

    private var titleList: List<Title>? = null

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(mpPopularMangaRequest())
            .asObservableSuccess()
            .map { response ->
                mpPopularMangaParse(response)
            }
    }

    private fun mpPopularMangaRequest(): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga_list/hot")
            .build()

        return GET("$API_URL/title_list/ranking", newHeaders)
    }

    private fun mpPopularMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null) {
            throw Exception(result.error!!.langPopup.body)
        }

        titleList = result.success.titleRankingView!!.titles
            .filter { it.language == langCode }

        val mangas = titleList!!.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.portraitImageUrl
                url = "#/titles/${it.titleId}"
            }
        }

        return MangasPage(mangas, false)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(mpLatestUpdatesRequest())
            .asObservableSuccess()
            .map { response ->
                mpLatestUpdatesParse(response)
            }
    }

    private fun mpLatestUpdatesRequest(): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/updates")
            .build()

        return GET("$API_URL/web/web_home?lang=$internalLang", newHeaders)
    }

    private fun mpLatestUpdatesParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null) {
            throw Exception(result.error!!.langPopup.body)
        }

        // Fetch all titles to get newer thumbnail urls at the interceptor.
        val popularResponse = client.newCall(mpPopularMangaRequest()).execute().asProto()

        if (popularResponse.success != null) {
            titleList = popularResponse.success.titleRankingView!!.titles
                .filter { it.language == langCode }
        }

        val mangas = result.success.webHomeView!!.groups
            .flatMap { it.titles }
            .mapNotNull { it.title }
            .filter { it.language == langCode }
            .map {
                SManga.create().apply {
                    title = it.name
                    thumbnail_url = it.portraitImageUrl
                    url = "#/titles/${it.titleId}"
                }
            }
            .distinctBy { it.title }

        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(mpSearchMangaRequest())
            .asObservableSuccess()
            .map { response ->
                mpSearchMangaParse(response)
            }
            .map { MangasPage(it.mangas.filter { m -> m.title.contains(query, true) }, it.hasNextPage) }
    }

    private fun mpSearchMangaRequest(): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga_list/all")
            .build()

        return GET("$API_URL/title_list/all", newHeaders)
    }

    private fun mpSearchMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null) {
            throw Exception(result.error!!.langPopup.body)
        }

        titleList = result.success.allTitlesView!!.titles
            .filter { it.language == langCode }

        val mangas = titleList!!.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.portraitImageUrl
                url = "#/titles/${it.titleId}"
            }
        }

        return MangasPage(mangas, false)
    }

    private fun titleDetailsRequest(manga: SManga): Request {
        val titleId = manga.url.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/titles/$titleId")
            .build()

        return GET("$API_URL/title_detail?title_id=$titleId", newHeaders)
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(titleDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mpMangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun mpMangaDetailsParse(response: Response): SManga {
        val result = response.asProto()

        if (result.success == null) {
            throw Exception(result.error!!.langPopup.body)
        }

        val details = result.success.titleDetailView!!
        val title = details.title
        val isCompleted = details.nonAppearanceInfo.contains(COMPLETE_REGEX)

        return SManga.create().apply {
            author = title.author.replace(" / ", ", ")
            artist = author
            description = details.overview + "\n\n" + details.viewingPeriodDescription
            status = if (isCompleted) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = title.portraitImageUrl
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            client.newCall(chapterListRequest(manga))
                .asObservableSuccess()
                .map { response ->
                    mpChapterListParse(response)
                }
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListRequest(manga: SManga): Request = titleDetailsRequest(manga)

    private fun mpChapterListParse(response: Response): List<SChapter> {
        val result = response.asProto()

        if (result.success == null) {
            throw Exception(result.error!!.langPopup.body)
        }

        val titleDetailView = result.success.titleDetailView!!

        val chapters = titleDetailView.firstChapterList + titleDetailView.lastChapterList

        return chapters.reversed()
            // If the subTitle is null, then the chapter time expired.
            .filter { it.subTitle != null }
            .map {
                SChapter.create().apply {
                    name = "${it.name} - ${it.subTitle}"
                    scanlator = "Shueisha"
                    date_upload = 1000L * it.startTimeStamp
                    url = "#/viewer/${it.chapterId}"
                    chapter_number = it.name.substringAfter("#").toFloatOrNull() ?: 0f
                }
            }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                mpPageListParse(response)
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/viewer/$chapterId")
            .build()

        val url = "$API_URL/manga_viewer".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("split", splitImages)
            .addQueryParameter("img_quality", imageResolution)
            .toString()

        return GET(url, newHeaders)
    }

    private fun mpPageListParse(response: Response): List<Page> {
        val result = response.asProto()

        if (result.success == null) {
            throw Exception(result.error!!.langPopup.body)
        }

        val referer = response.request.header("Referer")!!

        return result.success.mangaViewer!!.pages
            .mapNotNull { it.page }
            .mapIndexed { i, page ->
                val encryptionKey = if (page.encryptionKey == null) "" else "&encryptionKey=${page.encryptionKey}"
                Page(i, referer, "${page.imageUrl}$encryptionKey")
            }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Origin")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val resolutionPref = ListPreference(screen.context).apply {
            key = "${RESOLUTION_PREF_KEY}_$lang"
            title = RESOLUTION_PREF_TITLE
            entries = RESOLUTION_PREF_ENTRIES
            entryValues = RESOLUTION_PREF_ENTRY_VALUES
            setDefaultValue(RESOLUTION_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${RESOLUTION_PREF_KEY}_$lang", entry).commit()
            }
        }
        val splitPref = CheckBoxPreference(screen.context).apply {
            key = "${SPLIT_PREF_KEY}_$lang"
            title = SPLIT_PREF_TITLE
            summary = SPLIT_PREF_SUMMARY
            setDefaultValue(SPLIT_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${SPLIT_PREF_KEY}_$lang", checkValue).commit()
            }
        }

        screen.addPreference(resolutionPref)
        screen.addPreference(splitPref)
    }

    private val ErrorResult.langPopup: Popup
        get() = when (lang) {
            "es" -> spanishPopup
            else -> englishPopup
        }

    private fun Response.asProto(): MangaPlusResponse {
        return ProtoBuf.decodeFromByteArray(MangaPlusSerializer, body!!.bytes())
    }

    companion object {
        private const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"

        private const val RESOLUTION_PREF_KEY = "imageResolution"
        private const val RESOLUTION_PREF_TITLE = "Image resolution"
        private val RESOLUTION_PREF_ENTRIES = arrayOf("Low resolution", "Medium resolution", "High resolution")
        private val RESOLUTION_PREF_ENTRY_VALUES = arrayOf("low", "high", "super_high")
        private val RESOLUTION_PREF_DEFAULT_VALUE = RESOLUTION_PREF_ENTRY_VALUES[2]

        private const val SPLIT_PREF_KEY = "splitImage"
        private const val SPLIT_PREF_TITLE = "Split double pages"
        private const val SPLIT_PREF_SUMMARY = "Not all titles support disabling this."
        private const val SPLIT_PREF_DEFAULT_VALUE = true

        private val COMPLETE_REGEX = "completado|complete".toRegex()
    }
}
