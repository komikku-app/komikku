package tachiyomi.core.common.i18n

import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Plural
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc

/*
 * KMK: context-free string resolution for desktop code (the desktop counterpart of the
 * `Context.stringResource(...)` helpers in Localize.kt, which keep working on desktop too and give
 * the same results, except that the arguments here keep their backslashes). Strings resolve through moko for
 * `StringDesc.localeType` (system locale by default; the desktop app may set a custom one).
 */

fun stringResource(resource: StringResource): String =
    StringDesc.Resource(resource).localized().fixed()

fun stringResource(resource: StringResource, vararg args: Any): String =
    stringResource(resource).formatWith(args)

fun pluralStringResource(resource: PluralsResource, count: Int): String =
    StringDesc.Plural(resource, count).localized().fixed()

fun pluralStringResource(resource: PluralsResource, count: Int, vararg args: Any): String =
    pluralStringResource(resource, count).formatWith(args)

// Same workaround as Localize.kt for https://github.com/icerockdev/moko-resources/issues/337
private fun String.fixed() =
    this.replace("""\""", """"""")

/**
 * Formats the already fixed text, as moko's formatted descriptions do, so the workaround above never reaches the
 * arguments: a Windows path keeps its backslashes.
 */
private fun String.formatWith(args: Array<out Any>): String {
    val values = args.map { if (it is StringDesc) it.localized() else it }.toTypedArray()
    return String.format(StringDesc.localeType.currentLocale, this, *values)
}
