package app.komikku.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR

/**
 * Phase 0/1 Kotlin Multiplatform desktop entry point.
 *
 * Proves that the Compose Multiplatform desktop toolchain works inside this repo, that a shared KMP
 * module (:i18n) is consumable from desktop, and that the shared `PreferenceStore` abstraction
 * (:core:preference) persists data on desktop via its [DesktopPreferenceStore] implementation.
 */
fun main() {
    // Real shared domain plumbing running on desktop: persist + read back a launch counter.
    val preferenceStore: PreferenceStore = DesktopPreferenceStore()
    val launchCountPref = preferenceStore.getInt("desktop_launch_count", 0)
    val launchCount = launchCountPref.get() + 1
    launchCountPref.set(launchCount)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Komikku Desktop",
        ) {
            App(launchCount)
        }
    }
}

@Composable
fun App(launchCount: Int) {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(text = "Komikku — Kotlin Multiplatform desktop (Phase 1)")
            // Reference the shared :i18n module's generated resources to prove cross-platform
            // code/resource sharing compiles for the desktop (JVM) target.
            Text(text = "Shared i18n resources available: ${MR.strings::class.simpleName != null}")
            // Value persisted through the shared multiplatform PreferenceStore; increments every run.
            Text(text = "Desktop launches (persisted via shared PreferenceStore): $launchCount")
        }
    }
}
