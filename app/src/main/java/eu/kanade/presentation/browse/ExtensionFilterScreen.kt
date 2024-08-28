package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.interactor.GetExtensionLanguages.Companion.getLanguageIconID
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun ExtensionFilterScreen(
    navigateUp: () -> Unit,
    state: ExtensionFilterState.Success,
    onClickToggle: (String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extensions),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        ExtensionFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLang = onClickToggle,
        )
    }
}

@Composable
private fun ExtensionFilterContent(
    contentPadding: PaddingValues,
    state: ExtensionFilterState.Success,
    onClickLang: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = contentPadding,
        // KMK -->
        modifier = Modifier
            .padding(start = MaterialTheme.padding.small),
        // KMK <--
    ) {
        items(state.languages) { language ->
            // KMK -->
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val iconResId = getLanguageIconID(language) ?: R.drawable.globe
                Icon(
                    painter = painterResource(id = iconResId),
                    tint = Color.Unspecified,
                    contentDescription = language,
                    modifier = Modifier
                        .width(48.dp)
                        .height(32.dp),
                )
                // KMK <--
                SwitchPreferenceWidget(
                    modifier = Modifier.animateItem(),
                    title = LocaleHelper.getSourceDisplayName(language, context) +
                        // KMK -->
                        (
                            " (${LocaleHelper.getDisplayName(language)})"
                                .takeIf { language !in listOf("all", "other") } ?: ""
                            ),
                    // KMK <--
                    checked = language in state.enabledLanguages,
                    onCheckedChanged = { onClickLang(language) },
                )
            }
        }
    }
}

// KMK -->
@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun ExtensionFilterContentPreview() {
    ExtensionFilterContent(
        contentPadding = PaddingValues(),
        state = ExtensionFilterState.Success(
            languages = persistentListOf(
                "all",
                "other",
                "af",
                "am",
                "ar",
                "az",
                "be",
                "bg",
                "bn",
                "br",
                "bs",
                "ca",
                "ceb",
                "co",
                "cs",
                "da",
                "de",
                "el",
                "en",
                "eo",
                "es-419",
                "es",
                "et",
                "eu",
                "fa",
                "fi",
                "fil",
                "fo",
                "fr",
                "ga",
                "gn",
                "gu",
                "ha",
                "he",
                "hi",
                "hr",
                "ht",
                "hu",
                "hy",
                "id",
                "ig",
                "is",
                "it",
                "ja",
                "jv",
                "ka",
                "kk",
                "km",
                "kn",
                "ko",
                "kr",
                "ku",
                "ky",
                "la",
                "lb",
                "lmo",
                "lo",
                "lt",
                "lv",
                "mg",
                "mi",
                "mk",
                "ml",
                "mn",
                "mo",
                "mr",
                "ms",
                "mt",
                "my",
                "ne",
                "nl",
                "no",
                "ny",
                "pl",
                "ps",
                "pt-BR",
                "pt-PT",
                "pt",
                "rm",
                "ro",
                "ru",
                "sd",
                "sh",
                "si",
                "sk",
                "sl",
                "sm",
                "sn",
                "so",
                "sq",
                "sr",
                "st",
                "sv",
                "sw",
                "ta",
                "te",
                "tg",
                "th",
                "ti",
                "tk",
                "tl",
                "to",
                "tr",
                "uk",
                "ur",
                "uz",
                "vec",
                "vi",
                "yo",
                "zh-Hans",
                "zh-Hant",
                "zh",
                "zu",
            ),
            enabledLanguages = persistentSetOf("en"),
        ),
        onClickLang = {},
    )
}
// KMK <--
