package app.komikku.desktop.ui.browse

import android.content.Context
import android.widget.EditText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.preference.CheckBoxPreference
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import androidx.preference.children
import app.komikku.desktop.compat.NOT_SET
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A configurable source's settings (`ConfigurableSource.setupPreferenceScreen`), rendered in Compose. */
class SourcePreferencesScreen(private val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val source = remember { Injekt.get<SourceManager>().get(sourceId) }
        BrowseScaffold(title = source?.name.orEmpty(), onBack = navigator::pop) {
            if (source is ConfigurableSource) {
                SourcePreferencesContent(source)
            } else {
                EmptyScreen(stringRes = KMR.strings.desktop_source_not_installed)
            }
        }
    }
}

/**
 * Builds the source's preference screen exactly like Android's `SourcePreferencesScreen` (a
 * `PreferenceManager` over the source's own preferences, `source_<id>`) and renders it with
 * [PreferenceScreenContent].
 */
@Composable
fun SourcePreferencesContent(source: ConfigurableSource, context: Context = LocalContext.current) {
    val screen = remember(source) {
        val manager = PreferenceManager(context).apply { sharedPreferencesName = source.preferenceKey() }
        manager.createPreferenceScreen(context).also { screen ->
            source.setupPreferenceScreen(screen)
            manager.setPreferences(screen)
        }
    }
    PreferenceScreenContent(screen)
}

/**
 * Compose renderer of the compat `androidx.preference` model: categories, switches / check boxes
 * (`TwoStatePreference`), single and multi-select lists, edit texts (with the extension's
 * `OnBindEditTextListener` input type and text watchers) and plain clickable preferences. Values
 * go through `callChangeListener` like AndroidX, so the extension can veto them; the screen
 * recomposes on every preference change (`PreferenceManager.addPreferenceChangeObserver`).
 */
@Composable
fun PreferenceScreenContent(screen: PreferenceScreen) {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(screen) {
        val manager = screen.preferenceManager
        val observer = PreferenceManager.PreferenceChangeObserver { version++ }
        manager?.addPreferenceChangeObserver(observer)
        onDispose { manager?.removePreferenceChangeObserver(observer) }
    }
    var dialog by remember { mutableStateOf<DialogPreference?>(null) }

    // Read `version` so the list is rebuilt after changes (summaries, visibility, dependencies)
    val rows = remember(screen, version) { flatten(screen) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows) { index, (preference, depth) ->
            PreferenceRow(preference, depth, index, onOpenDialog = { dialog = it }, onChanged = { version++ })
        }
    }

    dialog?.let { preference ->
        PreferenceDialog(preference, onDismiss = { dialog = null }, onChanged = { version++ })
    }
}

private fun flatten(group: PreferenceGroup, depth: Int = 0): List<Pair<Preference, Int>> =
    group.children.filter { it.isVisible }.flatMap { preference ->
        if (preference is PreferenceGroup) {
            listOf(preference to depth) + flatten(preference, depth + 1)
        } else {
            listOf(preference to depth)
        }
    }.toList()

@Composable
private fun PreferenceRow(
    preference: Preference,
    depth: Int,
    index: Int,
    onOpenDialog: (DialogPreference) -> Unit,
    onChanged: () -> Unit,
) {
    if (preference is PreferenceCategory) {
        ListGroupHeader(text = preference.title?.toString().orEmpty())
        return
    }
    val enabled = preference.isEnabled
    val title = preference.title?.toString().orEmpty()
    val summary = summaryText(preference)
    val modifier = Modifier
        .testTag("preference-${preference.key ?: index}")
        .clickable(enabled = enabled && preference.isSelectable) {
            when (preference) {
                is TwoStatePreference -> {
                    preference.performClick()
                    onChanged()
                }
                is DialogPreference -> onOpenDialog(preference)
                else -> {
                    preference.performClick()
                    onChanged()
                }
            }
        }
        .padding(start = (depth * 8).dp)
    ListItem(
        modifier = modifier,
        headlineContent = { Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline) },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = when (preference) {
            is CheckBoxPreference -> {
                { Checkbox(checked = preference.isChecked, onCheckedChange = null, enabled = enabled) }
            }
            is TwoStatePreference -> {
                { Switch(checked = preference.isChecked, onCheckedChange = null, enabled = enabled) }
            }
            else -> null
        },
    )
}

@Composable
private fun summaryText(preference: Preference): String? {
    val summary = when (preference) {
        is TwoStatePreference -> (if (preference.isChecked) preference.summaryOn else preference.summaryOff) ?: preference.summary
        else -> preference.summary
    }?.toString()
    return if (summary == NOT_SET) stringResource(KMR.strings.desktop_pref_not_set) else summary?.takeIf { it.isNotEmpty() }
}

@Composable
private fun PreferenceDialog(preference: DialogPreference, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val title = (preference.dialogTitle ?: preference.title)?.toString().orEmpty()
    fun commit(newValue: Any?, apply: () -> Unit) {
        if (preference.callChangeListener(newValue)) apply()
        onChanged()
        onDismiss()
    }
    when (preference) {
        is ListPreference -> {
            val entries = preference.entries.orEmpty()
            val values = preference.entryValues.orEmpty()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        entries.forEachIndexed { index, entry ->
                            val value = values.getOrNull(index)?.toString()
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { commit(value) { preference.value = value } },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = preference.value == value, onClick = null)
                                Text(entry.toString(), modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
            )
        }
        is MultiSelectListPreference -> {
            val entries = preference.entries.orEmpty()
            val values = preference.entryValues.orEmpty().map { it.toString() }
            var selected by remember { mutableStateOf(preference.values) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        entries.forEachIndexed { index, entry ->
                            val value = values.getOrNull(index) ?: return@forEachIndexed
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = if (value in selected) selected - value else selected + value
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = value in selected, onCheckedChange = null)
                                Text(entry.toString(), modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { commit(selected) { preference.values = selected } }) { Text(stringResource(MR.strings.action_ok)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
            )
        }
        is EditTextPreference -> {
            // The extension configures input type and validation on an EditText holder
            val editText = remember {
                EditText(preference.context).apply {
                    setText(preference.text)
                    preference.getOnBindEditTextListener()?.onBindEditText(this)
                }
            }
            var text by remember { mutableStateOf(editText.getText().toString()) }
            var error by remember { mutableStateOf(editText.error?.toString()) }
            val inputClass = editText.inputType and 0xf
            val variation = editText.inputType and 0xff0
            val isPassword = variation == 0x80 || variation == 0x90 || variation == 0xe0 || (inputClass == 2 && variation == 0x10)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Column {
                        preference.dialogMessage?.let { Text(it.toString(), modifier = Modifier.padding(bottom = 8.dp)) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                editText.setText(it)
                                error = editText.error?.toString()
                            },
                            isError = error != null,
                            supportingText = error?.let { { Text(it) } },
                            singleLine = editText.maxLines == 1,
                            keyboardOptions = KeyboardOptions(keyboardType = if (inputClass == 2) KeyboardType.Number else KeyboardType.Text),
                            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier.fillMaxWidth().testTag("preference-edit-text"),
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = error == null, onClick = { commit(text) { preference.text = text } }) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
            )
        }
        else -> onDismiss()
    }
}
