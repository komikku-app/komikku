package eu.kanade.tachiyomi.ui.manga

import androidx.annotation.ColorInt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.ButtonDefaults
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A screen that displays a colors palette of current theme.
 */
class PaletteScreen(
    @ColorInt private val seedColor: Int?,
) : Screen() {

    @Composable
    override fun Content() {
        val uiPreferences = Injekt.get<UiPreferences>()
        val themeCoverBased = uiPreferences.themeCoverBased().get()

        val seedColor = seedColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

        TachiyomiTheme(
            seedColor = seedColor.takeIf { themeCoverBased }
        ) {
            MaterialThemeContent(seedColor)
        }
    }

    @Composable
    fun MaterialThemeContent(seedColor: Color) {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = "Colors Palette",
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(
                // Using modifier instead of contentPadding so we can use stickyHeader
                modifier = Modifier.padding(contentPadding),
            ) {
                item {
                    ButtonsColor(
                        "seedColor & onPrimary",
                        seedColor,
                        "seedColor & contentColor",
                        seedColor,
                        MaterialTheme.colorScheme.onPrimary,
                    )
                }
                item {
                    ButtonsColor(
                        "primary",
                        MaterialTheme.colorScheme.primary,
                        "primaryContainer",
                        MaterialTheme.colorScheme.primaryContainer
                    )
                }
                item {
                    ButtonsColor(
                        "secondary",
                        MaterialTheme.colorScheme.secondary,
                        "secondaryContainer",
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                }
                item {
                    ButtonsColor(
                        "tertiary",
                        MaterialTheme.colorScheme.tertiary,
                        "tertiaryContainer",
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
                item {
                    ButtonsColor(
                        "surface",
                        MaterialTheme.colorScheme.surface,
                        "surfaceVariant",
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                item {
                    ButtonsColor(
                        "inverseSurface",
                        MaterialTheme.colorScheme.inverseSurface,
                        "surfaceTint",
                        MaterialTheme.colorScheme.surfaceTint
                    )
                }
                item {
                    ButtonsColor(
                        "inversePrimary",
                        MaterialTheme.colorScheme.inversePrimary,
                        "background",
                        MaterialTheme.colorScheme.background
                    )
                }
                item {
                    ButtonsColor(
                        "error",
                        MaterialTheme.colorScheme.error,
                        "errorContainer",
                        MaterialTheme.colorScheme.errorContainer
                    )
                }
                item {
                    ButtonsColor(
                        "outline",
                        MaterialTheme.colorScheme.outline,
                        "outlineVariant",
                        MaterialTheme.colorScheme.outlineVariant
                    )
                }
                item {
                    ButtonsColor(
                        "scrim",
                        MaterialTheme.colorScheme.scrim,
                        "surfaceBright",
                        MaterialTheme.colorScheme.surfaceBright
                    )
                }
                item {
                    ButtonsColor(
                        "surfaceDim",
                        MaterialTheme.colorScheme.surfaceDim,
                        "surfaceContainer",
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                }
                item {
                    ButtonsColor(
                        "surfaceContainerHigh",
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        "surfaceContainerHighest",
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
                item {
                    ButtonsColor(
                        "surfaceContainerLow",
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        "surfaceContainerLowest",
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                }
            }
        }
    }
}

@Composable
fun ButtonsColor(
    name1: String,
    color1: Color,
    name2: String,
    color2: Color,
    contentColor1: Color = contentColorFor(backgroundColor = color1),
    contentColor2: Color = contentColorFor(backgroundColor = color2),
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Button(
            onClick = { },
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color1,
                contentColor = contentColor1,
            )
        ) {
            Text(
                text = name1,
            )
        }
        Button(
            onClick = { },
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color2,
                contentColor = contentColor2,
            ),
        ) {
            Text(
                text = name2
            )
        }
    }
}
