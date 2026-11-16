package app.komikku.desktop.extensions

import android.graphics.drawable.Drawable

/**
 * The launcher icon of an installed extension (`Extension.Installed.icon`): the bitmap bytes read from
 * the APK (PNG / WebP / JPEG). The desktop UI decodes [bytes] with Compose / Coil.
 */
class ExtensionIconDrawable(val bytes: ByteArray) : Drawable()
