package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Slider
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExhUtils(
    isVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    backgroundColor: Color,
    isAutoScroll: Boolean,
    isAutoScrollEnabled: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,
    onClickAutoScrollHelp: () -> Unit,
    // KMK -->
    /** The auto-scroll is controlled by its speed instead of an interval. */
    useAutoScrollSpeed: Boolean = false,
    autoScrollSpeed: Int = 0,
    onSetAutoScrollSpeed: (Int) -> Unit = {},
    // KMK <--
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    onClickBoostPage: () -> Unit,
    onClickBoostPageHelp: () -> Unit,
    modifier: Modifier = Modifier,
    // KMK -->
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    // KMK <--
) {
    // KMK -->
    // The interval is not used in speed mode, so it can't make the switch unavailable
    val isAutoScrollEnabled = isAutoScrollEnabled || useAutoScrollSpeed
    // KMK <--
    Column(
        modifier
            .fillMaxWidth()
            // KMK -->
            .background(backgroundColor)
            // Background stays edge-to-edge, only the content is inset
            .windowInsetsPadding(windowInsets),
        // KMK <--
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = isVisible) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight()
                            .padding(5.dp)
                            .clickable(enabled = isAutoScrollEnabled) { onToggleAutoscroll(!isAutoScroll) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(3f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(SYMR.strings.eh_autoscroll),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.fillMaxWidth(0.75f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Switch(
                                checked = isAutoScroll,
                                onCheckedChange = null,
                                enabled = isAutoScrollEnabled,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(0.9f).padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(3f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // KMK -->
                            if (useAutoScrollSpeed) {
                                Text(
                                    text = stringResource(KMR.strings.webtoon_auto_scroll_speed_value, autoScrollSpeed),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Slider(
                                    value = autoScrollSpeed,
                                    onValueChange = onSetAutoScrollSpeed,
                                    valueRange = ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_RANGE,
                                    steps = ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_SLIDER_STEPS,
                                )
                            } else {
                                // KMK <--
                                var autoScrollFrequencyState by remember {
                                    mutableStateOf(autoScrollFrequency)
                                }
                                TextField(
                                    value = autoScrollFrequencyState,
                                    onValueChange = {
                                        autoScrollFrequencyState = it
                                        onSetAutoScrollFrequency(it)
                                    },
                                    isError = !isAutoScrollEnabled,
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.fillMaxWidth(0.75f),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                    ),
                                )
                                AnimatedVisibility(!isAutoScrollEnabled) {
                                    Text(
                                        text = stringResource(SYMR.strings.eh_autoscroll_freq_invalid),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                // KMK -->
                            }
                            // KMK <--
                        }
                        // KMK -->
                        if (!useAutoScrollSpeed) {
                            // KMK <--
                            TextButton(
                                onClick = onClickAutoScrollHelp,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "?",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            // KMK -->
                        }
                        // KMK <--
                    }
                }
                Row(
                    Modifier.fillMaxWidth(0.9f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.fillMaxWidth(0.5f).padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onClickRetryAll,
                            modifier = Modifier.weight(3f),
                        ) {
                            Text(
                                text = stringResource(SYMR.strings.eh_retry_all),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                            )
                        }
                        TextButton(
                            onClick = onClickRetryAllHelp,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "?",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(0.9f).padding(5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onClickBoostPage,
                            modifier = Modifier.weight(3f),
                        ) {
                            Text(
                                text = stringResource(SYMR.strings.eh_boost_page),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.SansSerif,
                            )
                        }
                        TextButton(
                            onClick = onClickBoostPageHelp,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "?",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = { onSetExhUtilsVisibility(!isVisible) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (isVisible) {
                    Icons.Outlined.KeyboardArrowUp
                } else {
                    Icons.Outlined.KeyboardArrowDown
                },
                contentDescription = null,
                // KMK -->
                tint = MaterialTheme.colorScheme.primary,
                // KMK <--
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun ExhUtilsPreview() {
    Surface {
        ExhUtils(
            isVisible = true,
            onSetExhUtilsVisibility = {},
            backgroundColor = Color.Black,
            isAutoScroll = true,
            isAutoScrollEnabled = true,
            onToggleAutoscroll = {},
            autoScrollFrequency = "3.0",
            onSetAutoScrollFrequency = {},
            onClickAutoScrollHelp = {},
            onClickBoostPage = {},
            onClickBoostPageHelp = {},
            onClickRetryAll = {},
            onClickRetryAllHelp = {},
        )
    }
}
