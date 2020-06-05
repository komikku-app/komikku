package exh.uconfig

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import okhttp3.FormBody
import uy.kohesive.injekt.injectLazy

class EhUConfigBuilder {
    private val prefs: PreferencesHelper by injectLazy()

    fun build(hathPerks: EHHathPerksResponse): FormBody {
        val configItems = mutableListOf<ConfigItem>()

        configItems += when (
            prefs.imageQuality()
                .get()
                .toLowerCase()
        ) {
            "ovrs_2400" -> Entry.ImageSize.`2400`
            "ovrs_1600" -> Entry.ImageSize.`1600`
            "high" -> Entry.ImageSize.`1280`
            "med" -> Entry.ImageSize.`980`
            "low" -> Entry.ImageSize.`780`
            "auto" -> Entry.ImageSize.AUTO
            else -> Entry.ImageSize.AUTO
        }

        configItems += if (prefs.useHentaiAtHome().get()) {
            Entry.UseHentaiAtHome.YES
        } else {
            Entry.UseHentaiAtHome.NO
        }

        configItems += if (prefs.useJapaneseTitle().get()) {
            Entry.TitleDisplayLanguage.JAPANESE
        } else {
            Entry.TitleDisplayLanguage.DEFAULT
        }

        configItems += if (prefs.eh_useOriginalImages().get()) {
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

        configItems += Entry.TagFilteringThreshold(prefs.ehTagFilterValue().get())
        configItems += Entry.TagWatchingThreshold(prefs.ehTagWatchingValue().get())

        configItems += Entry.LanguageSystem().getLanguages(prefs.eh_settingsLanguages().get().split("\n"))

        configItems += Entry.Categories().categoryConfigs(prefs.eh_EnabledCategories().get().split(",").map { it.toBoolean() })

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
        YES("0"),
        NO("1");

        override val key = "uh"
    }

    enum class ImageSize(override val value: String) : ConfigItem {
        AUTO("0"),
        `2400`("5"),
        `1600`("4"),
        `1280`("3"),
        `980`("2"),
        `780`("1");

        override val key = "xr"
    }

    enum class TitleDisplayLanguage(override val value: String) : ConfigItem {
        DEFAULT("0"),
        JAPANESE("1");

        override val key = "tl"
    }

    // Locked to extended mode as that's what the parser and toplists use
    class DisplayMode : ConfigItem {
        override val key = "dm"
        override val value = "2"
    }

    enum class SearchResultsCount(override val value: String) : ConfigItem {
        `25`("0"),
        `50`("1"),
        `100`("2"),
        `200`("3");

        override val key = "rc"
    }

    enum class ThumbnailRows(override val value: String) : ConfigItem {
        `4`("0"),
        `10`("1"),
        `20`("2"),
        `40`("3");

        override val key = "tr"
    }

    enum class UseOriginalImages(override val value: String) : ConfigItem {
        NO("0"),
        YES("1");

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

    class Categories() {

        fun categoryConfigs(list: List<Boolean>): List<ConfigItem> {
            return listOf(
                Doujinshi(list[0]),
                Manga(list[1]),
                ArtistCG(list[2]),
                GameCG(list[3]),
                Western(list[4]),
                NonH(list[5]),
                ImageSet(list[6]),
                Cosplay(list[7]),
                AsianPorn(list[8]),
                Misc(list[9])
            )
        }

        private class Doujinshi(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_doujinshi"
        }
        private class Manga(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_manga"
        }
        private class ArtistCG(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_artistcg"
        }
        private class GameCG(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_gamecg"
        }
        private class Western(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_western"
        }
        private class NonH(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_non-h"
        }
        private class ImageSet(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_imageset"
        }
        private class Cosplay(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_cosplay"
        }
        private class AsianPorn(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_asianporn"
        }
        private class Misc(exclude: Boolean) : ConfigItem {
            override val value = if (exclude) "1" else "0"
            override val key = "ct_misc_div"
        }
    }

    class LanguageSystem {

        fun getLanguages(values: List<String>): List<ConfigItem> {
            return Japanese(values[0].split("*").map { it.toBoolean() }).configs +
                English(values[1].split("*").map { it.toBoolean() }).configs +
                Chinese(values[2].split("*").map { it.toBoolean() }).configs +
                Dutch(values[3].split("*").map { it.toBoolean() }).configs +
                French(values[4].split("*").map { it.toBoolean() }).configs +
                German(values[5].split("*").map { it.toBoolean() }).configs +
                Hungarian(values[6].split("*").map { it.toBoolean() }).configs +
                Italian(values[7].split("*").map { it.toBoolean() }).configs +
                Korean(values[8].split("*").map { it.toBoolean() }).configs +
                Polish(values[9].split("*").map { it.toBoolean() }).configs +
                Portuguese(values[10].split("*").map { it.toBoolean() }).configs +
                Russian(values[11].split("*").map { it.toBoolean() }).configs +
                Spanish(values[12].split("*").map { it.toBoolean() }).configs +
                Thai(values[13].split("*").map { it.toBoolean() }).configs +
                Vietnamese(values[14].split("*").map { it.toBoolean() }).configs +
                NotAvailable(values[15].split("*").map { it.toBoolean() }).configs +
                Other(values[16].split("*").map { it.toBoolean() }).configs
        }

        private class Japanese(values: List<Boolean>) {

            val configs = listOf(
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1024"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2048"
                override val value = if (value) "checked" else ""
            }
        }
        private class English(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_1"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1025"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2049"
                override val value = if (value) "checked" else ""
            }
        }
        private class Chinese(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_10"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1034"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2058"
                override val value = if (value) "checked" else ""
            }
        }

        private class Dutch(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_20"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1044"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2068"
                override val value = if (value) "checked" else ""
            }
        }

