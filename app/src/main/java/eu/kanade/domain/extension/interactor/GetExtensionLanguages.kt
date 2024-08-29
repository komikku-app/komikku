package eu.kanade.domain.extension.interactor

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetExtensionLanguages(
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {
    fun subscribe(): Flow<List<String>> {
        return combine(
            preferences.enabledLanguages().changes(),
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguage, availableExtensions ->
            availableExtensions
                .flatMap { ext ->
                    if (ext.sources.isEmpty()) {
                        listOf(ext.lang)
                    } else {
                        ext.sources.map { it.lang }
                    }
                }
                .distinct()
                .sortedWith(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }

    // KMK -->
    companion object {
        fun getLanguageIconID(lang: String): Int? {
            return when (lang) {
                "all" -> R.drawable.ic_flag_un
                // "other" -> R.drawable.globe
                "af" -> R.drawable.za // Afrikaans -> South Africa, ZA
                "am" -> R.drawable.et // Amharic -> Ethiopia, ET
                "ar" -> R.drawable.eg // Arabic -> Egypt, EG - Saudi Arabia, SA
                "az" -> R.drawable.az // Azerbaijani -> Azerbaijan, AZ
                "be" -> R.drawable.by // Belarusian -> Belarus, BY
                "bg" -> R.drawable.bg // Bulgarian -> Bulgaria, BG
                "bn" -> R.drawable.bd // Bengali -> Bangladesh, BD
                "br" -> R.drawable.fr // Breton -> France, FR
                "bs" -> R.drawable.ba // Bosnia and Herzegovina, BA
                "ca" -> R.drawable.es // Catalan -> Spain, ES
                "ceb" -> R.drawable.ph // Cebuano -> Philippines, PH
                "cn" -> R.drawable.cn // Chinese -> China, CN
                "co" -> R.drawable.es // Corsican -> Spain, ES
                "cs" -> R.drawable.cz // Czech -> Czech Republic, CZ
                "da" -> R.drawable.dk // Danish -> Denmark, DK
                "de" -> R.drawable.de // German -> Germany, DE
                "el" -> R.drawable.gr // Greek -> Greece, GR
                "en" -> R.drawable.us // English -> United States, US
                "eo" -> R.drawable.ic_flag_esperanto // Esperanto -> no country
                "es-419" -> R.drawable.mx // Spanish -> Mexico MX, Latin America, Latin America
                "es" -> R.drawable.es // Spanish -> Spain, ES
                "et" -> R.drawable.ee // Estonian -> Estonia, EE
                "eu" -> R.drawable.es // Basque -> Spain, ES
                "fa" -> R.drawable.ir // Persian -> Iran, IR
                "fi" -> R.drawable.fi // Finnish -> Finland, FI
                "fil" -> R.drawable.ph // Filipino -> Philippines, PH
                "fo" -> R.drawable.fo // Faroese -> Faroe Island, FO
                "fr" -> R.drawable.fr // French -> France, FR
                "ga" -> R.drawable.ie // Irish -> Ireland, IE
                "gn" -> R.drawable.py // Guarani -> Paraguay, PY
                "gu" -> R.drawable.in_ // Gujarati -> India, IN
                "ha" -> R.drawable.ng // Hausa -> Nigeria, NG
                "he" -> R.drawable.il // Hebrew -> Israel, IL
                "hi" -> R.drawable.in_ // Hindi -> India, IN
                "hr" -> R.drawable.hr // Croatian -> Croatia, HR
                "ht" -> R.drawable.ht // Haitian -> Haiti, HT
                "hu" -> R.drawable.hu // Hungarian -> Hungary, HU
                "hy" -> R.drawable.am // Armenian -> Armenia, AM
                "id" -> R.drawable.id // Indonesian -> Indonesia, ID
                "ig" -> R.drawable.ng // Igbo -> Nigeria, NG
                "is" -> R.drawable.is_ // Icelandic -> Iceland, IS
                "it" -> R.drawable.it // Italian -> Italy, IT
                "ja" -> R.drawable.jp // Japanese -> Japan, JP
                "jv" -> R.drawable.id // Javanese -> Indonesia, ID
                "ka" -> R.drawable.ge // Georgian -> Georgia, GE
                "kk" -> R.drawable.kz // Kazakh -> Kazakhstan, KZ
                "km" -> R.drawable.kh // Khmer -> Cambodia, KH
                "kn" -> R.drawable.in_ // Kannada -> India, IN
                "ko" -> R.drawable.kr // Korean -> South Korea, KR
                "kr" -> R.drawable.ng // Kanuri -> Nigeria, NG
                "ku" -> R.drawable.iq // Kurdish -> Iraq, IQ
                "ky" -> R.drawable.kg // Kyrgyz -> Kyrgyzstan, KG
                // "la" -> R.drawable.ic_flag_la       // Latin -> Latin America, Latin America
                "lb" -> R.drawable.lu // Luxembourgish -> Luxembourg, LU
                "lmo" -> R.drawable.it // Lombard, Italy, IT
                "lo" -> R.drawable.la // Lao -> Laos, LA
                "lt" -> R.drawable.lt // Lithuanian -> Lithuania, LT
                "lv" -> R.drawable.lv // Latvian -> Latvia, LV
                "mg" -> R.drawable.mg // Malagasy -> Madagascar, MG
                "mi" -> R.drawable.nz // Maori -> New Zealand, NZ
                "mk" -> R.drawable.mk // Macedonian -> Macedonia, MK
                "ml" -> R.drawable.in_ // Malayalam -> India, IN
                "mn" -> R.drawable.mn // Mongolian -> Mongolia, MN
                "mo" -> R.drawable.md // Moldavian -> Moldova, MD
                "mr" -> R.drawable.in_ // Marathi -> India, IN
                "ms" -> R.drawable.my // Malay -> Malaysia, MY
                "mt" -> R.drawable.mt // Maltese -> Malta, MT
                "my" -> R.drawable.mm // Myanmar -> Myanmar, MM
                "ne" -> R.drawable.np // Nepali -> Nepal, NP
                "nl" -> R.drawable.nl // Dutch -> Netherlands, NL
                "no" -> R.drawable.no // Norwegian -> Norway, NO
                "ny" -> R.drawable.mw // Nyanja -> Malawi, MW
                "pl" -> R.drawable.pl // Polish -> Poland, PL
                "ps" -> R.drawable.af // Pashto -> Afghanistan, AF - Pakistan, PK
                "pt-BR" -> R.drawable.br // Portuguese, Brazil, BR
                "pt-PT" -> R.drawable.pt // Portuguese, Portugal, PT
                "pt" -> R.drawable.pt // Portuguese -> Portugal, PT
                "rm" -> R.drawable.ch // Romansh -> Switzerland, SW
                "ro" -> R.drawable.ro // Romanian -> Romania, RO
                "ru" -> R.drawable.ru // Russian -> Russia, RU
                "sd" -> R.drawable.pk // Sindhi -> Pakistan, PK
                "sh" -> R.drawable.hr // Serbo-Croatian -> Serbia, HR
                "si" -> R.drawable.lk // Sinhalese -> Sri Lanka, LK
                "sk" -> R.drawable.sk // Slovak -> Slovakia, SK
                "sl" -> R.drawable.si // Slovenian -> Slovenia, SI
                "sm" -> R.drawable.ws // Samoan -> Samoa, WS
                "sn" -> R.drawable.zw // Shona -> Zimbabwe, ZW
                "so" -> R.drawable.so // Somali -> Somalia, SO
                "sq" -> R.drawable.al // Albanian -> Albania, AL
                "sr" -> R.drawable.hr // Serbian -> Serbia, HR
                "st" -> R.drawable.ls // Sesotho -> South Africa, ZA - Lesotho, LS
                "sv" -> R.drawable.se // Swedish -> Sweden, SE
                "sw" -> R.drawable.tz // Swahili -> Tanzania, TZ - Kenya, KE
                "ta" -> R.drawable.in_ // Tamil -> India, IN
                "te" -> R.drawable.in_ // Telugu -> India, IN
                "tg" -> R.drawable.tj // Tajik -> Tajikistan, TJ
                "th" -> R.drawable.th // Thai -> Thailand, TH
                "ti" -> R.drawable.er // Tigrinya -> Eritrea, ER
                "tk" -> R.drawable.tm // Turkmen -> Turkmenistan, TM
                "tl" -> R.drawable.ph // Filipino -> Philippines, PH
                "to" -> R.drawable.to // Tonga -> Tonga, TO
                "tr" -> R.drawable.tr // Turkish -> Turkey, TR
                "uk" -> R.drawable.ua // Ukrainian -> Ukraine, UA
                "ur" -> R.drawable.pk // Urdu -> Pakistan, PK
                "uz" -> R.drawable.uz // Uzbek -> Uzbekistan, UZ
                "vec" -> R.drawable.it // Venetian -> Italy, IT
                "vi" -> R.drawable.vn // Vietnamese -> Vietnam, VN
                "yo" -> R.drawable.ng // Yoruba -> Nigeria, NG
                "zh-Hans" -> R.drawable.cn // Chinese, simplified -> China, CN
                "zh-Hant" -> R.drawable.tw // Chinese, traditional -> Taiwan, TW
                "zh" -> R.drawable.cn // Chinese -> China, CN
                "zu" -> R.drawable.za // Zulu -> South Africa, ZA
                else -> null
            }
        }
    }
    // KMK <--
}

@Preview
@Composable
private fun LanguageIconsPreview() {
    val languages = listOf(
        "all",
        "other",
        "af",
        "am",
        "ar",
        "az",
        "be",
        "bg",
        "bn",
        "br",
        "bs",
        "ca",
        "ceb",
        "cn",
        "co",
        "cs",
        "da",
        "de",
        "el",
        "en",
        "eo",
        "es-419",
        "es",
        "et",
        "eu",
        "fa",
        "fi",
        "fil",
        "fo",
        "fr",
        "ga",
        "gn",
        "gu",
        "ha",
        "he",
        "hi",
        "hr",
        "ht",
        "hu",
        "hy",
        "id",
        "ig",
        "is",
        "it",
        "ja",
        "jv",
        "ka",
        "kk",
        "km",
        "kn",
        "ko",
        "kr",
        "ku",
        "ky",
        "la",
        "lb",
        "lmo",
        "lo",
        "lt",
        "lv",
        "mg",
        "mi",
        "mk",
        "ml",
        "mn",
        "mo",
        "mr",
        "ms",
        "mt",
        "my",
        "ne",
        "nl",
        "no",
        "ny",
        "pl",
        "ps",
        "pt-BR",
        "pt-PT",
        "pt",
        "rm",
        "ro",
        "ru",
        "sd",
        "sh",
        "si",
        "sk",
        "sl",
        "sm",
        "sn",
        "so",
        "sq",
        "sr",
        "st",
        "sv",
        "sw",
        "ta",
        "te",
        "tg",
        "th",
        "ti",
        "tk",
        "tl",
        "to",
        "tr",
        "uk",
        "ur",
        "uz",
        "vec",
        "vi",
        "yo",
        "zh-Hans",
        "zh-Hant",
        "zh",
        "zu",
    )
    FlowRow {
        languages.forEach { language ->
            val iconResId = GetExtensionLanguages.getLanguageIconID(language) ?: R.drawable.globe
            Icon(
                painter = painterResource(id = iconResId),
                tint = Color.Unspecified,
                contentDescription = language,
                modifier = Modifier
                    .width(21.dp)
                    .height(15.dp),
            )
        }
    }
}
