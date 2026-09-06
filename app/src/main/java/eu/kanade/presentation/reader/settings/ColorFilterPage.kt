package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.Companion.ColorFilterMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.util.waifu2x.Waifu2x
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColorFilterPage(screenModel: ReaderSettingsScreenModel) {
    val customBrightness by screenModel.preferences.customBrightness().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_custom_brightness),
        pref = screenModel.preferences.customBrightness(),
    )

    /*
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    if (customBrightness) {
        val customBrightnessValue by screenModel.preferences.customBrightnessValue().collectAsState()
        SliderItem(
            value = customBrightnessValue,
            valueRange = -75..100,
            steps = 0,
            label = stringResource(MR.strings.pref_custom_brightness),
            onChange = { screenModel.preferences.customBrightnessValue().set(it) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    val colorFilter by screenModel.preferences.colorFilter().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_custom_color_filter),
        pref = screenModel.preferences.colorFilter(),
    )
    if (colorFilter) {
        val colorFilterValue by screenModel.preferences.colorFilterValue().collectAsState()
        SliderItem(
            value = colorFilterValue.red,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_r_value),
            onChange = { newRValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newRValue, RED_MASK, 16)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.green,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_g_value),
            onChange = { newGValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newGValue, GREEN_MASK, 8)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.blue,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_b_value),
            onChange = { newBValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newBValue, BLUE_MASK, 0)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.alpha,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_a_value),
            onChange = { newAValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newAValue, ALPHA_MASK, 24)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        val colorFilterMode by screenModel.preferences.colorFilterMode().collectAsState()
        SettingsChipRow(MR.strings.pref_color_filter_mode) {
            ColorFilterMode.mapIndexed { index, it ->
                FilterChip(
                    selected = colorFilterMode == index,
                    onClick = { screenModel.preferences.colorFilterMode().set(index) },
                    label = { Text(stringResource(it.first)) },
                )
            }
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_grayscale),
        pref = screenModel.preferences.grayscale(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_inverted_colors),
        pref = screenModel.preferences.invertedColors(),
    )

    // KMK --> Image enhancement (upscale)
    val realCuganEnabled by screenModel.preferences.realCuganEnabled().collectAsState()

    CheckboxItem(
        label = stringResource(KMR.strings.reader_image_enhancement),
        checked = realCuganEnabled,
        onClick = {
            screenModel.preferences.realCuganEnabled().set(!realCuganEnabled)
        },
    )
    if (realCuganEnabled) {
        val realCuganModel by screenModel.preferences.realCuganModel().collectAsState()
        val realEsrganStyle by screenModel.preferences.realEsrganStyle().collectAsState()
        val realCuganNoiseLevel by screenModel.preferences.realCuganNoiseLevel().collectAsState()
        val realCuganScale by screenModel.preferences.realCuganScale().collectAsState()
        val processingBackend by screenModel.preferences.realCuganProcessingBackend().collectAsState()
        val npuDeviceAvailable = remember { Waifu2x.isQualcommNpuAvailable() }
        val useQualcommNpu = processingBackend == Waifu2x.PROCESSING_BACKEND_QUALCOMM_NPU && npuDeviceAvailable

        LaunchedEffect(npuDeviceAvailable, processingBackend) {
            if (!npuDeviceAvailable && processingBackend == Waifu2x.PROCESSING_BACKEND_QUALCOMM_NPU) {
                screenModel.preferences.realCuganProcessingBackend().set(Waifu2x.PROCESSING_BACKEND_VULKAN)
            }
        }
        LaunchedEffect(useQualcommNpu, realCuganModel, realCuganScale) {
            if (useQualcommNpu) {
                if (
                    realCuganModel != 0 &&
                    realCuganModel != 1 &&
                    realCuganModel != Waifu2x.MODEL_REAL_ESRGAN_ANIME &&
                    realCuganModel != Waifu2x.MODEL_W2XEX_PHOTO_SMALL &&
                    realCuganModel != Waifu2x.MODEL_SPAN_NOMOSUNI_PHOTO
                ) {
                    screenModel.preferences.realCuganModel().set(0)
                }
                if (
                    (realCuganModel == 1 && realCuganScale !in 2..3) ||
                    (realCuganModel != 1 && realCuganScale != 2)
                ) {
                    screenModel.preferences.realCuganScale().set(2)
                }
            }
        }
        LaunchedEffect(realCuganModel, realCuganNoiseLevel) {
            if (realCuganModel == 1 && realCuganNoiseLevel !in setOf(0, 3, 4)) {
                screenModel.preferences.realCuganNoiseLevel().set(3)
            }
        }

        SettingsChipRow(KMR.strings.reader_processing_backend) {
            FilterChip(
                selected = processingBackend == Waifu2x.PROCESSING_BACKEND_VULKAN,
                onClick = {
                    screenModel.preferences.realCuganProcessingBackend().set(Waifu2x.PROCESSING_BACKEND_VULKAN)
                },
                label = { Text(stringResource(KMR.strings.reader_backend_vulkan)) },
            )
            FilterChip(
                selected = useQualcommNpu,
                onClick = {
                    screenModel.preferences.realCuganProcessingBackend().set(Waifu2x.PROCESSING_BACKEND_QUALCOMM_NPU)
                },
                enabled = npuDeviceAvailable,
                label = { Text(stringResource(KMR.strings.reader_backend_qualcomm_npu)) },
            )
        }

        SettingsChipRow(KMR.strings.reader_model) {
            val models = if (useQualcommNpu) {
                listOf(
                    0 to "Real-CUGAN SE",
                    1 to "Real-CUGAN Pro",
                    Waifu2x.MODEL_REAL_ESRGAN_ANIME to "Real-ESRGAN",
                    Waifu2x.MODEL_W2XEX_PHOTO_SMALL to "W2xEX Photo Small",
                    Waifu2x.MODEL_SPAN_NOMOSUNI_PHOTO to "SPAN NomosUni Photo",
                )
            } else {
                listOf(
                    0 to "Real-CUGAN SE",
                    1 to "Real-CUGAN Pro",
                    Waifu2x.MODEL_REAL_ESRGAN_ANIME to "Real-ESRGAN",
                    3 to "Real-CUGAN Nose",
                    4 to "Waifu2x",
                    5 to "Waifu2x (Fast)",
                    6 to "W2xEX Universal Fast",
                    8 to "W2xEX Omni Mini V2",
                    Waifu2x.MODEL_W2XEX_PHOTO_SMALL to "W2xEX Photo Small",
                    16 to "AnimeJaNai v2 UltraCompact",
                    18 to "sudo UltraCompact",
                    Waifu2x.MODEL_SPAN_NOMOSUNI_PHOTO to "SPAN NomosUni Photo",
                )
            }
            models.map { (modelId, name) ->
                FilterChip(
                    selected = realCuganModel == modelId,
                    onClick = { screenModel.preferences.realCuganModel().set(modelId) },
                    label = { Text(name) },
                )
            }
        }

        if (realCuganModel == Waifu2x.MODEL_REAL_ESRGAN_ANIME) {
            SettingsChipRow(KMR.strings.reader_model_style) {
                listOf(
                    Waifu2x.REAL_ESRGAN_STYLE_ANIME to "Anime",
                    Waifu2x.REAL_ESRGAN_STYLE_PHOTO to "Photo",
                ).map { (style, name) ->
                    FilterChip(
                        selected = realEsrganStyle == style,
                        onClick = { screenModel.preferences.realEsrganStyle().set(style) },
                        label = { Text(name) },
                    )
                }
            }
        }

        if (realCuganModel == 0 || realCuganModel == 1 || realCuganModel == 4 || realCuganModel == 5) {
            val levels = if (realCuganModel == 1) { // Pro only has no-denoise, denoise3x, conservative
                listOf(
                    0 to stringResource(KMR.strings.reader_none),
                    3 to "3x",
                    4 to stringResource(KMR.strings.reader_conservative),
                )
            } else if (realCuganModel == 4) { // Waifu2x
                listOf(0 to "1x", 1 to "2x", 2 to "3x")
            } else if (realCuganModel == 5) { // Waifu2x Fast (UpConv7)
                listOf(0 to stringResource(KMR.strings.reader_none), 1 to "1x", 2 to "2x", 3 to "3x")
            } else { // SE
                listOf(
                    0 to stringResource(KMR.strings.reader_none),
                    1 to "1x",
                    2 to "2x",
                    3 to "3x",
                    4 to stringResource(KMR.strings.reader_conservative),
                )
            }

            SettingsChipRow(KMR.strings.reader_denoise_level) {
                levels.map { (index, name) ->
                    FilterChip(
                        selected = realCuganNoiseLevel == index,
                        onClick = { screenModel.preferences.realCuganNoiseLevel().set(index) },
                        label = { Text(name) },
                    )
                }
            }
        }

        val fixedW2xExScale = Waifu2x.w2xExScaleFor(realCuganModel)
        if (
            (useQualcommNpu && realCuganModel != 1) ||
            realCuganModel == 3 || realCuganModel == 4 || realCuganModel == 5 ||
            (realCuganModel == Waifu2x.MODEL_REAL_ESRGAN_ANIME && realEsrganStyle == Waifu2x.REAL_ESRGAN_STYLE_PHOTO) ||
            fixedW2xExScale == 2
        ) {
            SettingsChipRow(KMR.strings.reader_scale_factor) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = {
                        Text(
                            if (fixedW2xExScale == 4) {
                                "4x"
                            } else {
                                stringResource(KMR.strings.reader_scale_fixed_2x)
                            },
                        )
                    },
                )
            }
        } else if (fixedW2xExScale == 4) {
            SettingsChipRow(KMR.strings.reader_scale_factor) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("4x") },
                )
            }
        } else if (realCuganModel == 1) { // Pro only supports 2x, 3x
            SettingsChipRow(KMR.strings.reader_scale_factor) {
                listOf(2, 3).map { scale ->
                    FilterChip(
                        selected = realCuganScale == scale,
                        onClick = { screenModel.preferences.realCuganScale().set(scale) },
                        label = { Text("${scale}x") },
                    )
                }
            }
        } else {
            SettingsChipRow(KMR.strings.reader_scale_factor) {
                listOf(2, 3, 4).map { scale ->
                    FilterChip(
                        selected = realCuganScale == scale,
                        onClick = { screenModel.preferences.realCuganScale().set(scale) },
                        label = { Text("${scale}x") },
                    )
                }
            }
        }

        SettingsChipRow(KMR.strings.reader_preload_pages) {
            listOf(1, 2, 3, 5, 8).map { size ->
                val realCuganPreloadSize by screenModel.preferences.realCuganPreloadSize().collectAsState()
                FilterChip(
                    selected = realCuganPreloadSize == size,
                    onClick = { screenModel.preferences.realCuganPreloadSize().set(size) },
                    label = { Text(stringResource(KMR.strings.reader_preload_pages_value, size)) },
                )
            }
        }

        if (!useQualcommNpu) {
            SettingsChipRow(KMR.strings.reader_gpu_performance_mode) {
                val performanceMode by screenModel.preferences.realCuganPerformanceMode().collectAsState()
                listOf(
                    0 to stringResource(KMR.strings.reader_gpu_performance_high),
                    1 to stringResource(KMR.strings.reader_gpu_performance_balanced),
                    2 to stringResource(KMR.strings.reader_gpu_performance_power_saving),
                ).map { (value, name) ->
                    FilterChip(
                        selected = performanceMode == value,
                        onClick = { screenModel.preferences.realCuganPerformanceMode().set(value) },
                        label = { Text(name) },
                    )
                }
            }

            SettingsChipRow(KMR.strings.reader_tile_size) {
                val tileSize by screenModel.preferences.realCuganTileSize().collectAsState()
                listOf(64, 96, 128, 192, 256).map { value ->
                    FilterChip(
                        selected = tileSize == value,
                        onClick = { screenModel.preferences.realCuganTileSize().set(value) },
                        label = { Text(value.toString()) },
                    )
                }
            }
        }

        val precision by screenModel.preferences.realCuganPrecision().collectAsState()
        val supportedPrecisions = if (useQualcommNpu) {
            if (
                realCuganModel == 0 ||
                realCuganModel == 1 ||
                realCuganModel == Waifu2x.MODEL_REAL_ESRGAN_ANIME ||
                realCuganModel == Waifu2x.MODEL_W2XEX_PHOTO_SMALL ||
                realCuganModel == Waifu2x.MODEL_SPAN_NOMOSUNI_PHOTO
            ) {
                listOf(0, 2)
            } else {
                listOf(0)
            }
        } else {
            listOf(0, 1, 2, 3)
        }
        LaunchedEffect(useQualcommNpu, realCuganModel, precision) {
            if (precision !in supportedPrecisions) {
                screenModel.preferences.realCuganPrecision().set(supportedPrecisions.first())
            }
        }
        SettingsChipRow(KMR.strings.reader_precision) {
            listOf(
                0 to stringResource(KMR.strings.reader_precision_fp16),
                1 to stringResource(KMR.strings.reader_precision_fp32),
                2 to stringResource(KMR.strings.reader_precision_int8),
                3 to stringResource(KMR.strings.reader_precision_bf16),
            ).filter { (value, _) -> value in supportedPrecisions }.map { (value, name) ->
                FilterChip(
                    selected = precision == value,
                    onClick = { screenModel.preferences.realCuganPrecision().set(value) },
                    label = { Text(name) },
                )
            }
        }

        if (!useQualcommNpu && precision == 0) {
            CheckboxItem(
                label = stringResource(KMR.strings.reader_fp16_arithmetic),
                pref = screenModel.preferences.realCuganFp16Arithmetic(),
            )
        }

        val processMaxWidth by screenModel.preferences.realCuganMaxSizeWidth().collectAsState()
        val processMaxHeight by screenModel.preferences.realCuganMaxSizeHeight().collectAsState()
        ResolutionLimitFields(
            heading = stringResource(KMR.strings.reader_processing_resolution),
            width = processMaxWidth,
            height = processMaxHeight,
            onWidthChange = { screenModel.preferences.realCuganMaxSizeWidth().set(it) },
            onHeightChange = { screenModel.preferences.realCuganMaxSizeHeight().set(it) },
        )

        val skipMaxWidth by screenModel.preferences.realCuganSkipMaxSizeWidth().collectAsState()
        val skipMaxHeight by screenModel.preferences.realCuganSkipMaxSizeHeight().collectAsState()
        ResolutionLimitFields(
            heading = stringResource(KMR.strings.reader_max_resolution),
            width = skipMaxWidth,
            height = skipMaxHeight,
            onWidthChange = { screenModel.preferences.realCuganSkipMaxSizeWidth().set(it) },
            onHeightChange = { screenModel.preferences.realCuganSkipMaxSizeHeight().set(it) },
        )

        CheckboxItem(
            label = stringResource(KMR.strings.reader_show_processing_status),
            pref = screenModel.preferences.realCuganShowStatus(),
        )
    }
    // KMK <-- Image enhancement (upscale)
}

// KMK -->
@Composable
private fun ResolutionLimitFields(
    heading: String,
    width: Int,
    height: Int,
    onWidthChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
) {
    Column {
        HeadingItem(heading)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsItemsPaddings.Horizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ResolutionNumberField(
                modifier = Modifier.weight(1f),
                value = width,
                onValueChange = onWidthChange,
                label = stringResource(KMR.strings.reader_resolution_width),
            )
            ResolutionNumberField(
                modifier = Modifier.weight(1f),
                value = height,
                onValueChange = onHeightChange,
                label = stringResource(KMR.strings.reader_resolution_height),
            )
        }
    }
}

@Composable
private fun ResolutionNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toResolutionText()) }

    LaunchedEffect(value) {
        val normalized = value.toResolutionText()
        if (text != normalized && (text.toIntOrNull() ?: 0) != value) {
            text = normalized
        }
    }

    OutlinedTextField(
        modifier = modifier,
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter(Char::isDigit)
            text = filtered
            onValueChange(filtered.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

private fun Int.toResolutionText(): String = if (this == 0) "" else toString()
// KMK <--

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}
private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
