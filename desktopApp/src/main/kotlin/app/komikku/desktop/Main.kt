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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.komikku.desktop.di.DesktopInjekt
import tachiyomi.core.common.util.system.DesktopLogcatLogger
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Komikku desktop entry point.
 *
 * Boots the shared non-UI stack through Injekt ([DesktopInjekt]: preferences, network, database,
 * repositories, domain interactors) and shows a smoke screen with the library read through the real
 * interactors. The navigation shell replaces it in Phase 4.
 */
fun main() {
    DesktopLogcatLogger.install()
    DesktopInjekt.init()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = MR.strings.app_name.localized(),
        ) {
            App()
        }
    }
}

@Composable
fun App(
    getLibraryManga: GetLibraryManga = remember { Injekt.get() },
    getCategories: GetCategories = remember { Injekt.get() },
) {
    val libraryManga by remember(getLibraryManga) { getLibraryManga.subscribe() }.collectAsState(initial = null)
    val categories by remember(getCategories) { getCategories.subscribe() }.collectAsState(initial = null)

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
                    text = MR.strings.app_name.localized(),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusLine(
                            label = MR.strings.label_library.localized(),
                            value = libraryManga?.size?.toString() ?: MR.strings.loading.localized(),
                        )
                        StatusLine(
                            label = MR.strings.categories.localized(),
                            value = categories?.joinToString { it.displayName() } ?: MR.strings.loading.localized(),
                        )
                    }
                }
            }
        }
    }
}

/** System category ("Default") has an empty name in the database, like on Android. */
private fun Category.displayName(): String =
    if (isSystemCategory) MR.strings.label_default.localized() else name

@Composable
private fun StatusLine(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
