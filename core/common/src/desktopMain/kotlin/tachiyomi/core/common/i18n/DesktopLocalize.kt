package tachiyomi.core.common.i18n

import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Plural
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc

/*
 * KMK: context-free string resolution for desktop code (the desktop counterpart of the
 * `Context.stringResource(...)` helpers in Localize.kt, which keep working on desktop too and give
 * the same results). Strings resolve through moko for `StringDesc.localeType` (system locale by
 * default; the desktop app may set a custom one).
 */

fun stringResource(resource: StringResource): String =
    StringDesc.Resource(resource).localized().fixed()

fun stringResource(resource: StringResource, vararg args: Any): String =
    StringDesc.ResourceFormatted(resource, *args).localized().fixed()

fun pluralStringResource(resource: PluralsResource, count: Int): String =
    StringDesc.Plural(resource, count).localized().fixed()

fun pluralStringResource(resource: PluralsResource, count: Int, vararg args: Any): String =
    StringDesc.PluralFormatted(resource, count, *args).localized().fixed()

// Same workaround as Localize.kt for https://github.com/icerockdev/moko-resources/issues/337
private fun String.fixed() =
    this.replace("""\""", """"""")
