package eu.kanade.tachiyomi.extension

import app.komikku.shared.extension.PlatformExtensionManager

/**
 * KMK: desktop counterpart of the Android `ExtensionManager` type for the borrowed `DownloadCache`
 * (it only waits for `isInitialized`); resolves to the desktop extension manager registered as
 * [PlatformExtensionManager].
 */
typealias ExtensionManager = PlatformExtensionManager