        private class French(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_30"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1054"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2078"
                override val value = if (value) "checked" else ""
            }
        }

        private class German(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_40"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1064"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2088"
                override val value = if (value) "checked" else ""
            }
        }

        private class Hungarian(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_50"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1074"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2098"
                override val value = if (value) "checked" else ""
            }
        }

        private class Italian(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_60"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1084"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2108"
                override val value = if (value) "checked" else ""
            }
        }

        private class Korean(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_70"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1094"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2118"
                override val value = if (value) "checked" else ""
            }
        }

        private class Polish(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_80"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1104"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2128"
                override val value = if (value) "checked" else ""
            }
        }

        private class Portuguese(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_90"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1114"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2138"
                override val value = if (value) "checked" else ""
            }
        }

        private class Russian(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_100"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1124"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2148"
                override val value = if (value) "checked" else ""
            }
        }

        private class Spanish(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_110"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1134"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2158"
                override val value = if (value) "checked" else ""
            }
        }

        private class Thai(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_120"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1144"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2168"
                override val value = if (value) "checked" else ""
            }
        }

        private class Vietnamese(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_130"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1154"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2178"
                override val value = if (value) "checked" else ""
            }
        }

        private class NotAvailable(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_254"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1278"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2302"
                override val value = if (value) "checked" else ""
            }
        }

        private class Other(values: List<Boolean>) {

            val configs = listOf(
                Original(values[0]),
                Translated(values[1]),
                Rewrite(values[2])
            )

            class Original(value: Boolean) : ConfigItem {
                override val key = "xl_255"
                override val value = if (value) "checked" else ""
            }
            class Translated(value: Boolean) : ConfigItem {
                override val key = "xl_1279"
                override val value = if (value) "checked" else ""
            }
            class Rewrite(value: Boolean) : ConfigItem {
                override val key = "xl_2303"
                override val value = if (value) "checked" else ""
            }
        }
    }
}

interface ConfigItem {
    val key: String
    val value: String
}
