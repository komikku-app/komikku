package app.komikku.desktop.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * Reader settings panel, bound directly to the borrowed `ReaderPreferences` (same keys as Android) plus
 * the manga's own reading mode / orientation flags (stored like Android through `SetMangaViewerFlags`;
 * orientation has no effect on a desktop window).
 */
@Composable
fun ReaderSettingsPanel(
    state: ReaderModel.State,
    preferences: ReaderPreferences,
    onReadingMode: (ReadingMode) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 6.dp, modifier = modifier.width(360.dp).fillMaxHeight().testTag("reader-settings")) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Header(MR.strings.pref_category_reading_mode)
            ReadingMode.entries.filter { it != ReadingMode.DEFAULT }.forEach { mode ->
                RadioRow(stringResource(mode.stringRes), selected = state.readingMode == mode, tag = "reading-mode-${mode.name}") {
                    onReadingMode(mode)
                }
            }
            Divider()
            Header(MR.strings.pref_image_scale_type)
            val scaleType by preferences.imageScaleType().collectAsState()
            ReaderPreferences.ImageScaleType.forEachIndexed { i, label ->
                RadioRow(stringResource(label), selected = scaleType == i + 1) { preferences.imageScaleType().set(i + 1) }
            }
            val pageLayout by preferences.pageLayout().collectAsState()
            SwitchRow(
                stringResource(SYMR.strings.double_pages),
                checked = pageLayout == PagerConfig.PageLayout.DOUBLE_PAGES,
            ) { preferences.pageLayout().set(if (it) PagerConfig.PageLayout.DOUBLE_PAGES else PagerConfig.PageLayout.SINGLE_PAGE) }
            val padding by preferences.webtoonSidePadding().collectAsState()
            Text(
                stringResource(MR.strings.pref_webtoon_side_padding) + ": $padding%",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Slider(
                value = padding.toFloat(),
                onValueChange = { preferences.webtoonSidePadding().set(it.toInt()) },
                valueRange = 0f..25f,
                steps = 4,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Divider()
            Header(MR.strings.pref_reader_theme)
            val theme by preferences.readerTheme().collectAsState()
            listOf(MR.strings.white_background, MR.strings.black_background, MR.strings.gray_background, MR.strings.automatic_background)
                .forEachIndexed { i, label -> RadioRow(stringResource(label), selected = theme == i) { preferences.readerTheme().set(i) } }
            BooleanPref(MR.strings.pref_show_page_number, preferences.showPageNumber())
            Divider()
            Header(MR.strings.pref_category_display)
            BooleanPref(MR.strings.pref_custom_brightness, preferences.customBrightness())
            val brightness by preferences.customBrightnessValue().collectAsState()
            Slider(
                value = brightness.toFloat(),
                onValueChange = { preferences.customBrightnessValue().set(it.toInt()) },
                valueRange = -75f..0f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            BooleanPref(MR.strings.pref_custom_color_filter, preferences.colorFilter())
            val color by preferences.colorFilterValue().collectAsState()
            listOf(
                MR.strings.color_filter_r_value to 16,
                MR.strings.color_filter_g_value to 8,
                MR.strings.color_filter_b_value to 0,
                MR.strings.color_filter_a_value to 24,
            ).forEach { (label, shift) ->
                val channel = (color ushr shift) and 0xFF
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(label), modifier = Modifier.width(64.dp))
                    Slider(
                        value = channel.toFloat(),
                        onValueChange = { v -> preferences.colorFilterValue().set((color and (0xFF shl shift).inv()) or (v.toInt() shl shift)) },
                        valueRange = 0f..255f,
                    )
                }
            }
            Header(MR.strings.pref_color_filter_mode)
            val filterMode by preferences.colorFilterMode().collectAsState()
            ReaderPreferences.ColorFilterMode.forEachIndexed { i, (label, _) ->
                RadioRow(stringResource(label), selected = filterMode == i) { preferences.colorFilterMode().set(i) }
            }
            BooleanPref(MR.strings.pref_grayscale, preferences.grayscale())
            BooleanPref(MR.strings.pref_inverted_colors, preferences.invertedColors())
            Divider()
            Header(MR.strings.rotation_type)
            ReaderOrientation.entries.filter { it != ReaderOrientation.DEFAULT }.forEach { orientation ->
                RadioRow(stringResource(orientation.stringRes), selected = state.orientation == orientation) { onOrientation(orientation) }
            }
            Divider()
            Header(MR.strings.pref_category_general)
            BooleanPref(MR.strings.pref_skip_read_chapters, preferences.skipRead())
            BooleanPref(MR.strings.pref_skip_filtered_chapters, preferences.skipFiltered())
            BooleanPref(MR.strings.pref_skip_dupe_chapters, preferences.skipDupe())
        }
    }
}

@Composable
private fun Header(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(vertical = 8.dp))

@Composable
private fun RadioRow(label: String, selected: Boolean, tag: String? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .let { if (tag != null) it.testTag(tag) else it },
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(label)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BooleanPref(label: StringResource, preference: Preference<Boolean>) {
    val value by preference.collectAsState()
    SwitchRow(stringResource(label), value) { preference.set(it) }
}
