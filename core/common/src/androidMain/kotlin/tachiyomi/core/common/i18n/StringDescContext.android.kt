package tachiyomi.core.common.i18n

import android.content.Context
import dev.icerock.moko.resources.desc.StringDesc

// KMK: never called on Android (moko's `StringDesc.toString(Context)` member shadows it).
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
internal actual fun StringDesc.toString(context: Context): String = toString(context)
