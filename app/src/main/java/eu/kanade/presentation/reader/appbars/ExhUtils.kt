package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.util.ThemePreviews

@Composable
fun ExhUtils(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    backgroundColor: Color,
    isAutoScroll: Boolean,
    isAutoScrollEnabled: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,
    onClickAutoScrollHelp: () -> Unit,
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    onClickBoostPage: () -> Unit,
    onClickBoostPageHelp: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(backgroundColor),
    ) {
        AnimatedVisibility(visible = isVisible) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight()
                            .clickable(enabled = isAutoScrollEnabled) { onToggleAutoscroll(!isAutoScroll) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.eh_autoscroll),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                        Switch(
                            checked = isAutoScroll,
                            onCheckedChange = null,
                            enabled = isAutoScrollEnabled,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(3f)) {
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
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                            )
                            AnimatedVisibility(!isAutoScrollEnabled) {
                                Text(
                                    text = stringResource(R.string.eh_autoscroll_freq_invalid),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
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
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.fillMaxWidth(0.5f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onClickRetryAll,
                            modifier = Modifier.weight(3f),
                        ) {
                            Text(
                                text = stringResource(R.string.eh_retry_all),
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
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onClickBoostPage,
                            modifier = Modifier.weight(3f),
                        ) {
                            Text(
                                text = stringResource(R.string.eh_boost_page),
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
            )
        }
    }
}

@Composable
@ThemePreviews
private fun ExhUtilsPreview() {
    TachiyomiTheme {
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
