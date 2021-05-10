package exh.md.handlers

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import java.util.Locale

class FilterHandler(private val preferencesHelper: PreferencesHelper) {

    internal fun getMDFilterList(): FilterList {
        val filters = mutableListOf(
            OriginalLanguageList(getOriginalLanguage()),
            DemographicList(getDemographics()),
            StatusList(getStatus()),
            SortFilter(sortableList.map { it.first }.toTypedArray()),
            TagList(getTags()),
            TagInclusionMode(),
            TagExclusionMode()
        ).toMutableList()

        if (true) { // preferencesHelper.showR18Filter()) {
            filters.add(2, ContentRatingList(getContentRating()))
        }

        return FilterList(list = filters.toList())
    }

    private class Demographic(name: String) : Filter.CheckBox(name)
    private class DemographicList(demographics: List<Demographic>) :
        Filter.Group<Demographic>("Publication Demographic", demographics)

    private fun getDemographics() = listOf(
        Demographic("None"),
        Demographic("Shounen"),
        Demographic("Shoujo"),
        Demographic("Seinen"),
        Demographic("Josei")
    )

    private class Status(name: String) : Filter.CheckBox(name)
    private class StatusList(status: List<Status>) :
        Filter.Group<Status>("Status", status)

    private fun getStatus() = listOf(
        Status("Ongoing"),
        Status("Completed"),
        Status("Hiatus"),
        Status("Abandoned"),
    )

    private class ContentRating(name: String) : Filter.CheckBox(name)
    private class ContentRatingList(contentRating: List<ContentRating>) :
        Filter.Group<ContentRating>("Content Rating", contentRating)

    private fun getContentRating() = listOf(
        ContentRating("Safe"),
        ContentRating("Suggestive"),
        ContentRating("Erotica"),
        ContentRating("Pornographic")
    )

    private class OriginalLanguage(name: String, val isoCode: String) : Filter.CheckBox(name)
    private class OriginalLanguageList(originalLanguage: List<OriginalLanguage>) :
        Filter.Group<OriginalLanguage>("Original language", originalLanguage)

    private fun getOriginalLanguage() = listOf(
        OriginalLanguage("Japanese (Manga)", "ja"),
        OriginalLanguage("Chinese (Manhua)", "zh"),
        OriginalLanguage("Korean (Manhwa)", "ko"),
    )

    internal class Tag(val id: String, name: String) : Filter.TriState(name)
    private class TagList(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags)

