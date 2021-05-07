package exh.md.handlers.serializers

import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.protobuf.ProtoNumber

@Serializer(forClass = MangaPlusResponse::class)
object MangaPlusSerializer

@Serializable
data class MangaPlusResponse(
    @ProtoNumber(1) val success: SuccessResult? = null,
    @ProtoNumber(2) val error: ErrorResult? = null
)

@Serializable
data class ErrorResult(
    @ProtoNumber(1) val action: Action,
    @ProtoNumber(2) val englishPopup: Popup,
    @ProtoNumber(3) val spanishPopup: Popup
)

enum class Action { DEFAULT, UNAUTHORIZED, MAINTAINENCE, GEOIP_BLOCKING }

@Serializable
data class Popup(
    @ProtoNumber(1) val subject: String,
    @ProtoNumber(2) val body: String
)

@Serializable
data class SuccessResult(
    @ProtoNumber(1) val isFeaturedUpdated: Boolean? = false,
    @ProtoNumber(5) val allTitlesView: AllTitlesView? = null,
    @ProtoNumber(6) val titleRankingView: TitleRankingView? = null,
    @ProtoNumber(8) val titleDetailView: TitleDetailView? = null,
    @ProtoNumber(10) val mangaViewer: MangaViewer? = null,
    @ProtoNumber(11) val webHomeView: WebHomeView? = null
)

@Serializable
data class TitleRankingView(@ProtoNumber(1) val titles: List<Title> = emptyList())

@Serializable
data class AllTitlesView(@ProtoNumber(1) val titles: List<Title> = emptyList())

@Serializable
data class WebHomeView(@ProtoNumber(2) val groups: List<UpdatedTitleGroup> = emptyList())

@Serializable
data class TitleDetailView(
    @ProtoNumber(1) val title: Title,
    @ProtoNumber(2) val titleImageUrl: String,
    @ProtoNumber(3) val overview: String,
    @ProtoNumber(4) val backgroundImageUrl: String,
    @ProtoNumber(5) val nextTimeStamp: Int = 0,
    @ProtoNumber(6) val updateTiming: UpdateTiming? = UpdateTiming.DAY,
    @ProtoNumber(7) val viewingPeriodDescription: String = "",
    @ProtoNumber(9) val firstChapterList: List<Chapter> = emptyList(),
    @ProtoNumber(10) val lastChapterList: List<Chapter> = emptyList(),
    @ProtoNumber(14) val isSimulReleased: Boolean = true,
    @ProtoNumber(17) val chaptersDescending: Boolean = true
)

enum class UpdateTiming { NOT_REGULARLY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY, DAY }

@Serializable
data class MangaViewer(@ProtoNumber(1) val pages: List<MangaPlusPage> = emptyList())

@Serializable
data class Title(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val author: String,
    @ProtoNumber(4) val portraitImageUrl: String,
    @ProtoNumber(5) val landscapeImageUrl: String,
    @ProtoNumber(6) val viewCount: Int,
    @ProtoNumber(7) val language: Language? = Language.ENGLISH
)

@Serializable
enum class Language(val id: Int) {
    @ProtoNumber(0)
    ENGLISH(0),

    @ProtoNumber(1)
    SPANISH(1)
}

@Serializable
data class UpdatedTitleGroup(
    @ProtoNumber(1) val groupName: String,
    @ProtoNumber(2) val titles: List<UpdatedTitle> = emptyList()
)

@Serializable
data class UpdatedTitle(
    @ProtoNumber(1) val title: Title? = null
)

@Serializable
data class Chapter(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val chapterId: Int,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val subTitle: String? = null,
    @ProtoNumber(6) val startTimeStamp: Int,
    @ProtoNumber(7) val endTimeStamp: Int
)

@Serializable
data class MangaPlusPage(@ProtoNumber(1) val page: MangaPage? = null)

@Serializable
data class MangaPage(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(2) val width: Int,
    @ProtoNumber(3) val height: Int,
    @ProtoNumber(5) val encryptionKey: String? = null
)
