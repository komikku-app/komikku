package exh.uconfig

import exh.source.ExhPreferences
import okhttp3.FormBody
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class EhUConfigBuilder {
    private val exhPreferences: ExhPreferences by injectLazy()

    fun build(hathPerks: EHHathPerksResponse): FormBody {
        val configItems = mutableListOf<ConfigItem>()

        configItems += when (
            exhPreferences.imageQuality()
                .get()
                .lowercase(Locale.getDefault())
        ) {
            "ovrs_2400" -> Entry.ImageSize.`2400`
            "ovrs_1600" -> Entry.ImageSize.`1600`
            "high" -> Entry.ImageSize.`1280`
            "med" -> Entry.ImageSize.`980`
            "low" -> Entry.ImageSize.`780`
            "auto" -> Entry.ImageSize.AUTO
            else -> Entry.ImageSize.AUTO
        }

        configItems += when (exhPreferences.useHentaiAtHome().get()) {
            2 -> Entry.UseHentaiAtHome.NO
            1 -> Entry.UseHentaiAtHome.DEFAULTONLY
            else -> Entry.UseHentaiAtHome.ANY
        }

        configItems += if (exhPreferences.useJapaneseTitle().get()) {
            Entry.TitleDisplayLanguage.JAPANESE
        } else {
            Entry.TitleDisplayLanguage.DEFAULT
        }

        configItems += if (exhPreferences.exhUseOriginalImages().get()) {
            Entry.UseOriginalImages.YES
        } else {
            Entry.UseOriginalImages.NO
        }

        configItems += when {
            hathPerks.allThumbs -> Entry.ThumbnailRows.`40`
            hathPerks.thumbsUp -> Entry.ThumbnailRows.`20`
            hathPerks.moreThumbs -> Entry.ThumbnailRows.`10`
            else -> Entry.ThumbnailRows.`4`
        }

        configItems += when {
            hathPerks.pagingEnlargementIII -> Entry.SearchResultsCount.`200`
            hathPerks.pagingEnlargementII -> Entry.SearchResultsCount.`100`
            hathPerks.pagingEnlargementI -> Entry.SearchResultsCount.`50`
            else -> Entry.SearchResultsCount.`25`
        }

        configItems += Entry.DisplayMode()
        configItems += Entry.UseMPV()
        configItems += Entry.ShowPopularRightNowPane()

        configItems += Entry.TagFilteringThreshold(exhPreferences.ehTagFilterValue().get())
        configItems += Entry.TagWatchingThreshold(exhPreferences.ehTagWatchingValue().get())

        configItems += Entry.LanguageSystem().getLanguages(exhPreferences.exhSettingsLanguages().get().split("\n"))

        configItems += Entry.Categories().categoryConfigs(
            exhPreferences.exhEnabledCategories().get().split(",").map {
                it.toBoolean()
            },
        )

        // Actually build form body
        val formBody = FormBody.Builder()
        configItems.forEach {
            formBody.add(it.key, it.value)
        }
        formBody.add("apply", "Apply")
        return formBody.build()
    }
}

object Entry {
    enum class UseHentaiAtHome(override val value: String) : ConfigItem {
        ANY("0"),
        DEFAULTONLY("1"),
        NO("2"),
        ;

        override val key = "uh"
    }

    @Suppress("EnumEntryName")
    enum class ImageSize(override val value: String) : ConfigItem {
        AUTO("0"),
        `2400`("5"),
        `1600`("4"),
        `1280`("3"),
        `980`("2"),
        `780`("1"),
        ;

        override val key = "xr"
    }

    enum class TitleDisplayLanguage(override val value: String) : ConfigItem {
        DEFAULT("0"),
        JAPANESE("1"),
        ;

        override val key = "tl"
    }

    // Locked to extended mode as that's what the parser and toplists use
    class DisplayMode : ConfigItem {
        override val key = "dm"
        override val value = "2"
    }

    @Suppress("EnumEntryName")
    enum class SearchResultsCount(override val value: String) : ConfigItem {
        `25`("0"),
        `50`("1"),
        `100`("2"),
        `200`("3"),
        ;

        override val key = "rc"
    }

    @Suppress("EnumEntryName")
    enum class ThumbnailRows(override val value: String) : ConfigItem {
        `4`("0"),
        `10`("1"),
        `20`("2"),
        `40`("3"),
        ;

        override val key = "tr"
    }

    enum class UseOriginalImages(override val value: String) : ConfigItem {
        NO("0"),
        YES("1"),
        ;

        override val key = "oi"
    }

    // Locked to no MPV as that's what the parser uses
    class UseMPV : ConfigItem {
        override val key = "qb"
        override val value = "0"
    }

    // Locked to no popular pane as we can't parse it
    class ShowPopularRightNowPane : ConfigItem {
        override val key = "pp"
        override val value = "1"
    }

    class TagFilteringThreshold(value: Int) : ConfigItem {
        override val key = "ft"
        override val value = "$value"
    }

    class TagWatchingThreshold(value: Int) : ConfigItem {
        override val key = "wt"
        override val value = "$value"
    }