    internal fun getTags() = listOf(
        Tag("391b0423-d847-456f-aff0-8b0cfc03066b", "Action"),
        Tag("f4122d1c-3b44-44d0-9936-ff7502c39ad3", "Adaptation"),
        Tag("87cc87cd-a395-47af-b27a-93258283bbc6", "Adventure"),
        Tag("e64f6742-c834-471d-8d72-dd51fc02b835", "Aliens"),
        Tag("3de8c75d-8ee3-48ff-98ee-e20a65c86451", "Animals"),
        Tag("51d83883-4103-437c-b4b1-731cb73d786c", "Anthology"),
        Tag("0a39b5a1-b235-4886-a747-1d05d216532d", "Award Winning"),
        Tag("5920b825-4181-4a17-beeb-9918b0ff7a30", "Boy Love"),
        Tag("4d32cc48-9f00-4cca-9b5a-a839f0764984", "Comedy"),
        Tag("ea2bc92d-1c26-4930-9b7c-d5c0dc1b6869", "Cooking"),
        Tag("5ca48985-9a9d-4bd8-be29-80dc0303db72", "Crime"),
        Tag("489dd859-9b61-4c37-af75-5b18e88daafc", "Crossdressing"),
        Tag("da2d50ca-3018-4cc0-ac7a-6b7d472a29ea", "Delinquents"),
        Tag("39730448-9a5f-48a2-85b0-a70db87b1233", "Demons"),
        Tag("b13b2a48-c720-44a9-9c77-39c9979373fb", "Doujinshi"),
        Tag("b9af3a63-f058-46de-a9a0-e0c13906197a", "Drama"),
        Tag("fad12b5e-68ba-460e-b933-9ae8318f5b65", "Ecchi"),
        Tag("7b2ce280-79ef-4c09-9b58-12b7c23a9b78", "Fan Colored"),
        Tag("cdc58593-87dd-415e-bbc0-2ec27bf404cc", "Fantasy"),
        Tag("b11fda93-8f1d-4bef-b2ed-8803d3733170", "4-koma"),
        Tag("f5ba408b-0e7a-484d-8d49-4e9125ac96de", "Full Color"),
        Tag("2bd2e8d0-f146-434a-9b51-fc9ff2c5fe6a", "Genderswap"),
        Tag("3bb26d85-09d5-4d2e-880c-c34b974339e9", "Ghosts"),
        Tag("a3c67850-4684-404e-9b7f-c69850ee5da6", "Girl Love"),
        Tag("b29d6a3d-1569-4e7a-8caf-7557bc92cd5d", "Gore"),
        Tag("fad12b5e-68ba-460e-b933-9ae8318f5b65", "Gyaru"),
        Tag("aafb99c1-7f60-43fa-b75f-fc9502ce29c7", "Harem"),
        Tag("33771934-028e-4cb3-8744-691e866a923e", "Historical"),
        Tag("cdad7e68-1419-41dd-bdce-27753074a640", "Horror"),
        Tag("5bd0e105-4481-44ca-b6e7-7544da56b1a3", "Incest"),
        Tag("ace04997-f6bd-436e-b261-779182193d3d", "Isekai"),
        Tag("2d1f5d56-a1e5-4d0d-a961-2193588b08ec", "Loli"),
        Tag("3e2b8dae-350e-4ab8-a8ce-016e844b9f0d", "Long Strip"),
        Tag("85daba54-a71c-4554-8a28-9901a8b0afad", "Mafia"),
        Tag("a1f53773-c69a-4ce5-8cab-fffcd90b1565", "Magic"),
        Tag("81c836c9-914a-4eca-981a-560dad663e73", "Magical Girls"),
        Tag("799c202e-7daa-44eb-9cf7-8a3c0441531e", "Martial Arts"),
        Tag("50880a9d-5440-4732-9afb-8f457127e836", "Mecha"),
        Tag("c8cbe35b-1b2b-4a3f-9c37-db84c4514856", "Medical"),
        Tag("ac72833b-c4e9-4878-b9db-6c8a4a99444a", "Military"),
        Tag("dd1f77c5-dea9-4e2b-97ae-224af09caf99", "Monster Girls"),
        Tag("36fd93ea-e8b8-445e-b836-358f02b3d33d", "Monsters"),
        Tag("f42fbf9e-188a-447b-9fdc-f19dc1e4d685", "Music"),
        Tag("ee968100-4191-4968-93d3-f82d72be7e46", "Mystery"),
        Tag("489dd859-9b61-4c37-af75-5b18e88daafc", "Ninja"),
        Tag("92d6d951-ca5e-429c-ac78-451071cbf064", "Office Workers"),
        Tag("320831a8-4026-470b-94f6-8353740e6f04", "Official Colored"),
        Tag("0234a31e-a729-4e28-9d6a-3f87c4966b9e", "Oneshot"),
        Tag("b1e97889-25b4-4258-b28b-cd7f4d28ea9b", "Philosophical"),
        Tag("df33b754-73a3-4c54-80e6-1a74a8058539", "Police"),
        Tag("9467335a-1b83-4497-9231-765337a00b96", "Post-Apocalyptic"),
        Tag("3b60b75c-a2d7-4860-ab56-05f391bb889c", "Psychological"),
        Tag("0bc90acb-ccc1-44ca-a34a-b9f3a73259d0", "Reincarnation"),
        Tag("65761a2a-415e-47f3-bef2-a9dababba7a6", "Reverse Harem"),
        Tag("423e2eae-a7a2-4a8b-ac03-a8351462d71d", "Romance"),
        Tag("81183756-1453-4c81-aa9e-f6e1b63be016", "Samurai"),
        Tag("caaa44eb-cd40-4177-b930-79d3ef2afe87", "School Life"),
        Tag("256c8bd9-4904-4360-bf4f-508a76d67183", "Sci-Fi"),
        Tag("97893a4c-12af-4dac-b6be-0dffb353568e", "Sexual Violence"),
        Tag("ddefd648-5140-4e5f-ba18-4eca4071d19b", "Shota"),
        Tag("e5301a23-ebd9-49dd-a0cb-2add944c7fe9", "Slice of Life"),
        Tag("69964a64-2f90-4d33-beeb-f3ed2875eb4c", "Sports"),
        Tag("7064a261-a137-4d3a-8848-2d385de3a99c", "Superhero"),
        Tag("eabc5b4c-6aff-42f3-b657-3e90cbd00b75", "Supernatural"),
        Tag("5fff9cde-849c-4d78-aab0-0d52b2ee1d25", "Survival"),
        Tag("07251805-a27e-4d59-b488-f0bfbec15168", "Thriller"),
        Tag("292e862b-2d17-4062-90a2-0356caa4ae27", "Time Travel"),
        Tag("f8f62932-27da-4fe4-8ee1-6779a8c5edba", "Tragedy"),
        Tag("31932a7e-5b8e-49a6-9f12-2afa39dc544c", "Traditional Games"),
        Tag("891cf039-b895-47f0-9229-bef4c96eccd4", "User Created"),
        Tag("d7d1730f-6eb0-4ba6-9437-602cac38664c", "Vampires"),
        Tag("9438db5a-7e2a-4ac0-b39e-e0d95a34b8a8", "Video Games"),
        Tag("d14322ac-4d6f-4e9b-afd9-629d5f4d8a41", "Villainess"),
        Tag("8c86611e-fab7-4986-9dec-d1a2f44acdd5", "Virtual Reality"),
        Tag("e197df38-d0e7-43b5-9b09-2842d0c326dd", "Web Comic"),
        Tag("acc803a4-c95a-4c22-86fc-eb6b582d82a2", "Wuxia"),
        Tag("631ef465-9aba-4afb-b0fc-ea10efe274a8", "Zombies")
    )

