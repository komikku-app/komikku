package tachiyomi.core.common.i18n

import android.content.Context
import dev.icerock.moko.resources.desc.StringDesc

/**
 * KMK: lets the shared `Localize.kt` keep calling `StringDesc.toString(context)` unchanged.
 *
 * On Android moko's `StringDesc` has a `toString(Context)` member, which always wins over this
 * extension, so Android resolution (and bytecode) is exactly what it was before the KMP conversion.
 * On desktop `StringDesc` has no such member and this resolves to `localized()` (system locale).
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
internal expect fun StringDesc.toString(context: Context): String
