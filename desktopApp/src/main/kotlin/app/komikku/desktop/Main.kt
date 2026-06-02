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
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.network.httpClient
import tachiyomi.i18n.MR

/**
 * Phase 0/1/2 Kotlin Multiplatform desktop entry point.
 *
 * Demonstrates the cumulative cross-platform foundation:
 * - Phase 0: Compose Multiplatform desktop toolchain + consuming the shared :i18n module.
 * - Phase 1: the shared `PreferenceStore` abstraction persisting data on desktop.
 * - Phase 2: the shared multiplatform `NetworkClient` (OkHttp) performing a real HTTP request.
 */
fun main() {
    // Phase 1: persist + read back a launch counter through the shared PreferenceStore.
    val preferenceStore: PreferenceStore = DesktopPreferenceStore()
    val launchCountPref = preferenceStore.getInt("desktop_launch_count", 0)
    val launchCount = launchCountPref.get() + 1
    launchCountPref.set(launchCount)

    // Phase 2: perform a real HTTP request through the shared multiplatform NetworkClient.
    val networkStatus = runCatching {
        runBlocking {
            val response = httpClient().get("https://example.com")
            "HTTP ${response.statusCode} (${response.body.length} bytes)"
        }
    }.getOrElse { "failed: ${it.message}" }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Komikku Desktop",
        ) {
            App(launchCount = launchCount, networkStatus = networkStatus)
        }
    }
}

@Composable
fun App(launchCount: Int, networkStatus: String) {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(text = "Komikku — Kotlin Multiplatform desktop (Phase 2)")
            // Phase 0: shared :i18n module compiled for desktop.
            Text(text = "Shared i18n resources available: ${MR.strings::class.simpleName != null}")
            // Phase 1: value persisted through the shared PreferenceStore; increments every run.
            Text(text = "Desktop launches (persisted via shared PreferenceStore): $launchCount")
            // Phase 2: real network request via the shared OkHttp-backed NetworkClient.
            Text(text = "Shared NetworkClient GET example.com: $networkStatus")
        }
    }
}