    class Categories {

        fun categoryConfigs(list: List<Boolean>): List<ConfigItem> {
            return listOf(
                GenreConfigItem("ct_doujinshi", list[0]),
                GenreConfigItem("ct_manga", list[1]),
                GenreConfigItem("ct_artistcg", list[2]),
                GenreConfigItem("ct_gamecg", list[3]),
                GenreConfigItem("ct_western", list[4]),
                GenreConfigItem("ct_non-h", list[5]),
                GenreConfigItem("ct_imageset", list[6]),
                GenreConfigItem("ct_cosplay", list[7]),
                GenreConfigItem("ct_asianporn", list[8]),
                GenreConfigItem("ct_misc", list[9]),
            )
        }

        private class GenreConfigItem(override val key: String, exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
        }
    }

    class LanguageSystem {
        private fun transformConfig(values: List<String>) = values.map { pref ->
            pref.split("*").map { it.toBoolean() }
        }

        fun getLanguages(values: List<String>): List<ConfigItem> {
            val config = transformConfig(values)
            return listOf(
                Japanese(config[0]),
                English(config[1]),
                Chinese(config[2]),
                Dutch(config[3]),
                French(config[4]),
                German(config[5]),
                Hungarian(config[6]),
                Italian(config[7]),
                Korean(config[8]),
                Polish(config[9]),
                Portuguese(config[10]),
                Russian(config[11]),
                Spanish(config[12]),
                Thai(config[13]),
                Vietnamese(config[14]),
                NotAvailable(config[15]),
                Other(config[16]),
            ).flatMap { it.configs }
        }

        private abstract class BaseLanguage(val values: List<Boolean>) {
            abstract val translatedKey: String
            abstract val rewriteKey: String

            open val configs: List<LanguageConfigItem>
                get() = listOf(
                    LanguageConfigItem(translatedKey, values[1]),
                    LanguageConfigItem(rewriteKey, values[2]),
                )

            protected class LanguageConfigItem(override val key: String, value: Boolean) : ConfigItem {
                override val value = if (value) "checked" else ""
            }
        }

        private abstract class Language(values: List<Boolean>) : BaseLanguage(values) {
            abstract val originalKey: String

            override val configs: List<LanguageConfigItem>
                get() = listOf(
                    LanguageConfigItem(originalKey, values[0]),
                    LanguageConfigItem(translatedKey, values[1]),
                    LanguageConfigItem(rewriteKey, values[2]),
                )
        }

        private class Japanese(values: List<Boolean>) : BaseLanguage(values) {
            override val translatedKey: String = "xl_1024"
            override val rewriteKey: String = "xl_2048"
        }

        private class English(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_1"
            override val translatedKey: String = "xl_1025"
            override val rewriteKey: String = "xl_2049"
        }

        private class Chinese(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_10"
            override val translatedKey: String = "xl_1034"
            override val rewriteKey: String = "xl_2058"
        }

        private class Dutch(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_20"
            override val translatedKey: String = "xl_1044"
            override val rewriteKey: String = "xl_2068"
        }

        private class French(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_30"
            override val translatedKey: String = "xl_1054"
            override val rewriteKey: String = "xl_2078"
        }

        private class German(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_40"
            override val translatedKey: String = "xl_1064"
            override val rewriteKey: String = "xl_2088"
        }

        private class Hungarian(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_50"
            override val translatedKey: String = "xl_1074"
            override val rewriteKey: String = "xl_2098"
        }

        private class Italian(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_60"
            override val translatedKey: String = "xl_1084"
            override val rewriteKey: String = "xl_2108"
        }

        private class Korean(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_70"
            override val translatedKey: String = "xl_1094"
            override val rewriteKey: String = "xl_2118"
        }

        private class Polish(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_80"
            override val translatedKey: String = "xl_1104"
            override val rewriteKey: String = "xl_2128"
        }

        private class Portuguese(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_90"
            override val translatedKey: String = "xl_1114"
            override val rewriteKey: String = "xl_2138"
        }

        private class Russian(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_100"
            override val translatedKey: String = "xl_1124"
            override val rewriteKey: String = "xl_2148"
        }

        private class Spanish(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_110"
            override val translatedKey: String = "xl_1134"
            override val rewriteKey: String = "xl_2158"
        }

        private class Thai(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_120"
            override val translatedKey: String = "xl_1144"
            override val rewriteKey: String = "xl_2168"
        }

        private class Vietnamese(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_130"
            override val translatedKey: String = "xl_1154"
            override val rewriteKey: String = "xl_2178"
        }

        private class NotAvailable(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_254"
            override val translatedKey: String = "xl_1278"
            override val rewriteKey: String = "xl_2302"
        }

        private class Other(values: List<Boolean>) : Language(values) {
            override val originalKey: String = "xl_255"
            override val translatedKey: String = "xl_1279"
            override val rewriteKey: String = "xl_2303"
        }
    }
}

interface ConfigItem {
    val key: String
    val value: String
}
