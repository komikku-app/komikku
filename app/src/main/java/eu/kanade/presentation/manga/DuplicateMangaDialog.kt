package eu.kanade.presentation.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun DuplicateMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: () -> Unit,
    onMigrate: () -> Unit,
    // KMK -->
    duplicate: Manga,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    val minHeight = LocalPreferenceMinHeight.current

    // KMK -->
    val usePanoramaCover by Injekt.get<UiPreferences>().usePanoramaCover().collectAsState()
    val coverRatio = remember { mutableFloatStateOf(1f) }
    val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
    // KMK <--

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    vertical = TabbedDialogPaddings.Vertical,
                    horizontal = TabbedDialogPaddings.Horizontal,
                )
                .fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.padding(TitlePadding),
                text = stringResource(MR.strings.are_you_sure),
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = stringResource(MR.strings.confirm_add_duplicate_manga),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(PaddingSize))

            // KMK -->
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                if (usePanoramaCover && coverIsWide) {
                    MangaCover.Panorama(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .heightIn(max = 150.dp),
                        data = duplicate,
                        onCoverLoaded = { _, result ->
                            val image = result.result.image
                            coverRatio.floatValue = image.height.toFloat() / image.width
                        },
                    )
                } else {
                    MangaCover.Book(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 150.dp),
                        data = duplicate,
                        onCoverLoaded = { _, result ->
                            val image = result.result.image
                            coverRatio.floatValue = image.height.toFloat() / image.width
                        },
                    )
                }
            }
            // KMK <--

            TextPreferenceWidget(
                title = stringResource(MR.strings.action_show_manga),
                icon = Icons.Outlined.Book,
                onPreferenceClick = {
                    onDismissRequest()
                    onOpenManga()
                },
            )

            HorizontalDivider()

            TextPreferenceWidget(
                title = stringResource(MR.strings.action_migrate_duplicate),
                icon = Icons.Outlined.SwapVert,
                onPreferenceClick = {
                    onDismissRequest()
                    onMigrate()
                },
            )

            HorizontalDivider()

            TextPreferenceWidget(
                title = stringResource(MR.strings.action_add_anyway),
                icon = Icons.Outlined.Add,
                onPreferenceClick = {
                    onDismissRequest()
                    onConfirm()
                },
            )

            Row(
                modifier = Modifier
                    .sizeIn(minHeight = minHeight)
                    .clickable { onDismissRequest.invoke() }
                    .padding(ButtonPadding)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier
                            .padding(vertical = 8.dp),
                        text = stringResource(MR.strings.action_cancel),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

private val PaddingSize = 16.dp

private val ButtonPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
private val TitlePadding = PaddingValues(bottom = 16.dp, top = 8.dp)

// KMK -->
@Composable
fun DuplicateMangasDialog(
    onDismissRequest: () -> Unit,
    onAllowAllDuplicate: () -> Unit,
    onSkipAllDuplicate: () -> Unit,
    onOpenManga: () -> Unit,
    onAllowDuplicate: () -> Unit,
    onSkipDuplicate: () -> Unit,
    stopRunning: () -> Unit,
    mangaName: String,
    duplicate: Manga,
) {
    val usePanoramaCover by Injekt.get<UiPreferences>().usePanoramaCover().collectAsState()
    val coverRatio = remember { mutableFloatStateOf(1f) }
    val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama

    AlertDialog(
        onDismissRequest = {
            stopRunning()
            onDismissRequest()
        },
        title = {
            Text(text = mangaName)
        },
        text = {
            Column {
                Text(text = stringResource(MR.strings.confirm_add_duplicate_manga))

                Spacer(Modifier.height(PaddingSize))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    if (usePanoramaCover && coverIsWide) {
                        MangaCover.Panorama(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .heightIn(max = 150.dp),
                            data = duplicate,
                            onCoverLoaded = { _, result ->
                                val image = result.result.image
                                coverRatio.floatValue = image.height.toFloat() / image.width
                            },
                        )
                    } else {
                        MangaCover.Book(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .widthIn(max = 150.dp),
                            data = duplicate,
                            onCoverLoaded = { _, result ->
                                val image = result.result.image
                                coverRatio.floatValue = image.height.toFloat() / image.width
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                FlowColumn {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onAllowDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(KMR.strings.action_allow_duplicate_manga))
                    }

                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onAllowAllDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(KMR.strings.action_allow_all_duplicate_manga))
                    }
                }

                FlowColumn {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onSkipDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(KMR.strings.action_skip_duplicate_manga))
                    }

                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onSkipAllDuplicate()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(KMR.strings.action_skip_all_duplicate_manga))
                    }
                }

                FlowColumn {
                    TextButton(
                        onClick = {
                            stopRunning()
                            onDismissRequest()
                            onOpenManga()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_show_manga))
                    }

                    TextButton(
                        onClick = {
                            stopRunning()
                            onDismissRequest()
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                }
            }
        },
    )
}
// KMK <--
