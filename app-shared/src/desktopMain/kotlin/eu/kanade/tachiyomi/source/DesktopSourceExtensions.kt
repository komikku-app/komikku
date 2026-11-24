package eu.kanade.tachiyomi.source

import app.komikku.shared.extension.PlatformExtensionManager
import eu.kanade.domain.source.service.SourcePreferences
import exh.source.EH_PACKAGE
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.isEhBasedSource
import tachiyomi.domain.source.model.StubSource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
 * KMK: desktop counterparts of the `Source` extensions in app `eu/kanade/tachiyomi/source/SourceExtensions.kt`
 * that `desktopCopy` borrowed files call (`GetIncognitoState`, the migration list's `getNameForMangaInfo`). Same bodies,
 * with the extension package resolved through `PlatformExtensionManager` instead of Android's `ExtensionManager`.
 */

fun Source.getNameForMangaInfo(
    mergeSources: List<Source>? = null,
): String {
    val preferences = Injekt.get<SourcePreferences>()
    val enabledLanguages = preferences.enabledLanguages().get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        !mergeSources.isNullOrEmpty() -> getMergedSourcesString(mergeSources, enabledLanguages, hasOneActiveLanguages)
        isLocalOrStub() -> toString()
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages -> "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else -> "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
    }
}

private fun getMergedSourcesString(
    mergeSources: List<Source>,
    enabledLangs: List<String>,
    onlyName: Boolean,
): String {
    return if (onlyName) {
        mergeSources.joinToString { source ->
            when {
                source.isLocalOrStub() -> source.toString()
                source.lang !in enabledLangs -> "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
                else -> source.name
            }
        }
    } else {
        mergeSources.joinToString { source ->
            if (source.isLocalOrStub()) source.toString() else "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
        }
    }
}

fun Source.isLocalOrStub(): Boolean = isLocal() || this is StubSource

fun Source.isIncognitoModeEnabled(incognitoExtensions: Set<String>? = null): Boolean {
    val extensionPackage = when {
        isLocal() -> LOCAL_SOURCE_PACKAGE
        isEhBasedSource() -> EH_PACKAGE
        else -> Injekt.get<PlatformExtensionManager>().getExtensionPackage(id)
    }
    return extensionPackage in (incognitoExtensions ?: Injekt.get<SourcePreferences>().incognitoExtensions().get())
}