    private class TagInclusionMode :
        Filter.Select<String>("Included tags mode", arrayOf("And", "Or"), 0)

    private class TagExclusionMode :
        Filter.Select<String>("Excluded tags mode", arrayOf("And", "Or"), 1)

    val sortableList = listOf(
        Pair("Default (Asc/Desc doesn't matter)", ""),
        Pair("Created at", "createdAt"),
        Pair("Updated at", "updatedAt"),
    )

    class SortFilter(sortables: Array<String>) : Filter.Sort("Sort", sortables, Selection(0, false))

    fun addFiltersToUrl(url: HttpUrl.Builder, filters: FilterList): String {
        url.apply {
            // add filters
            filters.forEach { filter ->
                when (filter) {
                    is OriginalLanguageList -> {
                        filter.state.forEach { lang ->
                            if (lang.state) {
                                addQueryParameter(
                                    "originalLanguage[]",
                                    lang.isoCode
                                )
                            }
                            if (lang.isoCode == "zh") {
                                addQueryParameter(
                                    "originalLanguage[]",
                                    "zh-hk"
                                )
                            }
                        }
                    }
                    is ContentRatingList -> {
                        filter.state.forEach { rating ->
                            if (rating.state) {
                                addQueryParameter(
                                    "contentRating[]",
                                    rating.name.toLowerCase(Locale.US)
                                )
                            }
                        }
                    }
                    is DemographicList -> {
                        filter.state.forEach { demographic ->
                            if (demographic.state) {
                                addQueryParameter(
                                    "publicationDemographic[]",
                                    demographic.name.toLowerCase(
                                        Locale.US
                                    )
                                )
                            }
                        }
                    }
                    is StatusList -> {
                        filter.state.forEach { status ->
                            if (status.state) {
                                addQueryParameter(
                                    "status[]",
                                    status.name.toLowerCase(
                                        Locale.US
                                    )
                                )
                            }
                        }
                    }
                    is SortFilter -> {
                        if (filter.state != null) {
                            if (filter.state!!.index != 0) {
                                val query = sortableList[filter.state!!.index].second
                                val value = when (filter.state!!.ascending) {
                                    true -> "asc"
                                    false -> "desc"
                                }
                                addQueryParameter("order[$query]", value)
                            }
                        }
                    }
                    is TagList -> {
                        filter.state.forEach { tag ->
                            if (tag.isIncluded()) {
                                addQueryParameter("includedTags[]", tag.id)
                            } else if (tag.isExcluded()) {
                                addQueryParameter("excludedTags[]", tag.id)
                            }
                        }
                    }
                    is TagInclusionMode -> {
                        addQueryParameter(
                            "includedTagsMode",
                            filter.values[filter.state].toUpperCase(Locale.US)
                        )
                    }
                    is TagExclusionMode -> {
                        addQueryParameter(
                            "excludedTagsMode",
                            filter.values[filter.state].toUpperCase(Locale.US)
                        )
                    }
                }
            }
            if (false) { // preferencesHelper.showR18Filter().not()) {
                addQueryParameter("contentRating[]", "safe")
            }
        }

        return url.toString()
    }
}
