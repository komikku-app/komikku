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
import tachiyomi.i18n.MR

/**
 * Phase 0 Kotlin Multiplatform desktop entry point.
 *
 * Proves that the Compose Multiplatform desktop toolchain works inside this repo and that
 * a shared KMP module (:i18n, which also targets Android) can be consumed from desktop.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Komikku Desktop",
    ) {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(text = "Komikku — Kotlin Multiplatform desktop (Phase 0)")
            // Reference the shared :i18n module's generated resources to prove cross-platform
            // code/resource sharing compiles for the desktop (JVM) target.
            Text(text = "Shared i18n resources available: ${MR.strings::class.simpleName != null}")
        }
    }
}
