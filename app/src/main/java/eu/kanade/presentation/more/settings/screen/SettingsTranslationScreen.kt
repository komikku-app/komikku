package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.translation.TranslationFonts
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import mihon.domain.translation.translators.LanguageTranslators
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

object SettingsTranslationScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsTranslationScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val saveChapterAsCBZ by downloadPreferences.saveChaptersAsCBZ().collectAsState()
        return listOf(
            Preference.PreferenceItem.InfoPreference("Must turn-off CBZ for now"),
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.saveChaptersAsCBZ(),
                title = stringResource(MR.strings.save_chapter_as_cbz),
            ),
        ) +
            if (!saveChapterAsCBZ) {
                listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = downloadPreferences.translateOnDownload(),
                        title = "Auto Translate On Download",
                    ),
                    getTranslateFromLanguage(downloadPreferences = downloadPreferences),
                    getTranslateToLanguage(downloadPreferences = downloadPreferences),
                    getTranslateFont(downloadPreferences = downloadPreferences),
                    getTranslateEngineGroup(downloadPreferences = downloadPreferences),
                )
            } else {
                emptyList()
            }
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
        val translationEngine by downloadPreferences.translationEngine().collectAsState()
        return Preference.PreferenceGroup(
            title = "Translation Engine",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.translationEngine(),
                    title = "Translator",
                    entries = LanguageTranslators.entries
                        .associateWith { it.label }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = downloadPreferences.translationApiKey(),
                    subtitle = "Secret Key",
                    title = "Translator API Key",
                    enabled = translationEngine in listOf(
                        LanguageTranslators.GEMINI,
                        LanguageTranslators.OPENROUTER,
                    ),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    pref = downloadPreferences.translationEngineModel(),
                    subtitle = "Model for open router",
                    title = "Translator Model",
                    enabled = translationEngine in listOf(
                        LanguageTranslators.OPENROUTER,
                    ),
                ),
                Preference.PreferenceItem.InfoPreference(
                    title = "Please Read the Github page Instructions for Setting up Open Router",
                ),
            ),
        )
    }
}
