package app.komikku.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 * Komikku desktop entry point (Phase 0 scaffolding).
 *
 * Demonstrates the shared foundation compiled for desktop:
 * - Compose Multiplatform (Material 3) desktop toolchain + the shared :i18n module.
 * - The shared `PreferenceStore` abstraction persisting data on desktop.
 * - The shared `NetworkClient` performing a real HTTP request.
 */
fun main() {
    // Persist + read back a launch counter through the shared PreferenceStore.
    val preferenceStore: PreferenceStore = DesktopPreferenceStore()
    val launchCountPref = preferenceStore.getInt("desktop_launch_count", 0)
    val launchCount = launchCountPref.get() + 1
    launchCountPref.set(launchCount)

    val networkStatus = runCatching {
        runBlocking {
            val response = httpClient().get("https://example.com")
            "HTTP ${response.statusCode} (${response.body.length} bytes)"
        }
    }.getOrElse { "failed: ${it.message}" }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Komikku",
        ) {
            App(launchCount = launchCount, networkStatus = networkStatus)
        }
    }
}

@Composable
fun App(launchCount: Int, networkStatus: String) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        Scaffold { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = "Komikku desktop",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusLine("Shared i18n resources", "${MR.strings::class.simpleName != null}")
                        StatusLine("Launches (shared PreferenceStore)", "$launchCount")
                        StatusLine("NetworkClient GET example.com", networkStatus)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
