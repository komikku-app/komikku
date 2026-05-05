package eu.kanade.tachiyomi.source

import android.app.Application
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import exh.source.EH_PACKAGE
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Source.getNameForMangaInfo(
    // SY -->
    mergeSources: List<Source>? = null,
    // SY <--
): String {
    // KMK --> Resolve the merged label once at the top level and pass it down
    // instead of looking it up inside the helper.
    val mergedLabel = Injekt.get<Application>().stringResource(MR.strings.label_merged_entry)
    // KMK <--
    val preferences = Injekt.get<SourcePreferences>()
    val enabledLanguages = preferences.enabledLanguages().get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // SY -->
        !mergeSources.isNullOrEmpty() -> getMergedSourcesString(
            mergeSources,
            enabledLanguages,
            hasOneActiveLanguages,
            mergedLabel,
        )
        // SY <--
        // KMK -->
        isLocalOrStub() -> toString()
        // KMK <--
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages ->
            // KMK -->
            "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
        // KMK <--
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else ->
            // KMK -->
            "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
        // KMK <--
    }
}

// SY -->
private fun getMergedSourcesString(
    mergeSources: List<Source>,
    enabledLangs: List<String>,
    onlyName: Boolean,
    mergedLabel: String,
): String {
    // KMK --> Filter out MergedSource itself so it's not displayed in the list
    val realSources = mergeSources.filterNot { it.id == MERGED_SOURCE_ID }
    // KMK <--
    val sourceNames = if (onlyName) {
        realSources.joinToString { source ->
            when {
                // KMK -->
                source.isLocalOrStub() -> source.toString()
                // KMK <--
                source.lang !in enabledLangs ->
                    // KMK -->
                    "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
                // KMK <--
                else ->
                    source.name
            }
        }
    } else {
        realSources.joinToString { source ->
            // KMK -->
            if (source.isLocalOrStub()) {
                source.toString()
            } else {
                "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
            }
            // KMK <--
        }
    }

    return if (sourceNames.isBlank()) {
        mergedLabel
    } else {
        "$mergedLabel ($sourceNames)"
    }
    // KMK <--
}
// SY <--

fun Source.isLocalOrStub(): Boolean = isLocal() || this is StubSource

// KMK -->
fun Source.isIncognitoModeEnabled(incognitoExtensions: Set<String>? = null): Boolean {
    val extensionPackage = when {
        isLocal() -> LOCAL_SOURCE_PACKAGE
        isEhBasedSource() -> EH_PACKAGE
        else -> Injekt.get<ExtensionManager>().getExtensionPackage(id)
    }
    return extensionPackage in (incognitoExtensions ?: Injekt.get<SourcePreferences>().incognitoExtensions().get())
}
// KMK <--
