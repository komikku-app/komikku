package eu.kanade.presentation.more.settings.screen


import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.translation.TranslationFonts
import eu.kanade.translation.translators.LanguageTranslators
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = StringResource(R.string.pref_category_translation)

    @Composable
    override fun getPreferences(): List<Preference> {
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.translateOnDownload(),
                title = "Auto Translate On Download",
            ),
            getTranslateFromLanguage(downloadPreferences = downloadPreferences),
            getTranslateToLanguage(downloadPreferences = downloadPreferences),
            getTranslateFont(downloadPreferences = downloadPreferences),
            getTranslateEngineGroup(downloadPreferences = downloadPreferences),
        )
    }

    @Composable
    private fun getTranslateFromLanguage(
        downloadPreferences: DownloadPreferences,
    ): Preference {
        val opts = listOf("Chinese", "Japanese", "Korean", "Latin")
        return Preference.PreferenceItem.ListPreference(
            pref = downloadPreferences.translateFromLanguage(),
            title = "Translate From",
            entries = listOf(0, 1, 2, 3)
                .associateWith {
                    opts[it]
                }
                .toImmutableMap(),
        )
    }

    @Composable
    private fun getTranslateToLanguage(
        downloadPreferences: DownloadPreferences,
    ): Preference {
        return Preference.PreferenceItem.ListPreference(
            pref = downloadPreferences.translateToLanguage(),
            title = "Translate To",
            entries = Locale.getAvailableLocales().distinctBy { it.language }.sortedBy { it.displayLanguage }
                .associate { Pair(it.language, it.displayLanguage) }
                .toImmutableMap(),
        )
    }

    @Composable
    private fun getTranslateFont(
        downloadPreferences: DownloadPreferences,
    ): Preference {
        val opts = TranslationFonts.entries.map { v -> v.label }
        return Preference.PreferenceItem.ListPreference(
            pref = downloadPreferences.translationFont(),
            title = "Translation Font",
            entries = listOf(0, 1, 2)
                .associateWith {
                    opts[it]
                }
                .toImmutableMap(),
        )
    }

    @Composable
    private fun getTranslateEngineGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        val opts = LanguageTranslators.entries.map { v -> v.label }
        return Preference.PreferenceGroup(
            title = "Translation Engine",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.translationEngine(),
                    title = "Translator",
                    entries = listOf(0, 1, 2, 3)
                        .associateWith {
                            opts[it]
                        }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = downloadPreferences.translationApiKey(),
                    subtitle = "Secret Key",
                    title = "Translator API Key",
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = downloadPreferences.translationEngineModel(),
                    subtitle = "Model for open router",
                    title = "Translator Model",
                ),

                Preference.PreferenceItem.InfoPreference("Please Read the Github page Instructions for Setting up Open Router"),


                ),
        )
    }
}
