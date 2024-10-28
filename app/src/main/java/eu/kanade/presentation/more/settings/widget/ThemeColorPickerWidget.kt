package eu.kanade.presentation.more.settings.widget

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import eu.kanade.domain.ui.model.AppTheme
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.ButtonDefaults.buttonColors
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalStdlibApi::class)
@Composable
internal fun ThemeColorPickerWidget(
    initialColor: Color,
    controller: ColorPickerController,
    onItemClick: (Color, AppTheme) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hexCode by remember { mutableStateOf(initialColor.toArgb().toHexString()) }

    val wheelSize = with(LocalDensity.current) { 20.dp.toPx().roundToInt() }
    val wheelStrokeWidth = with(LocalDensity.current) { 2.dp.toPx() }

    // Remember a wheel bitmap
    val wheelBitmap = remember(wheelSize, wheelStrokeWidth) {
        val bitmap = Bitmap.createBitmap(wheelSize, wheelSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = wheelStrokeWidth
            isAntiAlias = true
        }

        // Draw the circle for wheel indicator
        canvas.drawCircle(
            wheelSize / 2f,
            wheelSize / 2f,
            wheelSize / 2f - wheelStrokeWidth,
            paint,
        )
        bitmap.asImageBitmap()
    }

    BasePreferenceWidget(
        subcomponent = {
            val scope = rememberCoroutineScope()
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.large)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .padding(
                            vertical = MaterialTheme.padding.small,
                        ),
                ) {
                    HsvColorPicker(
                        modifier = Modifier
                            .size(300.dp),
                        controller = controller,
                        wheelImageBitmap = wheelBitmap,
                        initialColor = initialColor,
                        onColorChanged = { colorEnvelope: ColorEnvelope ->
                            hexCode = colorEnvelope.hexCode
                            selectedColor = colorEnvelope.color
                        },
                    )
                }
                BrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.small)
                        .height(24.dp),
                    controller = controller,
                    borderRadius = 12.dp,
                    wheelImageBitmap = wheelBitmap,
                    initialColor = initialColor,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Column {
                        Text(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = "#${initialColor.toArgb().toHexString()}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = initialColor,
                            ),
                        )
                        AlphaTile(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                            selectedColor = initialColor,
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.CenterVertically)
                            .padding(MaterialTheme.padding.small)
                            .padding(top = MaterialTheme.padding.small),
                        contentDescription = null,
                    )

                    Column {
                        Text(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = "#$hexCode",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = selectedColor,
                            ),
                        )
                        AlphaTile(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                            controller = controller,
                        )
                    }
                }
                Button(
                    onClick = {
                        onItemClick(selectedColor, AppTheme.CUSTOM)
                    },
                    colors = buttonColors(
                        containerColor = animateColorAsState(
                            label = "animateColorAsState",
                            targetValue = MaterialTheme.colorScheme.primary,
                            animationSpec = tween(durationMillis = 500),
                        ).value,
                    ),
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .padding(vertical = MaterialTheme.padding.medium)
                        .fillMaxWidth()
                        .height(48.dp),
                    content = {
                        Text(text = stringResource(KMR.strings.action_confirm_color))
                    },
                )

                val colorsPalette = mapOf(
                    KMR.strings.custom_theme_palette_sunset to listOf(
                        Color(0xFF3e9cbf),
                        Color(0xFFa7ecf2),
                        Color(0xFFf2c43d),
                        Color(0xFFf17c37),
                        Color(0xFFf26d50),
                    ),
                    KMR.strings.custom_theme_palette_outrun to listOf(
                        Color(0xFFfffc40),
                        Color(0xFFfaba61),
                        Color(0xFFff8172),
                        Color(0xFFff2fa9),
                        Color(0xFF3a579a),
                    ),
                    KMR.strings.custom_theme_palette_raspberry to listOf(
                        Color(0xFF730517),
                        Color(0xFFf44560),
                        Color(0xFF44d1df),
                        Color(0xFF32a4a7),
                        Color(0xFF1e7069),
                    ),
                    KMR.strings.custom_theme_palette_cathode to listOf(
                        Color(0xFFa8216b),
                        Color(0xFFf1184c),
                        Color(0xFFf36943),
                        Color(0xFFf7dc66),
                        Color(0xFF2e9599),
                    ),
                    KMR.strings.custom_theme_palette_bubblegum to listOf(
                        Color(0xFFf8cd82),
                        Color(0xFFf65b74),
                        Color(0xFFf72078),
                        Color(0xFF23b0bd),
                        Color(0xFF0df7db),
                    ),
                    KMR.strings.custom_theme_palette_springfield to listOf(
                        Color(0xFF89d1dc),
                        Color(0xFFf89cfa),
                        Color(0xFFc386f1),
                        Color(0xFFf0d689),
                        Color(0xFFaff28b),
                    ),
                    KMR.strings.custom_theme_palette_spectral to listOf(
                        Color(0xFF471337),
                        Color(0xFFb13254),
                        Color(0xFFff5349),
                        Color(0xFFff7249),
                        Color(0xFFff9248),
                    ),
                    KMR.strings.custom_theme_palette_night_at_the_beach to listOf(
                        Color(0xFF262335),
                        Color(0xFF503c52),
                        Color(0xFF9f6c66),
                        Color(0xFFd4896a),
                        Color(0xFFffbb6c),
                    ),
                    KMR.strings.custom_theme_palette_casual to listOf(
                        Color(0xFF4f6a8f),
                        Color(0xFF88a2bc),
                        Color(0xFFf0dbb0),
                        Color(0xFFefb680),
                        Color(0xFFd99477),
                    ),
                    KMR.strings.custom_theme_palette_patagonia to listOf(
                        Color(0xFF202e32),
                        Color(0xFF85937a),
                        Color(0xFF586c5c),
                        Color(0xFFa9af90),
                        Color(0xFFdfdcb9),
                    ),
                    KMR.strings.custom_theme_palette_lcd to listOf(
                        Color(0xFF0f370e),
                        Color(0xFF30622f),
                        Color(0xFF8bad0d),
                        Color(0xFF9bbc0e),
                    ),
                    KMR.strings.custom_theme_palette_pop to listOf(
                        Color(0xFF00ff3f),
                        Color(0xFF35b5ff),
                        Color(0xFFff479c),
                        Color(0xFFfffb38),
                    ),
                    KMR.strings.custom_theme_palette_pico to listOf(
                        Color(0xFF1d2b53),
                        Color(0xFF7e2453),
                        Color(0xFF008751),
                        Color(0xFFab5236),
                        Color(0xFF5f574e),
                        Color(0xFFc2c3c7),
                        Color(0xFFff004d),
                        Color(0xFFffa300),
                        Color(0xFFffec27),
                        Color(0xFF00e536),
                        Color(0xFF29aeff),
                        Color(0xFF83769c),
                        Color(0xFFff77a8),
                        Color(0xFFffcdaa),
                    ),
                )

                colorsPalette.forEach { (title, colors) ->
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(top = MaterialTheme.padding.medium),
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = color,
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .height(48.dp)
                                    .aspectRatio(1f)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            controller.selectByColor(color, true)
                                            scope.launch {
                                                scrollState.animateScrollTo(0)
                                            }
                                        },
                                    ),
                                content = { },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun ThemeColorPickerWidgetPreview() {
    ThemeColorPickerWidget(
        initialColor = Color(0xFFDF0090),
        controller = ColorPickerController(),
        onItemClick = { _, _ -> },
    )
}
