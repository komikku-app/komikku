package tachiyomi.core.common.i18n

import android.content.Context
import dev.icerock.moko.resources.desc.StringDesc

// KMK: desktop strings do not need a context; moko resolves them for the default locale.
internal actual fun StringDesc.toString(context: Context): String = localized()
